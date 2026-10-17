package io.kestra.plugin.ovhcloud;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
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
    public static final String DEFAULT_AI_ENDPOINT = "https://gra.training.ai.cloud.ovh.net";

    public static final Set<String> ALLOWED_AI_HOSTS = Set.of(
        "gra.training.ai.cloud.ovh.net",
        "bhs.training.ai.cloud.ovh.net"
    );

    public static final Set<String> LOOPBACK_HOSTS = Set.of(
        "localhost",
        "127.0.0.1",
        "::1",
        "[::1]"
    );

    @Schema(
        title = "The OVHcloud AI API endpoint URL",
        description = "Regional data-plane endpoint URL. Defaults to 'https://gra.training.ai.cloud.ovh.net' (supported regions: GRA, BHS)."
    )
    @Builder.Default
    @PluginProperty(group = "connection")
    protected Property<String> endpoint = Property.ofValue(DEFAULT_AI_ENDPOINT);

    @Schema(
        title = "The OVHcloud AI Bearer token",
        description = "AI API Bearer token generated via 'ovhai token create' with the required role/permissions."
    )
    @NotNull
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @PluginProperty(group = "connection", secret = true)
    protected Property<String> token;

    public String defaultEndpoint() {
        return DEFAULT_AI_ENDPOINT;
    }

    /**
     * Strictly URL-encodes path segments and forbids traversal components ('.', '..', slashes).
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

    protected OvhResponse executeOvhRequest(RunContext runContext, String method, String path, String body) throws Exception {
        if (this.token == null) {
            throw new IllegalArgumentException("The 'token' property is required for OVHcloud AI authentication.");
        }

        String rEndpoint = this.endpoint != null ? runContext.render(this.endpoint).as(String.class).orElse(null) : null;
        if (rEndpoint == null || rEndpoint.isBlank()) {
            rEndpoint = defaultEndpoint();
        }

        URI parsedUri = URI.create(rEndpoint);
        if (parsedUri.getUserInfo() != null) {
            throw new IllegalArgumentException("Endpoint must not contain userinfo/credentials (received: " + rEndpoint + ")");
        }

        String host = parsedUri.getHost();
        if (host == null || !isAllowedHost(host)) {
            throw new IllegalArgumentException("Endpoint host '" + host + "' is not a recognized OVHcloud AI data-plane host or local test host.");
        }

        String scheme = parsedUri.getScheme();
        if (!"https".equalsIgnoreCase(scheme)) {
            if (!LOOPBACK_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Insecure HTTP endpoint is only permitted for loopback testing. Received scheme '" + scheme + "' for host: " + host);
            }
        }

        int port = parsedUri.getPort();
        if (port != -1 && port != 443 && !LOOPBACK_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Custom ports are not permitted for cloud endpoints. Port must be 443 or omitted. Received: " + port);
        }

        String fullUrl = rEndpoint + (path.startsWith("/") ? path : "/" + path);

        BearerAuthConfiguration authConfig = BearerAuthConfiguration.builder()
            .token(this.token)
            .build();

        HttpConfiguration httpConfig = HttpConfiguration.builder()
            .auth(authConfig)
            .allowFailed(Property.ofValue(true))
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
                HttpResponse<String> response = client.request(reqBuilder.build(), String.class);
                return new OvhResponse(response.getStatus().getCode(), response.getBody() != null ? response.getBody() : "");
            } catch (HttpClientResponseException e) {
                if (e.getResponse() != null) {
                    Object respBody = e.getResponse().getBody();
                    return new OvhResponse(e.getResponse().getStatus().getCode(), respBody != null ? respBody.toString() : "");
                }
                throw e;
            }
        }
    }

    @AllArgsConstructor
    @Getter
    public static class OvhResponse {
        private final int statusCode;
        private final String body;
    }
}
