Integrate with OVHcloud to orchestrate machine learning workflows and model serving endpoints.

The OVHcloud plugin provides tasks to interact with OVHcloud AI endpoints, allowing you to submit and manage AI training jobs and deploy AI model serving applications.

## Authentication

All tasks authenticate using an OVHcloud AI Bearer Token. Generate an AI token in the OVHcloud Manager or via the OVHcloud CLI, and pass it securely using Kestra secrets:

```yaml
token: "{{ secret('OVH_AI_TOKEN') }}"
```

## AI Training Tasks

- **SubmitJob**: Submit GPU or CPU AI/ML training jobs to OVHcloud AI Training.
- **GetJob**: Retrieve details, execution status, and logs URL of an existing training job.
- **CancelJob**: Cancel or kill an active training job.

## AI Deploy Tasks

- **CreateApp**: Deploy AI model serving container applications on OVHcloud AI Deploy.
- **GetApp**: Retrieve the runtime status and public/private endpoint URL of a deployed serving application.
- **DeleteApp**: Permanently terminate and remove a deployed serving application.

## Endpoints

Tasks connect to regional OVHcloud AI endpoints, defaulting to `https://gra.training.ai.cloud.ovh.net` (Gravelines, France) with support for `https://bhs.training.ai.cloud.ovh.net` (Beauharnois, Canada).
