package io.kestra.plugin.ovhcloud.ai;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.ovhcloud.AbstractOvhTask;
import io.kestra.plugin.ovhcloud.ai.deploy.CreateApp;
import io.kestra.plugin.ovhcloud.ai.deploy.DeleteApp;
import io.kestra.plugin.ovhcloud.ai.deploy.GetApp;
import io.kestra.plugin.ovhcloud.ai.training.CancelJob;
import io.kestra.plugin.ovhcloud.ai.training.GetJob;
import io.kestra.plugin.ovhcloud.ai.training.SubmitJob;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@KestraTest
class AiTrainingAndDeployTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private ModelValidator modelValidator;

    private static WireMockServer wireMockServer;
    private static String testEndpoint;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        testEndpoint = "http://localhost:" + wireMockServer.port();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void resetWireMock() {
        wireMockServer.resetAll();
    }

    @Test
    void testSubmitJobSuccess() throws Exception {
        wireMockServer.stubFor(post(urlEqualTo("/v1/job"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "id": "job-ai-12345",
                      "status": {
                        "state": "QUEUED",
                        "url": "https://dashboard.ovh.com/ai/job/job-ai-12345"
                      },
                      "createdAt": "2026-10-16T12:00:00Z"
                    }
                    """)));

        var runContext = runContextFactory.of();
        var task = SubmitJob.builder()
            .endpoint(Property.ofValue(testEndpoint))
            .token(Property.ofValue("ovh-test-token-12345"))
            .image(Property.ofValue("pytorch/pytorch:2.3.0"))
            .command(Property.ofValue(List.of("python", "train.py")))
            .name(Property.ofValue("mnist-classifier"))
            .gpu(Property.ofValue(1))
            .gpuModel(Property.ofValue("Tesla-V100S"))
            .build();

        var output = task.run(runContext);

        assertThat(output.getJobId(), is("job-ai-12345"));
        assertThat(output.getStatus(), is("QUEUED"));
        assertThat(output.getCreatedAt(), is(Instant.parse("2026-10-16T12:00:00Z")));
        assertThat(output.getUrl(), is("https://dashboard.ovh.com/ai/job/job-ai-12345"));

        wireMockServer.verify(postRequestedFor(urlEqualTo("/v1/job"))
            .withHeader("Authorization", equalTo("Bearer ovh-test-token-12345"))
            .withHeader("Accept", equalTo("application/json"))
            .withHeader("Content-Type", equalTo("application/json"))
            .withRequestBody(equalToJson("""
                {
                  "image": "pytorch/pytorch:2.3.0",
                  "command": ["python", "train.py"],
                  "name": "mnist-classifier",
                  "resources": {
                    "gpu": 1,
                    "gpuModel": "Tesla-V100S"
                  }
                }
                """)));
    }

    @Test
    void testGetJobSuccess() throws Exception {
        wireMockServer.stubFor(get(urlEqualTo("/v1/job/job-ai-12345"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "id": "job-ai-12345",
                      "status": {
                        "state": "DONE",
                        "exitCode": 0,
                        "startedAt": "2026-10-16T12:01:00Z",
                        "stoppedAt": "2026-10-16T12:30:00Z",
                        "url": "https://dashboard.ovh.com/ai/job/job-ai-12345"
                      },
                      "createdAt": "2026-10-16T12:00:00Z"
                    }
                    """)));

        var runContext = runContextFactory.of();
        var task = GetJob.builder()
            .endpoint(Property.ofValue(testEndpoint))
            .token(Property.ofValue("ovh-test-token-12345"))
            .jobId(Property.ofValue("job-ai-12345"))
            .build();

        var output = task.run(runContext);

        assertThat(output.getJobId(), is("job-ai-12345"));
        assertThat(output.getStatus(), is("DONE"));
        assertThat(output.getExitCode(), is(0));
        assertThat(output.getCreatedAt(), is(Instant.parse("2026-10-16T12:00:00Z")));
        assertThat(output.getStartDate(), is(Instant.parse("2026-10-16T12:01:00Z")));
        assertThat(output.getEndDate(), is(Instant.parse("2026-10-16T12:30:00Z")));
        assertThat(output.getUrl(), is("https://dashboard.ovh.com/ai/job/job-ai-12345"));

        wireMockServer.verify(getRequestedFor(urlEqualTo("/v1/job/job-ai-12345"))
            .withHeader("Authorization", equalTo("Bearer ovh-test-token-12345")));
    }

    @Test
    void testCancelJobSuccess() throws Exception {
        wireMockServer.stubFor(put(urlEqualTo("/v1/job/job-ai-12345/kill"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{}")));

        var runContext = runContextFactory.of();
        var task = CancelJob.builder()
            .endpoint(Property.ofValue(testEndpoint))
            .token(Property.ofValue("ovh-test-token-12345"))
            .jobId(Property.ofValue("job-ai-12345"))
            .build();

        var output = task.run(runContext);

        assertThat(output.getJobId(), is("job-ai-12345"));
        assertThat(output.getKilled(), is(true));

        wireMockServer.verify(putRequestedFor(urlEqualTo("/v1/job/job-ai-12345/kill"))
            .withHeader("Authorization", equalTo("Bearer ovh-test-token-12345")));
    }

    @Test
    void testCreateAppSuccess() throws Exception {
        wireMockServer.stubFor(post(urlEqualTo("/v1/app"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "id": "app-serving-789",
                      "spec": {
                        "name": "fraud-detector-api"
                      },
                      "status": {
                        "state": "SCALING",
                        "url": "https://app-serving-789.ai.gra.cloud.ovh.net"
                      },
                      "createdAt": "2026-10-16T12:00:00Z"
                    }
                    """)));

        var runContext = runContextFactory.of();
        var task = CreateApp.builder()
            .endpoint(Property.ofValue(testEndpoint))
            .token(Property.ofValue("ovh-test-token-12345"))
            .image(Property.ofValue("fraud-detector:v1"))
            .name(Property.ofValue("fraud-detector-api"))
            .cpu(Property.ofValue(2))
            .build();

        var output = task.run(runContext);

        assertThat(output.getAppId(), is("app-serving-789"));
        assertThat(output.getStatus(), is("SCALING"));
        assertThat(output.getUrl(), is("https://app-serving-789.ai.gra.cloud.ovh.net"));
        assertThat(output.getCreatedAt(), is(Instant.parse("2026-10-16T12:00:00Z")));

        wireMockServer.verify(postRequestedFor(urlEqualTo("/v1/app"))
            .withHeader("Authorization", equalTo("Bearer ovh-test-token-12345"))
            .withHeader("Content-Type", equalTo("application/json"))
            .withRequestBody(equalToJson("""
                {
                  "image": "fraud-detector:v1",
                  "name": "fraud-detector-api",
                  "resources": {
                    "cpu": 2
                  }
                }
                """)));
    }

    @Test
    void testGetAppSuccess() throws Exception {
        wireMockServer.stubFor(get(urlEqualTo("/v1/app/app-serving-789"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "id": "app-serving-789",
                      "spec": {
                        "name": "fraud-detector-api"
                      },
                      "status": {
                        "state": "RUNNING",
                        "url": "https://app-serving-789.ai.gra.cloud.ovh.net"
                      },
                      "createdAt": "2026-10-16T12:00:00Z",
                      "updatedAt": "2026-10-16T12:10:00Z"
                    }
                    """)));

        var runContext = runContextFactory.of();
        var task = GetApp.builder()
            .endpoint(Property.ofValue(testEndpoint))
            .token(Property.ofValue("ovh-test-token-12345"))
            .appId(Property.ofValue("app-serving-789"))
            .build();

        var output = task.run(runContext);

        assertThat(output.getAppId(), is("app-serving-789"));
        assertThat(output.getName(), is("fraud-detector-api"));
        assertThat(output.getStatus(), is("RUNNING"));
        assertThat(output.getUrl(), is("https://app-serving-789.ai.gra.cloud.ovh.net"));
        assertThat(output.getCreatedAt(), is(Instant.parse("2026-10-16T12:00:00Z")));
        assertThat(output.getUpdatedAt(), is(Instant.parse("2026-10-16T12:10:00Z")));

        wireMockServer.verify(getRequestedFor(urlEqualTo("/v1/app/app-serving-789"))
            .withHeader("Authorization", equalTo("Bearer ovh-test-token-12345")));
    }

    @Test
    void testDeleteAppSuccess() throws Exception {
        wireMockServer.stubFor(delete(urlEqualTo("/v1/app/app-serving-789"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{}")));

        var runContext = runContextFactory.of();
        var task = DeleteApp.builder()
            .endpoint(Property.ofValue(testEndpoint))
            .token(Property.ofValue("ovh-test-token-12345"))
            .appId(Property.ofValue("app-serving-789"))
            .build();

        var output = task.run(runContext);

        assertThat(output.getAppId(), is("app-serving-789"));
        assertThat(output.getDeleted(), is(true));

        wireMockServer.verify(deleteRequestedFor(urlEqualTo("/v1/app/app-serving-789"))
            .withHeader("Authorization", equalTo("Bearer ovh-test-token-12345")));
    }

    @Test
    void testNon2xxErrorThrowsException() {
        wireMockServer.stubFor(get(urlEqualTo("/v1/job/non-existent-job"))
            .willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"Job not found\"}")));

        var runContext = runContextFactory.of();
        var task = GetJob.builder()
            .endpoint(Property.ofValue(testEndpoint))
            .token(Property.ofValue("ovh-test-token-12345"))
            .jobId(Property.ofValue("non-existent-job"))
            .build();

        var ex = assertThrows(RuntimeException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("404"));
        assertThat(ex.getMessage(), containsString("Job not found"));
    }

    @Test
    void testUnauthorizedThrowsActionableError() {
        wireMockServer.stubFor(get(urlEqualTo("/v1/job/unauth-job"))
            .willReturn(aResponse()
                .withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"unauthorized\"}")));

        var runContext = runContextFactory.of();
        var task = GetJob.builder()
            .endpoint(Property.ofValue(testEndpoint))
            .token(Property.ofValue("bad-token"))
            .jobId(Property.ofValue("unauth-job"))
            .build();

        var ex = assertThrows(IllegalStateException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("401"));
        assertThat(ex.getMessage(), containsString("Check that 'token' is a valid OVHcloud AI token"));
    }

    @Test
    void testDescriptiveErrorMessageOnMissingRequiredProperties() {
        var runContext = runContextFactory.of();

        var noImageSubmitJob = SubmitJob.builder()
            .endpoint(Property.ofValue(testEndpoint))
            .token(Property.ofValue("token"))
            .build();
        var exSubmit = assertThrows(IllegalArgumentException.class, () -> noImageSubmitJob.run(runContext));
        assertThat(exSubmit.getMessage(), containsString("'image' is required"));

        var noJobIdGetJob = GetJob.builder()
            .endpoint(Property.ofValue(testEndpoint))
            .token(Property.ofValue("token"))
            .build();
        var exJob = assertThrows(IllegalArgumentException.class, () -> noJobIdGetJob.run(runContext));
        assertThat(exJob.getMessage(), containsString("'jobId' is empty"));

        var noAppIdGetApp = GetApp.builder()
            .endpoint(Property.ofValue(testEndpoint))
            .token(Property.ofValue("token"))
            .build();
        var exApp = assertThrows(IllegalArgumentException.class, () -> noAppIdGetApp.run(runContext));
        assertThat(exApp.getMessage(), containsString("'appId' is empty"));
    }

    @Test
    void testMissingIdInResponseThrows() {
        wireMockServer.stubFor(get(urlEqualTo("/v1/job/corrupt-job"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":{\"state\":\"DONE\"}}")));

        var runContext = runContextFactory.of();
        var task = GetJob.builder()
            .endpoint(Property.ofValue(testEndpoint))
            .token(Property.ofValue("ovh-test-token-12345"))
            .jobId(Property.ofValue("corrupt-job"))
            .build();

        var ex = assertThrows(IllegalStateException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("missing required 'id' property"));
    }

    @Test
    void testEmptyResponseBodyThrows() {
        wireMockServer.stubFor(get(urlEqualTo("/v1/job/empty-job"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("   ")));

        var runContext = runContextFactory.of();
        var task = GetJob.builder()
            .endpoint(Property.ofValue(testEndpoint))
            .token(Property.ofValue("ovh-test-token-12345"))
            .jobId(Property.ofValue("empty-job"))
            .build();

        var ex = assertThrows(IllegalStateException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("response body is empty"));
    }

    @Test
    void testMalformedTimestampHandledSafely() throws Exception {
        wireMockServer.stubFor(get(urlEqualTo("/v1/job/malformed-date-job"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "id": "job-bad-date",
                      "status": {
                        "state": "DONE"
                      },
                      "createdAt": "not-a-valid-iso-date"
                    }
                    """)));

        var runContext = runContextFactory.of();
        var task = GetJob.builder()
            .endpoint(Property.ofValue(testEndpoint))
            .token(Property.ofValue("ovh-test-token-12345"))
            .jobId(Property.ofValue("malformed-date-job"))
            .build();

        var output = task.run(runContext);
        assertThat(output.getJobId(), is("job-bad-date"));
        assertThat(output.getStatus(), is("DONE"));
        assertNull(output.getCreatedAt());
    }

    @Test
    void testPropertyRendering() throws Exception {
        var runContext = runContextFactory.of(Map.of(
            "tok", "ovh-rendered-token-999",
            "img", "pytorch/pytorch:2.3.0",
            "jName", "mnist-classifier"
        ));

        var task = SubmitJob.builder()
            .token(Property.ofExpression("{{ tok }}"))
            .image(Property.ofExpression("{{ img }}"))
            .name(Property.ofExpression("{{ jName }}"))
            .command(Property.ofValue(List.of("python", "train.py")))
            .gpu(Property.ofValue(1))
            .gpuModel(Property.ofValue("Tesla-V100S"))
            .build();

        assertThat(runContext.render(task.getToken()).as(String.class).orElse(null), is("ovh-rendered-token-999"));
        assertThat(runContext.render(task.getImage()).as(String.class).orElse(null), is("pytorch/pytorch:2.3.0"));
        assertThat(runContext.render(task.getName()).as(String.class).orElse(null), is("mnist-classifier"));
        assertThat(runContext.render(task.getGpu()).as(Integer.class).orElse(null), is(1));
    }

    @Test
    void testInsecureEndpointRejection() {
        var runContext = runContextFactory.of();

        var task = GetJob.builder()
            .endpoint(Property.ofValue("http://evil-attacker.com/api"))
            .token(Property.ofValue("key"))
            .jobId(Property.ofValue("j1"))
            .build();

        assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
    }

    @Test
    void testEndpointUserInfoRejection() {
        var runContext = runContextFactory.of();

        var task = GetJob.builder()
            .endpoint(Property.ofValue("https://user:pass@gra.training.ai.cloud.ovh.net"))
            .token(Property.ofValue("key"))
            .jobId(Property.ofValue("j1"))
            .build();

        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("'endpoint' must not contain userinfo"));
        assertThat(ex.getMessage(), not(containsString("pass")));
    }

    @Test
    void testAllowedHostsStrictness() {
        assertThat(AbstractOvhTask.isAllowedHost("gra.training.ai.cloud.ovh.net"), is(true));
        assertThat(AbstractOvhTask.isAllowedHost("bhs.training.ai.cloud.ovh.net"), is(true));
        // Inactive / non-existent / unverified AI hosts must be rejected
        assertThat(AbstractOvhTask.isAllowedHost("sbg.training.ai.cloud.ovh.net"), is(false));
        assertThat(AbstractOvhTask.isAllowedHost("rbx.training.ai.cloud.ovh.net"), is(false));
        assertThat(AbstractOvhTask.isAllowedHost("gra.deploy.ai.cloud.ovh.net"), is(false));
        assertThat(AbstractOvhTask.isAllowedHost("sbg.deploy.ai.cloud.ovh.net"), is(false));
        assertThat(AbstractOvhTask.isAllowedHost("bhs.deploy.ai.cloud.ovh.net"), is(false));
        assertThat(AbstractOvhTask.isAllowedHost("rbx.deploy.ai.cloud.ovh.net"), is(false));
        assertThat(AbstractOvhTask.isAllowedHost("api.ai.cloud.ovh.net"), is(false));
        // Legacy core endpoints must be rejected for AI Bearer tokens
        assertThat(AbstractOvhTask.isAllowedHost("eu.api.ovh.com"), is(false));
        assertThat(AbstractOvhTask.isAllowedHost("ca.api.ovh.com"), is(false));

        // Unauthorized / attacker domains must be rejected
        assertThat(AbstractOvhTask.isAllowedHost("attacker.com"), is(false));
        assertThat(AbstractOvhTask.isAllowedHost("unauthorized.training.ai.cloud.ovh.net"), is(false));
        assertThat(AbstractOvhTask.isAllowedHost("gra.training.ai.cloud.ovh.net.evil.com"), is(false));
        assertThat(AbstractOvhTask.isAllowedHost("localhost.attacker.com"), is(false));
        assertThat(AbstractOvhTask.isAllowedHost(null), is(false));
    }

    @Test
    void testEncodePath() {
        assertThat(AbstractOvhTask.encodePath("job-12345"), is("job-12345"));
        assertThat(AbstractOvhTask.encodePath("app with space"), is("app%20with%20space"));

        assertThrows(IllegalArgumentException.class, () -> AbstractOvhTask.encodePath(null));
        assertThrows(IllegalArgumentException.class, () -> AbstractOvhTask.encodePath("   "));
        assertThrows(IllegalArgumentException.class, () -> AbstractOvhTask.encodePath("."));
        assertThrows(IllegalArgumentException.class, () -> AbstractOvhTask.encodePath(".."));
        assertThrows(IllegalArgumentException.class, () -> AbstractOvhTask.encodePath("../traversal"));
        assertThrows(IllegalArgumentException.class, () -> AbstractOvhTask.encodePath("folder/job"));
        assertThrows(IllegalArgumentException.class, () -> AbstractOvhTask.encodePath("folder\\job"));
    }

    @Test
    void testCreateAppResourceBounds() {
        var runContext = runContextFactory.of();

        var zeroCpu = CreateApp.builder()
            .token(Property.ofValue("key"))
            .image(Property.ofValue("img:latest"))
            .cpu(Property.ofValue(0))
            .build();
        assertThrows(IllegalArgumentException.class, () -> zeroCpu.run(runContext));

        var excessiveCpu = CreateApp.builder()
            .token(Property.ofValue("key"))
            .image(Property.ofValue("img:latest"))
            .cpu(Property.ofValue(13))
            .build();
        assertThrows(IllegalArgumentException.class, () -> excessiveCpu.run(runContext));

        var excessiveGpu = CreateApp.builder()
            .token(Property.ofValue("key"))
            .image(Property.ofValue("img:latest"))
            .gpu(Property.ofValue(5))
            .build();
        assertThrows(IllegalArgumentException.class, () -> excessiveGpu.run(runContext));

        var conflictingResources = CreateApp.builder()
            .token(Property.ofValue("key"))
            .image(Property.ofValue("img:latest"))
            .cpu(Property.ofValue(2))
            .gpu(Property.ofValue(1))
            .build();
        assertThrows(IllegalArgumentException.class, () -> conflictingResources.run(runContext));
    }

    @Test
    void testSubmitJobResourceBounds() {
        var runContext = runContextFactory.of();

        var negativeGpu = SubmitJob.builder()
            .token(Property.ofValue("key"))
            .image(Property.ofValue("img:latest"))
            .gpu(Property.ofValue(-1))
            .build();
        assertThrows(IllegalArgumentException.class, () -> negativeGpu.run(runContext));

        var excessiveGpu = SubmitJob.builder()
            .token(Property.ofValue("key"))
            .image(Property.ofValue("img:latest"))
            .gpu(Property.ofValue(5))
            .build();
        assertThrows(IllegalArgumentException.class, () -> excessiveGpu.run(runContext));

        var zeroCpu = SubmitJob.builder()
            .token(Property.ofValue("key"))
            .image(Property.ofValue("img:latest"))
            .cpu(Property.ofValue(0))
            .build();
        assertThrows(IllegalArgumentException.class, () -> zeroCpu.run(runContext));

        var excessiveCpu = SubmitJob.builder()
            .token(Property.ofValue("key"))
            .image(Property.ofValue("img:latest"))
            .cpu(Property.ofValue(13))
            .build();
        assertThrows(IllegalArgumentException.class, () -> excessiveCpu.run(runContext));

        var conflictingResources = SubmitJob.builder()
            .token(Property.ofValue("key"))
            .image(Property.ofValue("img:latest"))
            .cpu(Property.ofValue(2))
            .gpu(Property.ofValue(1))
            .build();
        assertThrows(IllegalArgumentException.class, () -> conflictingResources.run(runContext));
    }

    @Test
    void testSubmitJobCpuTrainingSuccess() throws Exception {
        wireMockServer.stubFor(post(urlEqualTo("/v1/job"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "id": "job-cpu-9999",
                      "status": {
                        "state": "QUEUED"
                      },
                      "createdAt": "2026-10-16T12:00:00Z"
                    }
                    """)));

        var runContext = runContextFactory.of();
        var task = SubmitJob.builder()
            .endpoint(Property.ofValue(testEndpoint))
            .token(Property.ofValue("ovh-test-token-12345"))
            .image(Property.ofValue("python:3.11-slim"))
            .command(Property.ofValue(List.of("python", "train_cpu.py")))
            .name(Property.ofValue("cpu-training-job"))
            .cpu(Property.ofValue(4))
            .build();

        var output = task.run(runContext);
        assertThat(output.getJobId(), is("job-cpu-9999"));
        assertThat(output.getStatus(), is("QUEUED"));

        wireMockServer.verify(postRequestedFor(urlEqualTo("/v1/job"))
            .withRequestBody(equalToJson("""
                {
                  "image": "python:3.11-slim",
                  "command": ["python", "train_cpu.py"],
                  "name": "cpu-training-job",
                  "resources": {
                    "cpu": 4
                  }
                }
                """)));
    }

    @Test
    void testCreateAppGpuServingSuccess() throws Exception {
        wireMockServer.stubFor(post(urlEqualTo("/v1/app"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "id": "app-gpu-5555",
                      "spec": {
                        "name": "llm-serving-app"
                      },
                      "status": {
                        "state": "SCALING",
                        "url": "https://app-gpu-5555.ai.gra.cloud.ovh.net"
                      },
                      "createdAt": "2026-10-16T12:00:00Z"
                    }
                    """)));

        var runContext = runContextFactory.of();
        var task = CreateApp.builder()
            .endpoint(Property.ofValue(testEndpoint))
            .token(Property.ofValue("ovh-test-token-12345"))
            .image(Property.ofValue("vllm/vllm-openai:latest"))
            .name(Property.ofValue("llm-serving-app"))
            .gpu(Property.ofValue(2))
            .gpuModel(Property.ofValue("Tesla-V100S"))
            .build();

        var output = task.run(runContext);
        assertThat(output.getAppId(), is("app-gpu-5555"));
        assertThat(output.getStatus(), is("SCALING"));

        wireMockServer.verify(postRequestedFor(urlEqualTo("/v1/app"))
            .withRequestBody(equalToJson("""
                {
                  "image": "vllm/vllm-openai:latest",
                  "name": "llm-serving-app",
                  "resources": {
                    "gpu": 2,
                    "gpuModel": "Tesla-V100S"
                  }
                }
                """)));
    }

    @Test
    void testMissingTokenThrows() {
        var runContext = runContextFactory.of();

        var task = GetJob.builder()
            .jobId(Property.ofValue("00000000-0000-0000-0000-000000000000"))
            .build();

        assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
    }

    @Test
    void testDefaultEndpoints() throws Exception {
        var runContext = runContextFactory.of();
        var expected = "https://gra.training.ai.cloud.ovh.net";

        assertThat(runContext.render(SubmitJob.builder().build().getEndpoint()).as(String.class).orElse(null), is(expected));
        assertThat(runContext.render(GetJob.builder().build().getEndpoint()).as(String.class).orElse(null), is(expected));
        assertThat(runContext.render(CancelJob.builder().build().getEndpoint()).as(String.class).orElse(null), is(expected));
        assertThat(runContext.render(CreateApp.builder().build().getEndpoint()).as(String.class).orElse(null), is(expected));
        assertThat(runContext.render(GetApp.builder().build().getEndpoint()).as(String.class).orElse(null), is(expected));
        assertThat(runContext.render(DeleteApp.builder().build().getEndpoint()).as(String.class).orElse(null), is(expected));
    }

    @Test
    void testModelValidatorOnSubmitJobAndCreateApp() {
        var submitJob = SubmitJob.builder()
            .id("submit-job")
            .type(SubmitJob.class.getName())
            .token(Property.ofValue("ovh-test-token"))
            .image(Property.ofValue("pytorch/pytorch:2.3.0"))
            .build();
        assertTrue(modelValidator.isValid(submitJob).isEmpty());

        var createApp = CreateApp.builder()
            .id("create-app")
            .type(CreateApp.class.getName())
            .token(Property.ofValue("ovh-test-token"))
            .image(Property.ofValue("fraud-detector:v1"))
            .build();
        assertTrue(modelValidator.isValid(createApp).isEmpty());

        var submitJobWithResources = SubmitJob.builder()
            .id("submit-job-res")
            .type(SubmitJob.class.getName())
            .token(Property.ofValue("ovh-test-token"))
            .image(Property.ofValue("pytorch/pytorch:2.3.0"))
            .cpu(Property.ofValue(4))
            .build();
        assertTrue(modelValidator.isValid(submitJobWithResources).isEmpty());

        var createAppWithResources = CreateApp.builder()
            .id("create-app-res")
            .type(CreateApp.class.getName())
            .token(Property.ofValue("ovh-test-token"))
            .image(Property.ofValue("fraud-detector:v1"))
            .gpu(Property.ofValue(2))
            .build();
        assertTrue(modelValidator.isValid(createAppWithResources).isEmpty());

        var invalidCpuJob = SubmitJob.builder()
            .id("invalid-cpu-job")
            .type(SubmitJob.class.getName())
            .token(Property.ofValue("ovh-test-token"))
            .image(Property.ofValue("pytorch/pytorch:2.3.0"))
            .cpu(Property.ofValue(0))
            .build();
        assertTrue(modelValidator.isValid(invalidCpuJob).isPresent());

        var invalidSubmitJob = SubmitJob.builder()
            .id("invalid-submit-job")
            .type(SubmitJob.class.getName())
            .build();
        assertTrue(modelValidator.isValid(invalidSubmitJob).isPresent());

        var invalidCreateApp = CreateApp.builder()
            .id("invalid-create-app")
            .type(CreateApp.class.getName())
            .build();
        assertTrue(modelValidator.isValid(invalidCreateApp).isPresent());
    }
}
