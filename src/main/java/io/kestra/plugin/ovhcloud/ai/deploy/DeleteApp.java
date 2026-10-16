package io.kestra.plugin.ovhcloud.ai.deploy;

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
    title = "Delete OVHcloud AI Serving App",
    description = "Permanently deletes and tears down an OVHcloud AI Deploy model serving application."
)
@Plugin(
    examples = {
        @Example(
            title = "Delete an AI model serving application",
            full = true,
            code = """
                id: delete_ovh_app
                namespace: company.team

                tasks:
                  - id: teardown_app
                    type: io.kestra.plugin.ovhcloud.ai.deploy.DeleteApp
                    applicationKey: "{{ secret('OVH_APP_KEY') }}"
                    applicationSecret: "{{ secret('OVH_APP_SECRET') }}"
                    consumerKey: "{{ secret('OVH_CONSUMER_KEY') }}"
                    projectId: "{{ secret('OVH_PROJECT_ID') }}"
                    appId: "00000000-0000-0000-0000-000000000000"
                """
        )
    }
)
public class DeleteApp extends AbstractOvhTask implements RunnableTask<DeleteApp.Output> {
    @Schema(title = "The unique identifier of the serving application to delete")
    @NotNull
    @io.kestra.core.models.annotations.PluginProperty(group = "main")
    private Property<String> appId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rProjectId = runContext.render(this.projectId).as(String.class).orElseThrow();
        String rAppId = runContext.render(this.appId).as(String.class).orElseThrow();

        String path = "/cloud/project/" + encodePath(rProjectId) + "/ai/app/" + encodePath(rAppId);

        HttpResponse res = this.executeOvhRequest(runContext, "DELETE", path, null);
        if (res.getStatusCode() < 200 || res.getStatusCode() >= 300) {
            throw new RuntimeException("OVHcloud AI DeleteApp failed with status " + res.getStatusCode() + ": " + res.getBody());
        }

        return Output.builder()
            .appId(rAppId)
            .deleted(true)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The unique identifier of the deleted application")
        private final String appId;

        @Schema(title = "Whether the deletion request was successful")
        private final Boolean deleted;
    }
}
