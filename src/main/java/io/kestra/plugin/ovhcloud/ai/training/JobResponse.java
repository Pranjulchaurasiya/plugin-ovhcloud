package io.kestra.plugin.ovhcloud.ai.training;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.kestra.plugin.ovhcloud.AbstractOvhTask;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
        if (status != null && status.getStartedAt() != null && !status.getStartedAt().isBlank()) {
            return AbstractOvhTask.parseInstantSafe(status.getStartedAt());
        }
        return AbstractOvhTask.parseInstantSafe(startDate);
    }

    public Instant getEndDateInstant() {
        if (status != null) {
            if (status.getStoppedAt() != null && !status.getStoppedAt().isBlank()) {
                return AbstractOvhTask.parseInstantSafe(status.getStoppedAt());
            }
            if (status.getFinalizedAt() != null && !status.getFinalizedAt().isBlank()) {
                return AbstractOvhTask.parseInstantSafe(status.getFinalizedAt());
            }
        }
        return AbstractOvhTask.parseInstantSafe(endDate);
    }

    public String getUrl() {
        if (status != null && status.getUrl() != null && !status.getUrl().isBlank()) {
            return status.getUrl();
        }
        return url;
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

        @JsonProperty("url")
        private String url;

        @JsonProperty("startedAt")
        private String startedAt;

        @JsonProperty("stoppedAt")
        private String stoppedAt;

        @JsonProperty("finalizedAt")
        private String finalizedAt;

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
            String url = node.hasNonNull("url") ? node.get("url").asText() : null;
            String startedAt = node.hasNonNull("startedAt") ? node.get("startedAt").asText() : null;
            String stoppedAt = node.hasNonNull("stoppedAt") ? node.get("stoppedAt").asText() : null;
            String finalizedAt = node.hasNonNull("finalizedAt") ? node.get("finalizedAt").asText() : null;
            return JobStatus.builder()
                .state(state)
                .exitCode(exitCode)
                .url(url)
                .startedAt(startedAt)
                .stoppedAt(stoppedAt)
                .finalizedAt(finalizedAt)
                .build();
        }
    }
}
