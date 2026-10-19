package io.kestra.plugin.ovhcloud.ai.training;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    public Instant getCreatedAtInstant() {
        return AbstractOvhTask.parseInstantSafe(createdAt);
    }

    public Instant getStartDateInstant() {
        if (status != null && status.getStartedAt() != null && !status.getStartedAt().isBlank()) {
            return AbstractOvhTask.parseInstantSafe(status.getStartedAt());
        }
        return null;
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
        return null;
    }

    public String getUrl() {
        return status != null ? status.getUrl() : null;
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
    }
}
