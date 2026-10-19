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
import io.kestra.core.serializers.JacksonMapper;
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
        description = """
            Regional data-plane endpoint URL. Defaults to 'https://gra.training.ai.cloud.ovh.net' (supported regions: GRA, BHS).
            """
    )
    @Builder.Default
    @PluginProperty(group = "connection")
    protected Property<String> endpoint = Property.ofValue(DEFAULT_AI_ENDPOINT);

    @Schema(
        title = "The OVHcloud AI Bearer token",
        description = """
            AI API Bearer token generated via 'ovhai token create' with the required role/permissions.
            """
    )
    @NotNull
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @PluginProperty(group = "connection", secret = true)
    protected Property<String> token;

    public String defaultEndpoint() {
        return DEFAULT_AI_ENDPOINT;
    }

    public static String encodePath(String segment) {
        if (segment == null || segment.isBlank()) {
            throw new IllegalArgumentException("Path segment must not be null or blank");
        }
        if (segment.equals(".") || segment.equals("..") || segment.contains("/") || segment.contains("\\") || segment.contains("..")) {
            throw new IllegalArgumentException("Invalid path segment: path traversal segments ('.', '..') are not permitted (received: " + segment + ")");
        }
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public static boolean isAllowedHost(String host) {
        if (host == null) {
            return false;
        }
        var lower = host.toLowerCase(Locale.ROOT);
        return ALLOWED_AI_HOSTS.contains(lower) || LOOPBACK_HOSTS.contains(lower);
    }

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

    public static void validateComputeResources(Integer cpu, Integer gpu) {
        if (cpu != null && (cpu < 1 || cpu > 12)) {
            throw new IllegalArgumentException("CPU allocation must be between 1 and 12 cores (received: " + cpu + ")");
        }
        if (gpu != null && (gpu < 1 || gpu > 4)) {
            throw new IllegalArgumentException("GPU allocation must be between 1 and 4 GPUs (received: " + gpu + ")");
        }
        if (cpu != null && gpu != null) {
            throw new IllegalArgumentException("Cannot allocate both 'cpu' and 'gpu': OVHcloud AI flavors are either CPU-based or GPU-based.");
        }
    }

    protected void checkStatus(OvhResponse res, String action) {
        var code = res.getStatusCode();
        if (code < 200 || code >= 300) {
            if (code == 401 || code == 403) {
                throw new IllegalStateException("OVHcloud AI " + action + " failed with status " + code +
                    ": unauthorized or forbidden. Check that 'token' is a valid OVHcloud AI token with sufficient permissions for this resource. Response: " + res.getBody());
            }
            throw new RuntimeException("OVHcloud AI " + action + " failed with status " + code + ": " + res.getBody());
        }
    }

    protected <T> T readResponse(OvhResponse res, Class<T> type, String action) throws Exception {
        checkStatus(res, action);

        if (res.getBody() == null || res.getBody().isBlank()) {
            throw new IllegalStateException("OVHcloud AI " + action + " response body is empty");
        }

        var parsed = JacksonMapper.ofJson().readValue(res.getBody(), type);
        if (parsed == null) {
            throw new IllegalStateException("OVHcloud AI " + action + " response could not be parsed: " + res.getBody());
        }
        return parsed;
    }

    protected OvhResponse executeOvhRequest(RunContext runContext, String method, String path, String body) throws Exception {
        if (this.token == null) {
            throw new IllegalArgumentException("The 'token' property is required for OVHcloud AI authentication.");
        }

        var rEndpoint = this.endpoint != null ? runContext.render(this.endpoint).as(String.class).orElse(null) : null;
        if (rEndpoint == null || rEndpoint.isBlank()) {
            rEndpoint = defaultEndpoint();
        }

        var parsedUri = URI.create(rEndpoint);
        if (parsedUri.getUserInfo() != null) {
            throw new IllegalArgumentException("Endpoint must not contain userinfo/credentials (received: " + rEndpoint + ")");
        }

        var host = parsedUri.getHost();
        if (host == null || !isAllowedHost(host)) {
            throw new IllegalArgumentException("Endpoint host '" + host + "' is not a recognized OVHcloud AI data-plane host or local test host.");
        }

        var scheme = parsedUri.getScheme();
        if (!"https".equalsIgnoreCase(scheme)) {
            if (!LOOPBACK_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Insecure HTTP endpoint is only permitted for loopback testing. Received scheme '" + scheme + "' for host: " + host);
            }
        }

        var port = parsedUri.getPort();
        if (port != -1 && port != 443 && !LOOPBACK_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Custom ports are not permitted for cloud endpoints. Port must be 443 or omitted. Received: " + port);
        }

        var fullUrl = rEndpoint + (path.startsWith("/") ? path : "/" + path);

        var authConfig = BearerAuthConfiguration.builder()
            .token(this.token)
            .build();

        var httpConfig = HttpConfiguration.builder()
            .auth(authConfig)
            .allowFailed(Property.ofValue(true))
            .build();

        var reqBuilder = HttpRequest.builder()
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

        try (var client = new HttpClient(runContext, httpConfig)) {
            try {
                var response = client.request(reqBuilder.build(), String.class);
                return new OvhResponse(response.getStatus().getCode(), response.getBody() != null ? response.getBody() : "");
            } catch (HttpClientResponseException e) {
                if (e.getResponse() != null) {
                    var respBody = e.getResponse().getBody();
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
