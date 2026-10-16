## Why

Build and Git results appear as transient "action summary" toasts pushed over STOMP, and transient local messages appear next to them. Once a toast is dismissed, the page reloads, or the app restarts, that information is gone. Users must re-run the action or dig through logs to recall what happened, and there is no way to see a missed notification. A persisted, reviewable notification history fixes this.

## What Changes

- Persist every notification in H2 so it survives page reloads and app restarts, and expose it through a new `Notification` entity, repository, and service.
- Capture **everything notifiable**: build and Git action summaries (`/topic/action-summary`), build-failure notices (`/topic/build-failure`), and the transient local success/error messages shown by the page. Action summaries and build failures are stored server-side as they are published; local messages are posted by the client so they are recorded too.
- Add a bell button with an unread-count badge to the workspace detail navigation, opening a notification history panel that lists past notifications (newest first) with type, title, message, project chips, and timestamp.
- Live updates: when a toast is shown, the history list and badge update without a reload; opening the panel marks notifications as read.
- Add "Clear all" to empty the history, and bound retention to a fixed maximum so storage stays small.

## Capabilities

### New Capabilities

- `notification-history`: Persist, list, mark-read, and clear application notifications, with an unread badge and a reviewable history panel.

### Modified Capabilities

<!-- None: existing spec-level behavior is unchanged; toasts keep working as today. -->

## Impact

- **Backend**: new `Notification` entity, `NotificationRepository`, `NotificationService`, and `NotificationController` (`GET /notifications`, `POST /notifications/local`, `POST /notifications/read`, `DELETE /notifications`). `BuildService` routes action summaries and build failures through the service before broadcasting.
- **Frontend**: `workspace-detail.html` gains a nav bell/badge, a history panel, and script to load, render, mark read, clear, and live-update the list. `showLocalActionMessage` also posts to the server.
- **Native image**: register the new entity and DTO in `NativeRuntimeHints`.
- **Tests**: service/repository tests for recording, retention, read, and clear; controller contract tests; a headless-Chrome regression for the panel. H2 schema is auto-generated; no migration tooling is introduced.
