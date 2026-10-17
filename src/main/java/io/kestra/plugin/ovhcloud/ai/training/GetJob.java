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
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Get OVHcloud AI Training Job",
    description = "Fetches status, metrics, and details of an OVHcloud AI Training job by ID."
)
@Plugin(
    examples = {
        @Example(
            title = "Get AI Training job details and status",
            full = true,
            code = """
                id: get_ovh_training_status
                namespace: company.team

                tasks:
                  - id: get_job
                    type: io.kestra.plugin.ovhcloud.ai.training.GetJob
                    token: "{{ secret('OVH_AI_TOKEN') }}"
                    jobId: "00000000-0000-0000-0000-000000000000"
                """
        )
    }
)
public class GetJob extends AbstractOvhTask implements RunnableTask<GetJob.Output> {
    @Schema(
        title = "The unique identifier of the training job",
        description = "UUID assigned to the job when submitted."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> jobId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rJobId = runContext.render(this.jobId).as(String.class).orElseThrow();
        String path = "/v1/job/" + encodePath(rJobId);

        OvhResponse res = this.executeOvhRequest(runContext, "GET", path, null);
        if (res.getStatusCode() < 200 || res.getStatusCode() >= 300) {
            throw new RuntimeException("OVHcloud AI GetJob failed with status " + res.getStatusCode() + ": " + res.getBody());
        }

        if (res.getBody() == null || res.getBody().isBlank()) {
            throw new IllegalStateException("OVHcloud AI GetJob response body is empty");
        }

        JobResponse response = JacksonMapper.ofJson().readValue(res.getBody(), JobResponse.class);
        if (response == null || response.getId() == null || response.getId().isBlank()) {
            throw new IllegalStateException("OVHcloud AI response is missing required 'id' property: " + res.getBody());
        }

        return Output.builder()
            .jobId(response.getId())
            .status(response.getState())
            .createdAt(response.getCreatedAtInstant())
            .startDate(response.getStartDateInstant())
            .endDate(response.getEndDateInstant())
            .exitCode(response.getExitCode())
            .url(response.getUrl())
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The unique identifier of the AI training job")
        private final String jobId;

        @Schema(title = "The current lifecycle state of the training job")
        private final String status;

        @Schema(title = "Timestamp when the job was created")
        private final Instant createdAt;

        @Schema(title = "Timestamp when execution started")
        private final Instant startDate;

        @Schema(title = "Timestamp when execution concluded")
        private final Instant endDate;

        @Schema(title = "Container exit code if execution finished")
        private final Integer exitCode;

        @Schema(title = "Direct dashboard URL to access the job on OVHcloud")
        private final String url;
    }
}
