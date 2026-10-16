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
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

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
                    token: "{{ secret('OVH_AI_TOKEN') }}"
                    appId: "00000000-0000-0000-0000-000000000000"
                """
        )
    }
)
public class GetApp extends AbstractOvhTask implements RunnableTask<GetApp.Output> {
    @Schema(
        title = "The unique identifier of the serving application",
        description = "UUID of the deployed application."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> appId;

    @Override
    protected String defaultEndpoint() {
        return "https://gra.deploy.ai.cloud.ovh.net";
    }

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rAppId = runContext.render(this.appId).as(String.class).orElseThrow();
        String path = "/v1/app/" + encodePath(rAppId);

        HttpResponse res = this.executeOvhRequest(runContext, "GET", path, null);
        if (res.getStatusCode() < 200 || res.getStatusCode() >= 300) {
            throw new RuntimeException("OVHcloud AI GetApp failed with status " + res.getStatusCode() + ": " + res.getBody());
        }

        if (res.getBody() == null || res.getBody().isBlank()) {
            throw new IllegalStateException("OVHcloud AI GetApp response body is empty");
        }

        AppResponse response = JacksonMapper.ofJson().readValue(res.getBody(), AppResponse.class);
        if (response == null || response.getId() == null || response.getId().isBlank()) {
            throw new IllegalStateException("OVHcloud AI response is missing required 'id' property: " + res.getBody());
        }

        return Output.builder()
            .appId(response.getId())
            .name(response.getName())
            .status(response.getState())
            .url(response.getUrl())
            .createdAt(response.getCreatedAtInstant())
            .updatedAt(response.getUpdatedAtInstant())
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

        @Schema(title = "Timestamp when the application was created")
        private final Instant createdAt;

        @Schema(title = "Timestamp when the application was last updated")
        private final Instant updatedAt;
    }
}
