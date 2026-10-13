package io.kestra.plugin.ovhcloud.ai;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class AiTrainingAndDeployTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void testComputeOvhSignature() {
        String appSecret = "sec123";
        String consumerKey = "ck456";
        String method = "GET";
        String url = "https://eu.api.ovh.com/1.0/cloud/project/p1/ai/job";
        String body = "";
        long timestamp = 1700000000L;

        String sig = AbstractOvhTask.computeOvhSignature(appSecret, consumerKey, method, url, body, timestamp);
        assertThat(sig, startsWith("$1$"));
        assertThat(sig.length(), is(43)); // "$1$" + 40 hex chars
    }

    @Test
    void testSubmitJobOutput() {
        SubmitJob.Output output = SubmitJob.Output.builder()
            .jobId("job-ai-12345")
            .status("QUEUED")
            .createdAt("2026-10-13T10:00:00Z")
            .url("https://dashboard.ovh.com/ai/job/job-ai-12345")
            .build();

        assertThat(output.getJobId(), is("job-ai-12345"));
        assertThat(output.getStatus(), is("QUEUED"));
        assertThat(output.getCreatedAt(), is("2026-10-13T10:00:00Z"));
        assertThat(output.getUrl(), is("https://dashboard.ovh.com/ai/job/job-ai-12345"));
    }

    @Test
    void testGetJobOutput() {
        GetJob.Output output = GetJob.Output.builder()
            .jobId("job-ai-12345")
            .status("DONE")
            .createdAt("2026-10-13T10:00:00Z")
            .startDate("2026-10-13T10:01:00Z")
            .endDate("2026-10-13T10:30:00Z")
            .exitCode(0)
            .build();

        assertThat(output.getJobId(), is("job-ai-12345"));
        assertThat(output.getStatus(), is("DONE"));
        assertThat(output.getExitCode(), is(0));
    }

    @Test
    void testCancelJobOutput() {
        CancelJob.Output output = CancelJob.Output.builder()
            .jobId("job-ai-12345")
            .cancelled(true)
            .build();

        assertThat(output.getJobId(), is("job-ai-12345"));
        assertThat(output.getCancelled(), is(true));
    }

    @Test
    void testCreateAppOutput() {
        CreateApp.Output output = CreateApp.Output.builder()
            .appId("app-serving-789")
            .status("RUNNING")
            .url("https://app-serving-789.ai.gra.cloud.ovh.net")
            .build();

        assertThat(output.getAppId(), is("app-serving-789"));
        assertThat(output.getStatus(), is("RUNNING"));
        assertThat(output.getUrl(), is("https://app-serving-789.ai.gra.cloud.ovh.net"));
    }

    @Test
    void testGetAppOutput() {
        GetApp.Output output = GetApp.Output.builder()
            .appId("app-serving-789")
            .name("fraud-detector")
            .status("RUNNING")
            .url("https://app-serving-789.ai.gra.cloud.ovh.net")
            .build();

        assertThat(output.getAppId(), is("app-serving-789"));
        assertThat(output.getName(), is("fraud-detector"));
        assertThat(output.getStatus(), is("RUNNING"));
    }

    @Test
    void testDeleteAppOutput() {
        DeleteApp.Output output = DeleteApp.Output.builder()
            .appId("app-serving-789")
            .deleted(true)
            .build();

        assertThat(output.getAppId(), is("app-serving-789"));
        assertThat(output.getDeleted(), is(true));
    }

    @Test
    void testPropertyRendering() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of(
            "pId", "proj-999",
            "img", "pytorch/pytorch:2.3.0",
            "jName", "mnist-classifier"
        ));

        SubmitJob task = SubmitJob.builder()
            .applicationKey(Property.of("app-key-test"))
            .applicationSecret(Property.of("app-secret-test"))
            .consumerKey(Property.of("consumer-key-test"))
            .projectId(Property.ofExpression("{{ pId }}"))
            .image(Property.ofExpression("{{ img }}"))
            .name(Property.ofExpression("{{ jName }}"))
            .command(Property.of(List.of("python", "train.py")))
            .gpu(Property.of(1))
            .gpuModel(Property.of("Tesla-V100S"))
            .build();

        assertThat(runContext.render(task.getProjectId()).as(String.class).orElse(null), is("proj-999"));
        assertThat(runContext.render(task.getImage()).as(String.class).orElse(null), is("pytorch/pytorch:2.3.0"));
        assertThat(runContext.render(task.getName()).as(String.class).orElse(null), is("mnist-classifier"));
        assertThat(runContext.render(task.getGpu()).as(Integer.class).orElse(null), is(1));
    }

    @Test
    void testMissingCredentialsThrows() {
        RunContext runContext = runContextFactory.of();

        GetJob task = GetJob.builder()
            .jobId(Property.of("00000000-0000-0000-0000-000000000000"))
            .build();

        assertThrows(Exception.class, () -> task.run(runContext));
    }
}
