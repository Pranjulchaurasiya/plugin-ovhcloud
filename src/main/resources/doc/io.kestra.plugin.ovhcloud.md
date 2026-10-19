# How to use the OVHcloud plugin

Integrate with [OVHcloud AI](https://help.ovhcloud.com/csm/en-public-cloud-ai-training-getting-started?id=kb_article_view&sysparm_article=KB0050856) to orchestrate distributed machine learning training and model serving endpoints directly from Kestra flows.

The OVHcloud plugin provides dedicated tasks for [OVHcloud AI Training](https://www.ovhcloud.com/en/public-cloud/ai-training/) and [OVHcloud AI Deploy](https://www.ovhcloud.com/en/public-cloud/ai-deploy/).

## Authentication

All tasks authenticate using an OVHcloud AI Bearer Token. Generate an AI token in the OVHcloud Manager or via the `ovhai token create` CLI, and pass it securely using Kestra [secrets](https://kestra.io/docs/concepts/secret) or set it org-wide via [plugin defaults](https://kestra.io/docs/workflow-components/plugin-defaults):

```yaml
tasks:
  - id: submit_job
    type: io.kestra.plugin.ovhcloud.ai.training.SubmitJob
    token: "{{ secret('OVH_AI_TOKEN') }}"
```

## AI Training Tasks

- **SubmitJob**: Submit GPU or CPU AI/ML training jobs to OVHcloud AI Training.
  - **Required fields**: `token`, `image`
  - **Optional fields**: `command`, `name`, `gpu` (1–4), `gpuModel`, `cpu` (1–12), `endpoint` (defaults to Gravelines: `https://gra.training.ai.cloud.ovh.net`)
- **GetJob**: Retrieve details, execution status (`QUEUED`, `RUNNING`, `DONE`, `FAILED`), container exit code, and job access URL of an existing training job.
  - **Required fields**: `token`, `jobId`
  - **Optional fields**: `endpoint`
- **CancelJob**: Terminate or kill an active training job.
  - **Required fields**: `token`, `jobId`
  - **Optional fields**: `endpoint`

## AI Deploy Tasks

- **CreateApp**: Deploy AI model serving container applications on OVHcloud AI Deploy.
  - **Required fields**: `token`, `image`
  - **Optional fields**: `name`, `cpu` (1–12, defaults to 1), `gpu` (1–4), `gpuModel`, `endpoint` (defaults to `https://gra.training.ai.cloud.ovh.net`)
- **GetApp**: Retrieve the runtime status (`SCALING`, `RUNNING`, `STOPPED`), timestamps, and public/private endpoint URL of a deployed serving application.
  - **Required fields**: `token`, `appId`
  - **Optional fields**: `endpoint`
- **DeleteApp**: Permanently terminate and tear down a deployed serving application.
  - **Required fields**: `token`, `appId`
  - **Optional fields**: `endpoint`

## Endpoints

Tasks connect to regional OVHcloud AI endpoints, defaulting to `https://gra.training.ai.cloud.ovh.net` (Gravelines, France) with support for `https://bhs.training.ai.cloud.ovh.net` (Beauharnois, Canada). Custom regional AI endpoints must use HTTPS on port 443.
