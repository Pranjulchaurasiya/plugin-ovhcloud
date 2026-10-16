package io.kestra.plugin.ovhcloud.ai.training;

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
    title = "Cancel OVHcloud AI Training Job",
    description = "Gracefully cancels or stops a running OVHcloud AI Training job."
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
                    applicationKey: "{{ secret('OVH_APP_KEY') }}"
                    applicationSecret: "{{ secret('OVH_APP_SECRET') }}"
                    consumerKey: "{{ secret('OVH_CONSUMER_KEY') }}"
                    projectId: "{{ secret('OVH_PROJECT_ID') }}"
                    jobId: "00000000-0000-0000-0000-000000000000"
                """
        )
    }
)
public class CancelJob extends AbstractOvhTask implements RunnableTask<CancelJob.Output> {
    @Schema(title = "The unique identifier of the training job to stop")
    @NotNull
    @io.kestra.core.models.annotations.PluginProperty(group = "main")
    private Property<String> jobId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rProjectId = runContext.render(this.projectId).as(String.class).orElseThrow();
        String rJobId = runContext.render(this.jobId).as(String.class).orElseThrow();

        String path = String.format("/cloud/project/%s/ai/job/%s/stop", rProjectId, rJobId);

        HttpResponse res = this.executeOvhRequest(runContext, "POST", path, "{}");
        if (res.getStatusCode() < 200 || res.getStatusCode() >= 300) {
            throw new RuntimeException("OVHcloud AI CancelJob failed with status " + res.getStatusCode() + ": " + res.getBody());
        }

        return Output.builder()
            .jobId(rJobId)
            .cancelled(true)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The unique identifier of the stopped training job")
        private final String jobId;

        @Schema(title = "Whether the cancellation request was accepted successfully")
        private final Boolean cancelled;
    }
}
