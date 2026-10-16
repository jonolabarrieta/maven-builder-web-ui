## Context

The Build Output panel keeps one tab per log label plus a `Global` tab. `appendLog(label, content)` always appends to Global and, for a project label, creates a tab on first use. `createTab` switches to the new project tab only when the active tab is still `Global`; after that, later projects build without the view following them. `switchTab(label)` toggles container/tab visibility and scrolls to the bottom. `closeTab(label, event)` removes the tab group and container, drops the `projectTabs`/`logLines` entries, and falls back to `Global` when the active tab is closed. Logs arrive over STOMP `/topic/logs` as `{label, content}`.

This is a Thymeleaf + HTMX page with a large inline script (`<script th:inline="javascript">`) that existing headless-Chrome regressions already exercise by extracting and running the production script against a small DOM.

## Goals / Non-Goals

**Goals**

- Provide an opt-in "follow the build" mode that keeps the active tab on the project currently producing output, remembered across reloads.
- Let the user close a project log tab with a middle mouse click, keeping the existing ✕ button.
- Keep the default behavior identical to today when follow mode is off.

**Non-Goals**

- Changing how logs are received, stored, or downloaded; the Global tab and per-project tabs keep their content.
- Auto-opening the browser or a separate window; "screen" means a Build Output log tab.
- Reordering tabs, renaming, or persisting which tabs are open.
- A wheel-scroll gesture to close tabs (only the middle button closes).

## Decisions

### 1. Follow the project on each labeled log line

In `appendLog`, when follow mode is enabled and the incoming line has a non-`Global` label, call `switchTab(label)` unless it is already active. Following on every labeled line (not just tab creation) keeps the view on the project that is currently emitting output, which is what "always open the last screen" means.

**Alternative:** switch only when a new tab is created. Rejected: it would follow only the first line of each project and miss ongoing output; and it would not follow a project whose tab already exists.

**Alternative:** introduce a distinct "current project" event from the backend. Rejected: logs already carry the label; no backend change is needed.

### 2. Opt-in toggle, persisted, default off

Add a checkbox-styled switch in the Build Output header (next to Clear All Logs / Download) with `aria-pressed`. On change, store the value under a namespaced `localStorage` key (`mvnbuilder.buildLogFollow`). On page load, read it (guarded by try/catch) and set the toggle; a missing key means off. When off, `appendLog` behaves exactly as today.

**Alternative:** always follow with no toggle. Rejected: it would fight a user who wants to read a specific finished project while others still build.

**Alternative:** session-only (no persistence). Rejected by the user, who wants it to "always" follow once enabled.

### 3. Middle-click closes the tab

Attach an `auxclick` handler (Button `1`) on the tab group and its label button that calls `closeTab(label, event)`; also `mousedown` with `button === 1` calls `preventDefault()` to suppress the browser's middle-click autoscroll. The existing ✕ button is unchanged, and both paths share `closeTab`, so fallback-to-Global and focus restoration behave identically.

**Alternative:** close on wheel scroll over the tab. Rejected: surprising and error-prone with horizontal tab scrolling; the user confirmed the middle button.

**Alternative:** remove the ✕ and rely only on middle click. Rejected: the ✕ is discoverable and keyboard-accessible; keeping both is strictly better.

### 4. Keep the change frontend-only

All logic lives in `workspace-detail.html`'s inline script; no controller, DTO, or persistence changes. This keeps the change small and independently testable in the existing Chrome harness.

## Risks / Trade-offs

- **Middle-click autoscroll** → `preventDefault()` on `mousedown` button 1, plus tests asserting the default is suppressed.
- **Follow fights a manual click** → expected while follow is on; turning the toggle off stops it. The toggle is the documented escape hatch.
- **Global interleaving** → only non-`Global` labels trigger following, so the aggregated Global stream does not steal focus.
- **localStorage unavailable** (private mode/disabled) → reads/writes wrapped in try/catch with an in-memory default of off; the toggle still works for the session.
- **Existing first-tab auto-switch** → `createTab`'s "switch from Global" behavior is preserved; follow mode extends it rather than replacing it.
- **Multiple tabs with the same label** → labels are unique per project artifact ID, so following is deterministic.
