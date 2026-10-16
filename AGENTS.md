# Kestra OVHcloud Plugin

## What

- Provides tasks for OVHcloud AI Training and AI Deploy under `io.kestra.plugin.ovhcloud.ai`.
- Tasks communicate with OVHcloud AI data-plane regional APIs using Bearer token authentication.

## Why

- **What user problem does this solve?** Orchestrating machine learning training workflows and model serving endpoints on OVHcloud without manual scripting or custom CLI tooling.
- **Why would a team adopt this plugin in a workflow?** It enables declarative, event-driven orchestration of distributed AI jobs with GPU acceleration and deployment of AI serving containers directly within Kestra flows.
- **What operational/business outcome does it enable?** Automated end-to-end ML lifecycles (data preparation, training job submission, status tracking, teardown, and model deployment) with full auditability and error isolation.

## How

### Architecture

Single-module Java plugin. Source packages under `io.kestra.plugin.ovhcloud`:

- `io.kestra.plugin.ovhcloud`: Base task `AbstractOvhTask` implementing security controls, endpoint validation, URL encoding, and HTTP client wiring via Kestra's core `HttpClient` with Bearer auth.
- `io.kestra.plugin.ovhcloud.ai.training`: AI Training tasks (`SubmitJob`, `GetJob`, `CancelJob`).
- `io.kestra.plugin.ovhcloud.ai.deploy`: AI Deploy tasks (`CreateApp`, `GetApp`, `DeleteApp`).

### Key Plugin Classes

- `io.kestra.plugin.ovhcloud.AbstractOvhTask`: Abstract foundation task providing regional AI data-plane routing, Bearer token injection, and endpoint validation.
- `io.kestra.plugin.ovhcloud.ai.training.SubmitJob`: Submits GPU/CPU AI training jobs to `POST /v1/job`.
- `io.kestra.plugin.ovhcloud.ai.training.GetJob`: Inspects job lifecycle status and execution metrics via `GET /v1/job/{id}`.
- `io.kestra.plugin.ovhcloud.ai.training.CancelJob`: Terminates active training jobs via `PUT /v1/job/{id}/kill`.
- `io.kestra.plugin.ovhcloud.ai.deploy.CreateApp`: Deploys model serving application containers to `POST /v1/app`.
- `io.kestra.plugin.ovhcloud.ai.deploy.GetApp`: Retrieves deployment status and endpoint URLs via `GET /v1/app/{id}`.
- `io.kestra.plugin.ovhcloud.ai.deploy.DeleteApp`: Tears down serving applications via `DELETE /v1/app/{id}`.

### Project Structure

```
plugin-ovhcloud/
├── src/main/java/io/kestra/plugin/ovhcloud/
│   ├── AbstractOvhTask.java
│   └── ai/
│       ├── training/ (SubmitJob, GetJob, CancelJob)
│       └── deploy/   (CreateApp, GetApp, DeleteApp)
├── src/main/resources/
│   ├── icons/
│   └── metadata/
├── src/test/java/io/kestra/plugin/ovhcloud/ai/
│   └── AiTrainingAndDeployTest.java
├── build.gradle
└── README.md
```

## Local rules

- All tasks use Kestra core's `HttpClient` with Bearer token authentication against regional OVHcloud AI endpoints.
- Path parameters must be safely URL-encoded and checked for dot-segment traversal using `AbstractOvhTask.encodePath()`.
- Endpoints must be strictly validated against allowed OVHcloud AI regional hosts and local loopback for testing.
- Tests must execute 100% offline using WireMock mock server asserting request methods, headers, and payload structures.

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
- https://docs.ovhcloud.com/en/
