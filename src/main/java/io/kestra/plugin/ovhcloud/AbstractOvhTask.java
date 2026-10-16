package io.kestra.plugin.ovhcloud;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.http.client.configurations.BearerAuthConfiguration;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractOvhTask extends Task {
    public static final Set<String> ALLOWED_AI_HOSTS = Set.of(
        "api.ai.cloud.ovh.net",
        "gra.training.ai.cloud.ovh.net",
        "sbg.training.ai.cloud.ovh.net",
        "bhs.training.ai.cloud.ovh.net",
        "rbx.training.ai.cloud.ovh.net",
        "gra.deploy.ai.cloud.ovh.net",
        "sbg.deploy.ai.cloud.ovh.net",
        "bhs.deploy.ai.cloud.ovh.net",
        "rbx.deploy.ai.cloud.ovh.net"
    );

    public static final Set<String> LOOPBACK_HOSTS = Set.of(
        "localhost",
        "127.0.0.1",
        "::1",
        "[::1]"
    );

    @Schema(
        title = "The OVHcloud AI API endpoint URL",
        description = "Regional data-plane endpoint URL (e.g. 'https://gra.training.ai.cloud.ovh.net' for training or 'https://gra.deploy.ai.cloud.ovh.net' for deploy)."
    )
    @PluginProperty(group = "connection")
    protected Property<String> endpoint;

    @Schema(
        title = "The OVHcloud AI Bearer token",
        description = "AI API Bearer token generated via 'ovhai token create' with the required role/permissions."
    )
    @NotNull
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @PluginProperty(group = "connection", secret = true)
    protected Property<String> token;

    @Schema(
        title = "The OVH Public Cloud project ID (serviceName)",
        description = "Optional Public Cloud project identifier."
    )
    @PluginProperty(group = "main")
    protected Property<String> projectId;

    protected abstract String defaultEndpoint();

    /**
     * URL-encodes a single path segment and guards against dot-segment path traversal (e.g. '.', '..').
     */
    public static String encodePath(String segment) {
        if (segment == null || segment.isBlank()) {
            throw new IllegalArgumentException("Path segment must not be null or blank");
        }
        if (segment.equals(".") || segment.equals("..") || segment.contains("/") || segment.contains("\\") || segment.contains("..")) {
            throw new IllegalArgumentException("Invalid path segment: path traversal segments ('.', '..') are not permitted (received: " + segment + ")");
        }
        try {
            return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
        } catch (Exception e) {
            throw new RuntimeException("Failed to URL-encode path segment: " + segment, e);
        }
    }

    /**
     * Strictly validates host against exact allowed OVHcloud AI regional data-plane hosts and loopback test hosts.
     */
    public static boolean isAllowedHost(String host) {
        if (host == null) {
            return false;
        }
        String lower = host.toLowerCase(Locale.ROOT);
        return ALLOWED_AI_HOSTS.contains(lower) || LOOPBACK_HOSTS.contains(lower);
    }

    /**
     * Safely parses an ISO-8601 timestamp string into an Instant, returning null if unparseable or blank.
     */
    public static Instant parseInstantSafe(String isoDate) {
        if (isoDate == null || isoDate.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(isoDate);
        } catch (Exception e) {
            return null;
        }
    }

    protected HttpResponse executeOvhRequest(RunContext runContext, String method, String path, String body) throws Exception {
        if (this.token == null) {
            throw new IllegalArgumentException("The 'token' property is required for OVHcloud AI authentication.");
        }

        String rEndpoint = this.endpoint != null ? runContext.render(this.endpoint).as(String.class).orElse(null) : null;
        if (rEndpoint == null || rEndpoint.isBlank()) {
            rEndpoint = defaultEndpoint();
        }
        rEndpoint = rEndpoint.trim();

        URI parsedUri = URI.create(rEndpoint);
        if (parsedUri.getUserInfo() != null) {
            throw new IllegalArgumentException("Insecure endpoint rejected: user-info in endpoint URL is not permitted");
        }

        String host = parsedUri.getHost();
        String scheme = parsedUri.getScheme();
        boolean isLoopback = host != null && LOOPBACK_HOSTS.contains(host.toLowerCase(Locale.ROOT));
        boolean allowedHost = isAllowedHost(host);
        int port = parsedUri.getPort();
        boolean isStandardHttpsPort = (port == -1 || port == 443);

        if (!("https".equalsIgnoreCase(scheme) && allowedHost && isStandardHttpsPort) && !("http".equalsIgnoreCase(scheme) && isLoopback)) {
            throw new IllegalArgumentException(
                "Insecure or unrecognised OVHcloud AI endpoint rejected. Host must be one of " + ALLOWED_AI_HOSTS
                + " over HTTPS (port 443), or localhost/127.0.0.1/[::1] over HTTP for testing."
            );
        }

        if (rEndpoint.endsWith("/")) {
            rEndpoint = rEndpoint.substring(0, rEndpoint.length() - 1);
        }
        String fullUrl = rEndpoint + (path.startsWith("/") ? path : "/" + path);

        BearerAuthConfiguration authConfig = BearerAuthConfiguration.builder()
            .token(this.token)
            .build();

        HttpConfiguration httpConfig = HttpConfiguration.builder()
            .auth(authConfig)
            .allowFailed(Property.of(true))
            .build();

        HttpRequest.HttpRequestBuilder reqBuilder = HttpRequest.builder()
            .method(method)
            .uri(URI.create(fullUrl))
            .addHeader("Accept", "application/json");

        if (body != null && !body.isBlank() && ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method))) {
            reqBuilder.addHeader("Content-Type", "application/json");
            reqBuilder.body(HttpRequest.StringRequestBody.builder()
                .content(body)
                .contentType("application/json")
                .charset(StandardCharsets.UTF_8)
                .build());
        }

        try (HttpClient client = new HttpClient(runContext, httpConfig)) {
            try {
                io.kestra.core.http.HttpResponse<String> response = client.request(reqBuilder.build(), String.class);
                return new HttpResponse(response.getStatus().getCode(), response.getBody() != null ? response.getBody() : "");
            } catch (HttpClientResponseException e) {
                if (e.getResponse() != null) {
                    Object respBody = e.getResponse().getBody();
                    return new HttpResponse(e.getResponse().getStatus().getCode(), respBody != null ? respBody.toString() : "");
                }
                throw e;
            }
        }
    }

    @AllArgsConstructor
    @Getter
    public static class HttpResponse {
        private final int statusCode;
        private final String body;
    }
}
