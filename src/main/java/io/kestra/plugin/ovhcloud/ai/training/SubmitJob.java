package io.kestra.plugin.ovhcloud.ai.training;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.ovhcloud.AbstractOvhTask;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Submit OVHcloud AI Training Job",
    description = "Submits a GPU or CPU distributed AI/ML training job to OVHcloud AI Training."
)
@Plugin(
    examples = {
        @Example(
            title = "Submit a PyTorch training job with GPU resources",
            full = true,
            code = """
                id: submit_ovh_training
                namespace: company.team

                tasks:
                  - id: train_job
                    type: io.kestra.plugin.ovhcloud.ai.training.SubmitJob
                    token: "{{ secret('OVH_AI_TOKEN') }}"
                    image: "pytorch/pytorch:2.3.0-cuda12.1-cudnn8-runtime"
                    command: ["python", "train.py", "--epochs", "50"]
                    name: "kestra-pytorch-training"
                    gpu: 1
                    gpuModel: "Tesla-V100S"
                """
        )
    }
)
public class SubmitJob extends AbstractOvhTask implements RunnableTask<SubmitJob.Output> {
    @Schema(
        title = "The container image to execute for training",
        description = "Public or private container image URI containing the training script and runtime."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> image;

    @Schema(
        title = "The command line arguments for the training container",
        description = "Array of command-line arguments to execute inside the container."
    )
    @PluginProperty(group = "main")
    private Property<List<String>> command;

    @Schema(
        title = "A user-friendly name for the training job",
        description = "Label displayed in the OVHcloud manager dashboard and CLI."
    )
    @PluginProperty(group = "main")
    private Property<String> name;

    @Schema(
        title = "Number of GPUs to allocate for the job",
        description = "Must be between 1 and 4 GPUs (hardware/region dependent). Defaults to 1 GPU if cpu is not specified. Mutually exclusive with 'cpu'."
    )
    @Min(1)
    @Max(4)
    @PluginProperty(group = "advanced")
    private Property<Integer> gpu;

    @Schema(
        title = "GPU model type",
        description = "Specific accelerator model requested (e.g. 'Tesla-V100S', 'A100-SXM4-40GB', 'L4', 'L40S')."
    )
    @PluginProperty(group = "advanced")
    private Property<String> gpuModel;

    @Schema(
        title = "Number of CPU cores to allocate for the job",
        description = "Must be between 1 and 12 vCores for CPU-only training workloads. Mutually exclusive with 'gpu'."
    )
    @Min(1)
    @Max(12)
    @PluginProperty(group = "advanced")
    private Property<Integer> cpu;

    @Override
    protected String defaultEndpoint() {
        return "https://gra.training.ai.cloud.ovh.net";
    }

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rImage = runContext.render(this.image).as(String.class).orElseThrow();
        List<String> rCommand = runContext.render(this.command).asList(String.class);
        String rName = runContext.render(this.name).as(String.class).orElse(null);
        Integer rGpu = this.gpu != null ? runContext.render(this.gpu).as(Integer.class).orElse(null) : null;
        String rGpuModel = this.gpuModel != null ? runContext.render(this.gpuModel).as(String.class).orElse(null) : null;
        Integer rCpu = this.cpu != null ? runContext.render(this.cpu).as(Integer.class).orElse(null) : null;

        if (rCpu != null && (rCpu < 1 || rCpu > 12)) {
            throw new IllegalArgumentException("CPU allocation must be between 1 and 12 vCores (received: " + rCpu + ")");
        }

        if (rGpu != null && (rGpu < 1 || rGpu > 4)) {
            throw new IllegalArgumentException("GPU allocation must be between 1 and 4 GPUs (received: " + rGpu + ")");
        }

        if (rCpu != null && rGpu != null) {
            throw new IllegalArgumentException("Cannot allocate both 'cpu' and 'gpu': OVHcloud AI flavors are either CPU-based or GPU-based.");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("image", rImage);
        if (rCommand != null && !rCommand.isEmpty()) {
            payload.put("command", rCommand);
        }
        if (rName != null && !rName.isBlank()) {
            payload.put("name", rName);
        }

        Map<String, Object> resources = new HashMap<>();
        if (rCpu != null) {
            resources.put("cpu", rCpu);
        } else {
            int gpuCount = (rGpu != null) ? rGpu : 1;
            resources.put("gpu", gpuCount);
            if (rGpuModel != null && !rGpuModel.isBlank()) {
                resources.put("gpuModel", rGpuModel);
            }
        }
        payload.put("resources", resources);

        String jsonPayload = JacksonMapper.ofJson().writeValueAsString(payload);
        HttpResponse res = this.executeOvhRequest(runContext, "POST", "/v1/job", jsonPayload);
        if (res.getStatusCode() < 200 || res.getStatusCode() >= 300) {
            throw new RuntimeException("OVHcloud AI SubmitJob failed with status " + res.getStatusCode() + ": " + res.getBody());
        }

        if (res.getBody() == null || res.getBody().isBlank()) {
            throw new IllegalStateException("OVHcloud AI SubmitJob response body is empty");
        }

        JobResponse response = JacksonMapper.ofJson().readValue(res.getBody(), JobResponse.class);
        if (response == null || response.getId() == null || response.getId().isBlank()) {
            throw new IllegalStateException("OVHcloud AI response is missing required 'id' property: " + res.getBody());
        }

        return Output.builder()
            .jobId(response.getId())
            .status(response.getState())
            .createdAt(response.getCreatedAtInstant())
            .url(response.getUrl())
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The unique identifier of the submitted AI training job")
        private final String jobId;

        @Schema(title = "The current lifecycle state of the training job (e.g. QUEUED, RUNNING, DONE, FAILED)")
        private final String status;

        @Schema(title = "Creation timestamp of the training job")
        private final Instant createdAt;

        @Schema(title = "Direct dashboard URL to access the job on OVHcloud")
        private final String url;
    }
}
