package io.kestra.plugin.ovhcloud.ai.deploy;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.ovhcloud.AbstractOvhTask;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Delete OVHcloud AI Serving App",
    description = """
        Permanently deletes and tears down an OVHcloud AI Deploy model serving application.
        """
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
                    token: "{{ secret('OVH_AI_TOKEN') }}"
                    appId: "00000000-0000-0000-0000-000000000000"
                """
        )
    }
)
public class DeleteApp extends AbstractOvhTask implements RunnableTask<DeleteApp.Output> {
    @Schema(
        title = "The unique identifier of the serving application to delete",
        description = """
            UUID of the application to terminate and remove.
            """
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> appId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var rAppId = runContext.render(this.appId).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("'appId' is empty: set it to the app UUID returned by CreateApp"));
        var path = "/v1/app/" + encodePath(rAppId);

        var res = this.executeOvhRequest(runContext, "DELETE", path, null);
        checkStatus(res, "DeleteApp");

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
