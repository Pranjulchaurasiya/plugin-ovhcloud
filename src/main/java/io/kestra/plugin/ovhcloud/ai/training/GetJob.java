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
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

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
                    applicationKey: "{{ secret('OVH_APP_KEY') }}"
                    applicationSecret: "{{ secret('OVH_APP_SECRET') }}"
                    consumerKey: "{{ secret('OVH_CONSUMER_KEY') }}"
                    projectId: "{{ secret('OVH_PROJECT_ID') }}"
                    jobId: "00000000-0000-0000-0000-000000000000"
                """
        )
    }
)
public class GetJob extends AbstractOvhTask implements RunnableTask<GetJob.Output> {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Schema(title = "The unique identifier of the training job")
    @NotNull
    @io.kestra.core.models.annotations.PluginProperty(group = "main")
    private Property<String> jobId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rProjectId = runContext.render(this.projectId).as(String.class).orElseThrow();
        String rJobId = runContext.render(this.jobId).as(String.class).orElseThrow();

        String path = "/cloud/project/" + encodePath(rProjectId) + "/ai/job/" + encodePath(rJobId);

        HttpResponse res = this.executeOvhRequest(runContext, "GET", path, null);
        if (res.getStatusCode() < 200 || res.getStatusCode() >= 300) {
            throw new RuntimeException("OVHcloud AI GetJob failed with status " + res.getStatusCode() + ": " + res.getBody());
        }

        JsonNode root = MAPPER.readTree(res.getBody());
        String status = root.has("status") ? (root.get("status").isObject() ? root.get("status").path("state").asText() : root.get("status").asText()) : "UNKNOWN";
        String createdAt = root.has("createdAt") ? root.get("createdAt").asText() : null;
        String startDate = root.has("startDate") ? root.get("startDate").asText() : null;
        String endDate = root.has("endDate") ? root.get("endDate").asText() : null;
        Integer exitCode = root.path("status").has("exitCode") ? root.path("status").get("exitCode").asInt() : null;

        return Output.builder()
            .jobId(rJobId)
            .status(status)
            .createdAt(createdAt)
            .startDate(startDate)
            .endDate(endDate)
            .exitCode(exitCode)
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
        private final String createdAt;

        @Schema(title = "Timestamp when execution started")
        private final String startDate;

        @Schema(title = "Timestamp when execution concluded")
        private final String endDate;

        @Schema(title = "Container exit code if execution finished")
        private final Integer exitCode;
    }
}
