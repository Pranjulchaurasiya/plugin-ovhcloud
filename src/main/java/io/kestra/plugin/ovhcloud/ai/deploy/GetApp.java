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

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Get OVHcloud AI Serving App",
    description = "Retrieves status, endpoints, and health of an OVHcloud AI Deploy model serving application."
)
@Plugin(
    examples = {
        @Example(
            title = "Get AI Deploy application status",
            full = true,
            code = """
                id: get_ovh_serving_app
                namespace: company.team

                tasks:
                  - id: get_app
                    type: io.kestra.plugin.ovhcloud.ai.deploy.GetApp
                    applicationKey: "{{ secret('OVH_APP_KEY') }}"
                    applicationSecret: "{{ secret('OVH_APP_SECRET') }}"
                    consumerKey: "{{ secret('OVH_CONSUMER_KEY') }}"
                    projectId: "{{ secret('OVH_PROJECT_ID') }}"
                    appId: "00000000-0000-0000-0000-000000000000"
                """
        )
    }
)
public class GetApp extends AbstractOvhTask implements RunnableTask<GetApp.Output> {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Schema(title = "The unique identifier of the serving application")
    @NotNull
    @io.kestra.core.models.annotations.PluginProperty(group = "main")
    private Property<String> appId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rProjectId = runContext.render(this.projectId).as(String.class).orElseThrow();
        String rAppId = runContext.render(this.appId).as(String.class).orElseThrow();

        String path = String.format("/cloud/project/%s/ai/app/%s", rProjectId, rAppId);

        HttpResponse res = this.executeOvhRequest(runContext, "GET", path, null);
        if (res.getStatusCode() < 200 || res.getStatusCode() >= 300) {
            throw new RuntimeException("OVHcloud AI GetApp failed with status " + res.getStatusCode() + ": " + res.getBody());
        }

        JsonNode root = MAPPER.readTree(res.getBody());
        String name = root.has("name") ? root.get("name").asText() : null;
        String status = root.has("status") ? (root.get("status").isObject() ? root.get("status").path("state").asText() : root.get("status").asText()) : "UNKNOWN";
        String url = root.has("url") ? root.get("url").asText() : null;

        return Output.builder()
            .appId(rAppId)
            .name(name)
            .status(status)
            .url(url)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The unique identifier of the application")
        private final String appId;

        @Schema(title = "Friendly name of the application")
        private final String name;

        @Schema(title = "The current lifecycle state of the app")
        private final String status;

        @Schema(title = "The URL endpoint exposing the AI service")
        private final String url;
    }
}
