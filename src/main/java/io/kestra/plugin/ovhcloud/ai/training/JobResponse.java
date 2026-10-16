package io.kestra.plugin.ovhcloud.ai.training;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.kestra.plugin.ovhcloud.AbstractOvhTask;
import lombok.*;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class JobResponse {
    @JsonProperty("id")
    private String id;

    @JsonProperty("status")
    private JobStatus status;

    @JsonProperty("createdAt")
    private String createdAt;

    @JsonProperty("startDate")
    private String startDate;

    @JsonProperty("endDate")
    private String endDate;

    @JsonProperty("url")
    private String url;

    public Instant getCreatedAtInstant() {
        return AbstractOvhTask.parseInstantSafe(createdAt);
    }

    public Instant getStartDateInstant() {
        return AbstractOvhTask.parseInstantSafe(startDate);
    }

    public Instant getEndDateInstant() {
        return AbstractOvhTask.parseInstantSafe(endDate);
    }

    public String getState() {
        return status != null && status.getState() != null ? status.getState() : "UNKNOWN";
    }

    public Integer getExitCode() {
        return status != null ? status.getExitCode() : null;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JobStatus {
        @JsonProperty("state")
        private String state;

        @JsonProperty("exitCode")
        private Integer exitCode;

        @JsonCreator
        public static JobStatus fromJson(JsonNode node) {
            if (node == null || node.isNull()) {
                return null;
            }
            if (node.isTextual()) {
                return JobStatus.builder().state(node.asText()).build();
            }
            String state = node.hasNonNull("state") ? node.get("state").asText() : null;
            Integer exitCode = node.hasNonNull("exitCode") ? node.get("exitCode").asInt() : null;
            return JobStatus.builder().state(state).exitCode(exitCode).build();
        }
    }
}
