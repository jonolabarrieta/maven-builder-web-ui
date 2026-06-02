## Why

Currently, when users perform actions in the application (such as compiling a project, triggering git fetch, checking out branches, updating project order, or changing profiles), there is no unified visual feedback that an HTTP request is in progress. This leads to user uncertainty about whether the application is responding, and can cause duplicate actions. Adding a global loader/spinner ensures the UI clearly indicates background work is happening.

## What Changes

- Implement a global loading spinner/indicator overlay (or a sleek fixed corner indicator) that displays when there is active network traffic.
- Listen to HTMX events (`htmx:beforeRequest` and `htmx:afterRequest` / errors) to toggle the spinner state.
- Intercept vanilla JavaScript `fetch` calls to toggle the spinner state in sync with non-HTMX requests.
- Ensure the spinner does not block non-conflicting UI interactions unless desired, but remains visually clear.

## Capabilities

### New Capabilities
- `http-loading-spinner`: A global UI spinner that automatically triggers during any HTMX or fetch-based HTTP request, ensuring visual response feedback.

### Modified Capabilities
