package io.kestra.plugin.ovhcloud.ai.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.ovhcloud.AbstractOvhTask;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

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
                    applicationKey: "{{ secret('OVH_APP_KEY') }}"
                    applicationSecret: "{{ secret('OVH_APP_SECRET') }}"
                    consumerKey: "{{ secret('OVH_CONSUMER_KEY') }}"
                    projectId: "{{ secret('OVH_PROJECT_ID') }}"
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
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Schema(title = "The container image to execute for training")
    @NotNull
    @io.kestra.core.models.annotations.PluginProperty(group = "main")
    private Property<String> image;

    @Schema(title = "The command line arguments for the training container")
    @io.kestra.core.models.annotations.PluginProperty(group = "main")
    private Property<List<String>> command;

    @Schema(title = "A user-friendly name for the training job")
    @io.kestra.core.models.annotations.PluginProperty(group = "main")
    private Property<String> name;

    @Schema(title = "Number of GPUs to allocate for the job (between 0 and 8, defaults to 0)")
    @Builder.Default
    @Min(0)
    @Max(8)
    @io.kestra.core.models.annotations.PluginProperty(group = "advanced")
    private Property<Integer> gpu = Property.of(0);

    @Schema(title = "GPU model type (e.g. 'Tesla-V100S', 'A100')")
    @io.kestra.core.models.annotations.PluginProperty(group = "advanced")
    private Property<String> gpuModel;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rProjectId = runContext.render(this.projectId).as(String.class).orElseThrow();
        String rImage = runContext.render(this.image).as(String.class).orElseThrow();
        List<String> rCommand = runContext.render(this.command).asList(String.class);
        String rName = runContext.render(this.name).as(String.class).orElse(null);
        Integer rGpu = runContext.render(this.gpu).as(Integer.class).orElse(0);
        String rGpuModel = runContext.render(this.gpuModel).as(String.class).orElse(null);

        if (rGpu < 0 || rGpu > 8) {
            throw new IllegalArgumentException("GPU allocation must be between 0 and 8 (received: " + rGpu + ")");
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
        if (rGpu > 0) {
            resources.put("gpu", rGpu);
            if (rGpuModel != null && !rGpuModel.isBlank()) {
                resources.put("gpuModel", rGpuModel);
            }
        }
        if (!resources.isEmpty()) {
            payload.put("resources", resources);
        }

        String path = "/cloud/project/" + encodePath(rProjectId) + "/ai/job";
        String jsonPayload = MAPPER.writeValueAsString(payload);

        HttpResponse res = this.executeOvhRequest(runContext, "POST", path, jsonPayload);
        if (res.getStatusCode() < 200 || res.getStatusCode() >= 300) {
            throw new RuntimeException("OVHcloud AI SubmitJob failed with status " + res.getStatusCode() + ": " + res.getBody());
        }

        JsonNode root = MAPPER.readTree(res.getBody());
        String jobId = root.has("id") ? root.get("id").asText() : "";
        String status = root.has("status") ? (root.get("status").isObject() ? root.get("status").path("state").asText() : root.get("status").asText()) : "QUEUED";
        String createdAt = root.has("createdAt") ? root.get("createdAt").asText() : null;
        String url = root.has("url") ? root.get("url").asText() : null;

        return Output.builder()
            .jobId(jobId)
            .status(status)
            .createdAt(createdAt)
            .url(url)
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
        private final String createdAt;

        @Schema(title = "Direct dashboard URL to access the job on OVHcloud")
        private final String url;
    }
}
