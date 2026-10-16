## 1. Persistence Foundation

- [x] 1.1 Add a `Notification` JPA entity in `entities/` with `createdAt`, `category`, `action`, `success`, `title`, `message`, `workspaceId`, `workspaceName`, the four project-id lists (failed/succeeded/changed/no-changes) as bounded text, and `read`.
- [x] 1.2 Add a `NotificationRepository` with a newest-first query, an unread count, a delete-all operation, and support for locating rows older than the retention bound.
- [x] 1.3 Add a `NotificationService` with `recordActionSummary`, `recordBuildFailure`, `recordLocal`, `recent`, `unreadCount`, `markAllRead`, and `clear`, plus pruning to the latest N (default 200) on insert.

## 2. Server-Side Capture

- [x] 2.1 Add `NotificationService.publish(ActionSummary, workspaceId, workspaceName)` that persists then broadcasts `/topic/action-summary`, and replace the direct `convertAndSend` summary calls in `BuildService`.
- [x] 2.2 Route the build-failure notice through the service so it is persisted before broadcasting `/topic/build-failure`.
- [x] 2.3 Derive the workspace id/name at each call site from the affected project(s), including bulk Git actions and range builds.

## 3. HTTP API

- [x] 3.1 Add a `NotificationController` exposing `GET /notifications` (newest first), `GET /notifications/unread-count`, `POST /notifications/local`, `POST /notifications/read`, and `DELETE /notifications`.
- [x] 3.2 Add a `NotificationDto` response record carrying the fields the panel renders, and map entities to it.
- [x] 3.3 Register the `Notification` entity and `NotificationDto` reflection hints in `NativeRuntimeHints` for the native image profile.

## 4. Frontend History UI

- [x] 4.1 Add a notification bell button with an unread badge to `#workspace-nav` in `workspace-detail.html`.
- [x] 4.2 Add the notification history panel markup (header, "Clear all", scrollable list, empty state), hidden by default and keyboard-dismissible.
- [x] 4.3 Implement the panel script: load history and unread count, render items newest first with escaped text and project chips, mark read on open, clear all, and open/close with correct focus handling.
- [x] 4.4 Extend the STOMP subscriptions to prepend new action summaries and build failures to the panel and bump the badge when closed, and post local messages from `showLocalActionMessage` to `POST /notifications/local`.

## 5. Tests and Verification

- [x] 5.1 Add service/repository tests covering each notification category, the retention bound pruning the oldest rows, unread count, mark-all-read, and clear.
- [x] 5.2 Add controller tests for every new endpoint, including rejecting malformed local-notification payloads.
- [x] 5.3 Add a headless-Chrome regression for the panel: badge from stored history, open lists items and clears the badge, live prepend, clear-all empty state, and escaped untrusted content.
- [x] 5.4 Run the full test suite, verify H2 startup with the new table, and run `openspec validate add-notification-history`.
