package io.kestra.plugin.ovhcloud.ai.training;

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
    title = "Cancel OVHcloud AI Training Job",
    description = "Cancels or stops a running OVHcloud AI Training job."
)
@Plugin(
    examples = {
        @Example(
            title = "Cancel a running training job",
            full = true,
            code = """
                id: cancel_ovh_training
                namespace: company.team

                tasks:
                  - id: stop_job
                    type: io.kestra.plugin.ovhcloud.ai.training.CancelJob
                    token: "{{ secret('OVH_AI_TOKEN') }}"
                    jobId: "00000000-0000-0000-0000-000000000000"
                """
        )
    }
)
public class CancelJob extends AbstractOvhTask implements RunnableTask<CancelJob.Output> {
    @Schema(
        title = "The unique identifier of the training job to stop",
        description = "UUID of the active job to terminate."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> jobId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rJobId = runContext.render(this.jobId).as(String.class).orElseThrow();
        String path = "/v1/job/" + encodePath(rJobId) + "/kill";

        OvhResponse res = this.executeOvhRequest(runContext, "PUT", path, null);
        if (res.getStatusCode() < 200 || res.getStatusCode() >= 300) {
            throw new RuntimeException("OVHcloud AI CancelJob failed with status " + res.getStatusCode() + ": " + res.getBody());
        }

        return Output.builder()
            .jobId(rJobId)
            .killed(true)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The unique identifier of the stopped training job")
        private final String jobId;

        @Schema(title = "Whether the cancellation or kill request was accepted successfully")
        private final Boolean killed;
    }
}
