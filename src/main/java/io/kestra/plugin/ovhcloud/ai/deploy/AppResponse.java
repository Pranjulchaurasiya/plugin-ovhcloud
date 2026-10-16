package io.kestra.plugin.ovhcloud.ai.deploy;

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
public class AppResponse {
    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("status")
    private AppStatus status;

    @JsonProperty("url")
    private String url;

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

    public String getState() {
        return status != null && status.getState() != null ? status.getState() : "UNKNOWN";
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AppStatus {
        @JsonProperty("state")
        private String state;

        @JsonCreator
        public static AppStatus fromJson(JsonNode node) {
            if (node == null || node.isNull()) {
                return null;
            }
            if (node.isTextual()) {
                return AppStatus.builder().state(node.asText()).build();
            }
            String state = node.hasNonNull("state") ? node.get("state").asText() : null;
            return AppStatus.builder().state(state).build();
        }
    }
}
