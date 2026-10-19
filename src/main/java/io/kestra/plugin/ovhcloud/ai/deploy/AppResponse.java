package io.kestra.plugin.ovhcloud.ai.deploy;

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
public class AppResponse {
    @JsonProperty("id")
    private String id;

    @JsonProperty("spec")
    private AppSpec spec;

    @JsonProperty("status")
    private AppStatus status;

    @JsonProperty("createdAt")
    private String createdAt;

    @JsonProperty("updatedAt")
    private String updatedAt;

    public Instant getCreatedAtInstant() {
        return AbstractOvhTask.parseInstantSafe(createdAt);
    }

    public Instant getUpdatedAtInstant() {
        return AbstractOvhTask.parseInstantSafe(updatedAt);
    }

    public String getName() {
        return spec != null ? spec.getName() : null;
    }

    public String getUrl() {
        return status != null ? status.getUrl() : null;
    }

    public String getState() {
        return status != null && status.getState() != null ? status.getState() : "UNKNOWN";
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AppSpec {
        @JsonProperty("name")
        private String name;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AppStatus {
        @JsonProperty("state")
        private String state;

        @JsonProperty("url")
        private String url;
    }
}
