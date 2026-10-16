package io.kestra.plugin.ovhcloud;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.stream.Collectors;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractOvhTask extends Task {
    @Schema(title = "The OVH API endpoint URL (defaults to 'https://eu.api.ovh.com/1.0')")
    @Builder.Default
    @io.kestra.core.models.annotations.PluginProperty(group = "connection")
    protected Property<String> endpoint = Property.of("https://eu.api.ovh.com/1.0");

    @Schema(title = "The OVH application key")
    @NotNull
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @io.kestra.core.models.annotations.PluginProperty(group = "connection", secret = true)
    protected Property<String> applicationKey;

    @Schema(title = "The OVH application secret")
    @NotNull
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @io.kestra.core.models.annotations.PluginProperty(group = "connection", secret = true)
    protected Property<String> applicationSecret;

    @Schema(title = "The OVH consumer key")
    @NotNull
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @io.kestra.core.models.annotations.PluginProperty(group = "connection", secret = true)
    protected Property<String> consumerKey;

    @Schema(title = "The OVH Public Cloud project ID (serviceName)")
    @NotNull
    @io.kestra.core.models.annotations.PluginProperty(group = "main")
    protected Property<String> projectId;

    public static String computeOvhSignature(String appSecret, String consumerKey, String method, String fullUrl, String body, long timestamp) {
        try {
            String toSign = appSecret + "+" + consumerKey + "+" + method + "+" + fullUrl + "+" + (body != null ? body : "") + "+" + timestamp;
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(toSign.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder("$1$");
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute OVH HMAC-SHA1 signature", e);
        }
    }

    protected HttpResponse executeOvhRequest(RunContext runContext, String method, String path, String body) throws Exception {
        String rEndpoint = runContext.render(this.endpoint).as(String.class).orElse("https://eu.api.ovh.com/1.0").trim();
        if (!rEndpoint.startsWith("https://") && !rEndpoint.startsWith("http://localhost") && !rEndpoint.startsWith("http://127.0.0.1")) {
            throw new IllegalArgumentException("Insecure endpoint rejected: OVH API endpoint must use HTTPS (received: " + rEndpoint + ")");
        }
        if (rEndpoint.endsWith("/")) {
            rEndpoint = rEndpoint.substring(0, rEndpoint.length() - 1);
        }
        String fullUrl = rEndpoint + (path.startsWith("/") ? path : "/" + path);

        String rAppKey = runContext.render(this.applicationKey).as(String.class).orElseThrow();
        String rAppSecret = runContext.render(this.applicationSecret).as(String.class).orElseThrow();
        String rConsumerKey = runContext.render(this.consumerKey).as(String.class).orElseThrow();

        long timestamp = System.currentTimeMillis() / 1000L;
        String signature = computeOvhSignature(rAppSecret, rConsumerKey, method, fullUrl, body, timestamp);

        URL url = URI.create(fullUrl).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("X-Ovh-Application", rAppKey);
        conn.setRequestProperty("X-Ovh-Timestamp", String.valueOf(timestamp));
        conn.setRequestProperty("X-Ovh-Signature", signature);
        conn.setRequestProperty("X-Ovh-Consumer", rConsumerKey);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);

        if (body != null && !body.isEmpty() && ("POST".equals(method) || "PUT".equals(method))) {
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }

        int status = conn.getResponseCode();
        InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
        String responseBody = "";
        if (is != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                responseBody = reader.lines().collect(Collectors.joining("\n"));
            }
        }

        return new HttpResponse(status, responseBody);
    }

    @AllArgsConstructor
    @Getter
    public static class HttpResponse {
        private final int statusCode;
        private final String body;
    }
}
