## Context

Notifications today are ephemeral toasts on the workspace detail page. Build and Git results arrive over STOMP (`/topic/action-summary`), build failures over `/topic/build-failure`, and client-side success/error messages are created by `showLocalActionMessage`. All live only in the DOM under `#action-summary-container` and disappear on dismiss, reload, or restart. There is no notification spec or persistence.

The app is Spring Boot 3.2.5 + Thymeleaf + HTMX + H2/JPA with a native-image profile. `BuildService` sends action summaries directly through `SimpMessagingTemplate` from many call sites; the workspace page already renders JSON payloads (e.g. Git status) and receives live pushes, so client-side rendering of a notification list matches existing patterns.

## Goals / Non-Goals

**Goals**

- Persist every notification in H2 and make it reviewable after toasts disappear or the app restarts.
- Capture action summaries, build failures, and local success/error messages.
- Provide a bell with an unread badge and a history panel with live updates, mark-read, and clear-all.
- Keep the existing toast behavior unchanged.

**Non-Goals**

- Native OS/desktop notifications or email.
- Per-user accounts, read state per user, or multi-user routing (single-user app).
- Changing what actions exist or their results; pagination/search of history.
- Rewriting the toast rendering; the panel reuses the same summary content shape.

## Decisions

### 1. Persist server-side in H2 (user decision)

Introduce a `Notification` entity, `NotificationRepository`, and `NotificationService`. History survives reloads, navigation, and restarts, and is the single source of truth for the panel and badge. Retention is bounded to the latest 200 rows, pruned on insert.

**Alternatives:** in-memory ring buffer (lost on restart); `localStorage` (per-browser, not shared, no backend query). Rejected: the user chose durable, server-side history.

### 2. One write path: `NotificationService.publish(...)` persists then broadcasts

Replace the direct `messagingTemplate.convertAndSend("/topic/action-summary", summary)` calls in `BuildService` with a service call that saves the row and then broadcasts it. This keeps persistence and live delivery from drifting apart. Build failures go through an equivalent method that records and sends `/topic/build-failure`.

**Alternatives:** keep the broadcast and add a second `record(...)` call at each site (easy to miss a site); Spring `ApplicationEvent` listeners (extra indirection, same number of edits, harder to test end-to-end). Rejected for clarity/coverage.

### 3. Normalized columns, not an opaque JSON blob

Store `category` (ACTION/BUILD_FAILURE/LOCAL), `action`, `success`, `title`, `message`, `workspaceId`, `workspaceName`, the four project-id lists (failed/succeeded/changed/no-changes) as bounded comma-joined text, `read`, and `createdAt`. This renders directly into the existing chips and supports the unread/clear queries without a serializer contract.

**Alternative:** a JSON `payload` blob. Flexible but opaque, harder to query for unread/retention, and couples the schema to `ActionSummary` serialization. Rejected.

### 4. Local messages are posted by the client

`showLocalActionMessage` never reaches the server, so it issues a best-effort `POST /notifications/local` with severity, title, message, and workspace id before/while showing the toast. A failed post must not block or delay the toast.

**Alternatives:** omit local messages (rejected: user wants everything notifiable); convert local messages into server-emitted notifications (changes latency/UX and still needs a round trip). Rejected.

### 5. Bell + slide-over panel rendered from JSON

Add a bell button with a badge to `#workspace-nav` and a panel that fetches `GET /notifications`, renders newest first, marks read on open, and clears via `DELETE /notifications`. The existing STOMP subscriptions also prepend to the panel and bump the badge, so it updates live without a reload. Message and project text reuse `escapeHtml`, consistent with toasts.

**Alternative:** server-rendered HTMX fragment for the list. Rejected as the primary path because live STOMP prepends need client rendering regardless; a fragment would duplicate two render paths.

### 6. Read model: global, set on panel open

`read` is a boolean; opening the panel marks all current notifications read via `POST /notifications/read`, and the badge reflects unread count from `GET /notifications`. This matches a single-user app without per-item acknowledgement UI.

**Alternative:** per-notification read toggles and click-to-mark. More UI and state for little value here. Rejected.

## Risks / Trade-offs

- **Persistence/broadcast drift** → single `publish(...)` path; delete the raw `convertAndSend` summary calls.
- **More DB writes during parallel Git/build actions** → small rows, bounded retention, writes already happen in messaging callbacks; verify no throughput regression.
- **Pruning race under concurrency** → prune by selecting the newest N ids and deleting the remainder, tolerant of concurrent inserts; keep the bound generous (200).
- **Best-effort local-message post fails** → toast still shows; the omission is acceptable and never blocks the UI.
- **New entity changes the H2 schema** (`ddl-auto`) → verify startup and that existing data still loads; keep the column lengths bounded (message 2000, lists 4000).
- **Native image reflection gaps** → register `Notification` and the response DTO in `NativeRuntimeHints`, mirroring existing entities/DTOs.
- **Untrusted text in notifications** (project names, branches, server errors) → render as text with `escapeHtml`, same as the toast component.
- **Unknown action types** → fall back to a generic title, as the toast already does.
