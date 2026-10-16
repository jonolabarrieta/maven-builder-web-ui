## Why

During a multi-project build the Build Output console creates one tab per project, but it only auto-switches once: as soon as the first project tab exists, later projects keep building invisibly until the user notices and clicks. The user asked for the console to always jump to the project currently producing output. Separately, project log tabs accumulate with no quick way to dismiss them — the only affordance is a small ✕ button — so the user wants to close a tab with a middle mouse click (the wheel button).

## What Changes

- Add a **Follow build** toggle to the Build Output header. When enabled, the active log tab automatically switches to whichever project is currently producing output (the "last screen"). The preference is remembered between page loads (localStorage) and defaults to off.
- Allow closing a project log tab with a **middle mouse click** (wheel button) on the tab. The existing ✕ close button remains.
- Keep the current behavior when the toggle is off: tabs are created but the active tab does not change after the first project.

## Capabilities

### New Capabilities

- `build-log-tabs`: Interactive behavior of the Build Output log tabs — following the project that is currently building, and closing a tab with the mouse wheel.

### Modified Capabilities

<!-- None: backend behavior and existing build specs are unchanged. -->

## Impact

- `workspace-detail.html` only: the Build Output header gains a toggle, and the log-tab script (`appendLog`, `createTab`, `switchTab`, `closeTab`) learns to follow output and to close on middle click, with a localStorage-backed preference.
- Locked to the frontend: no backend, API, database, or spec-delta changes elsewhere.
- Tests: a headless-Chrome regression covering the toggle (default off, persistence, follow on/off, Global does not trigger follow) and wheel-close (middle click closes, active tab falls back to Global, no browser autoscroll).
