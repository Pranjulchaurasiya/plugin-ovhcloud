package io.kestra.plugin.ovhcloud.ai.deploy;

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
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Deploy OVHcloud AI Serving App",
    description = "Deploys an AI model serving container endpoint on OVHcloud AI Deploy."
)
@Plugin(
    examples = {
        @Example(
            title = "Deploy a machine learning serving container",
            full = true,
            code = """
                id: deploy_ovh_model
                namespace: company.team

                tasks:
                  - id: create_endpoint
                    type: io.kestra.plugin.ovhcloud.ai.deploy.CreateApp
                    token: "{{ secret('OVH_AI_TOKEN') }}"
                    image: "my-docker-registry/fraud-model:v1"
                    name: "fraud-detector-api"
                    cpu: 2
                """
        )
    }
)
public class CreateApp extends AbstractOvhTask implements RunnableTask<CreateApp.Output> {
    @Schema(
        title = "The container image to deploy for model serving",
        description = "Container image URI exposing an HTTP inference service."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> image;

    @Schema(
        title = "A unique friendly name for the serving application",
        description = "Identifier tag displayed on the OVHcloud AI dashboard."
    )
    @PluginProperty(group = "main")
    private Property<String> name;

    @Schema(
        title = "Number of CPU cores allocated for the app",
        description = "Must be between 1 and 12 cores. Mutually exclusive with 'gpu'. Defaults to 1 core."
    )
    @Min(1)
    @Max(12)
    @PluginProperty(group = "advanced")
    private Property<Integer> cpu;

    @Schema(
        title = "Number of GPUs allocated for the app",
        description = "Must be between 1 and 4 GPUs. Mutually exclusive with 'cpu'."
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

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rImage = runContext.render(this.image).as(String.class).orElseThrow();
        String rName = runContext.render(this.name).as(String.class).orElse(null);
        Integer rCpu = this.cpu != null ? runContext.render(this.cpu).as(Integer.class).orElse(null) : null;
        Integer rGpu = this.gpu != null ? runContext.render(this.gpu).as(Integer.class).orElse(null) : null;
        String rGpuModel = this.gpuModel != null ? runContext.render(this.gpuModel).as(String.class).orElse(null) : null;

        if (rCpu != null && (rCpu < 1 || rCpu > 12)) {
            throw new IllegalArgumentException("CPU allocation must be between 1 and 12 cores (received: " + rCpu + ")");
        }

        if (rGpu != null && (rGpu < 1 || rGpu > 4)) {
            throw new IllegalArgumentException("GPU allocation must be between 1 and 4 GPUs (received: " + rGpu + ")");
        }

        if (rCpu != null && rGpu != null) {
            throw new IllegalArgumentException("Cannot allocate both 'cpu' and 'gpu': OVHcloud AI Deploy flavors are either CPU-based or GPU-based.");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("image", rImage);
        if (rName != null && !rName.isBlank()) {
            payload.put("name", rName);
        }

        Map<String, Object> resources = new HashMap<>();
        if (rGpu != null) {
            resources.put("gpu", rGpu);
            if (rGpuModel != null && !rGpuModel.isBlank()) {
                resources.put("gpuModel", rGpuModel);
            }
        } else {
            int cpuCount = (rCpu != null) ? rCpu : 1;
            resources.put("cpu", cpuCount);
        }
        payload.put("resources", resources);

        String jsonPayload = JacksonMapper.ofJson().writeValueAsString(payload);
        OvhResponse res = this.executeOvhRequest(runContext, "POST", "/v1/app", jsonPayload);
        if (res.getStatusCode() < 200 || res.getStatusCode() >= 300) {
            throw new RuntimeException("OVHcloud AI CreateApp failed with status " + res.getStatusCode() + ": " + res.getBody());
        }

        if (res.getBody() == null || res.getBody().isBlank()) {
            throw new IllegalStateException("OVHcloud AI CreateApp response body is empty");
        }

        AppResponse response = JacksonMapper.ofJson().readValue(res.getBody(), AppResponse.class);
        if (response == null || response.getId() == null || response.getId().isBlank()) {
            throw new IllegalStateException("OVHcloud AI response is missing required 'id' property: " + res.getBody());
        }

        return Output.builder()
            .appId(response.getId())
            .status(response.getState())
            .url(response.getUrl())
            .createdAt(response.getCreatedAtInstant())
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The unique identifier of the deployed serving application")
        private final String appId;

        @Schema(title = "The current lifecycle state of the app (e.g. SCALING, RUNNING, STOPPED)")
        private final String status;

        @Schema(title = "The public or private URL endpoint exposing the AI service")
        private final String url;

        @Schema(title = "Creation timestamp of the application")
        private final Instant createdAt;
    }
}
