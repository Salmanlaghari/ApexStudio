# AI content reporting: current implementation and limits

The editor's **Flag AI content** action creates a private, on-device journal entry in the app's internal files (`ai-content-reports/ai-content-reports.json`). Each entry contains a timestamp, a selectable reason (sexual, violent, hateful, or other), and a reference to the current project and selected clip; when a valid text overlay is selected, its identifier is recorded too. The journal does **not** copy media, selected text, prompts, generated output, or any other content. It does not use a network request or upload content.

The UI tells users that the entry is saved on this device and is not sent to the developer. The reference is useful as a local note for the user, but it is not a moderation case, does not reach the developer, and does not provide a way for the developer to review the reported content.

## Google Play / developer reporting limitation

This implementation must **not** be described as satisfying Google Play's AI-generated-content reporting requirements, developer submission obligations, or any other reporting/compliance obligation. No valid developer reporting endpoint, submission API, or operational review process is configured in this app. Do not imply that a locally saved journal entry was submitted or received. A real submission flow requires an approved and documented destination, user-facing disclosure, a privacy/security review of the data sent, and a tested delivery and failure-handling process. Until that exists, the only supported behavior is local reference journaling.
