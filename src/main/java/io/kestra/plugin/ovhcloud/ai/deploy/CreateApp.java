package io.kestra.plugin.ovhcloud.ai.deploy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.ovhcloud.AbstractOvhTask;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

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
                    applicationKey: "{{ secret('OVH_APP_KEY') }}"
                    applicationSecret: "{{ secret('OVH_APP_SECRET') }}"
                    consumerKey: "{{ secret('OVH_CONSUMER_KEY') }}"
                    projectId: "{{ secret('OVH_PROJECT_ID') }}"
                    image: "my-docker-registry/fraud-model:v1"
                    name: "fraud-detector-api"
                    cpu: 2
                    memory: 4
                """
        )
    }
)
public class CreateApp extends AbstractOvhTask implements RunnableTask<CreateApp.Output> {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Schema(title = "The container image to deploy for model serving")
    @NotNull
    @io.kestra.core.models.annotations.PluginProperty(group = "main")
    private Property<String> image;

    @Schema(title = "A unique friendly name for the serving application")
    @io.kestra.core.models.annotations.PluginProperty(group = "main")
    private Property<String> name;

    @Schema(title = "Number of CPU cores allocated for the app (minimum 1, defaults to 1)")
    @Builder.Default
    @io.kestra.core.models.annotations.PluginProperty(group = "advanced")
    private Property<Integer> cpu = Property.of(1);

    @Schema(title = "RAM allocated in Gigabytes (minimum 1, defaults to 2)")
    @Builder.Default
    @io.kestra.core.models.annotations.PluginProperty(group = "advanced")
    private Property<Integer> memory = Property.of(2);

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rProjectId = runContext.render(this.projectId).as(String.class).orElseThrow();
        String rImage = runContext.render(this.image).as(String.class).orElseThrow();
        String rName = runContext.render(this.name).as(String.class).orElse(null);
        Integer rCpu = runContext.render(this.cpu).as(Integer.class).orElse(1);
        Integer rMemory = runContext.render(this.memory).as(Integer.class).orElse(2);

        Map<String, Object> payload = new HashMap<>();
        payload.put("image", rImage);
        if (rName != null && !rName.isBlank()) {
            payload.put("name", rName);
        }

        Map<String, Object> resources = new HashMap<>();
        resources.put("cpu", rCpu);
        resources.put("memory", rMemory);
        payload.put("resources", resources);

        String path = String.format("/cloud/project/%s/ai/app", rProjectId);
        String jsonPayload = MAPPER.writeValueAsString(payload);

        HttpResponse res = this.executeOvhRequest(runContext, "POST", path, jsonPayload);
        if (res.getStatusCode() < 200 || res.getStatusCode() >= 300) {
            throw new RuntimeException("OVHcloud AI CreateApp failed with status " + res.getStatusCode() + ": " + res.getBody());
        }

        JsonNode root = MAPPER.readTree(res.getBody());
        String appId = root.has("id") ? root.get("id").asText() : "";
        String status = root.has("status") ? (root.get("status").isObject() ? root.get("status").path("state").asText() : root.get("status").asText()) : "STARTING";
        String url = root.has("url") ? root.get("url").asText() : null;

        return Output.builder()
            .appId(appId)
            .status(status)
            .url(url)
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
    }
}
