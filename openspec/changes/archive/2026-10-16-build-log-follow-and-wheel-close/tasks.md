## 1. Follow Toggle (UI and Persistence)

- [x] 1.1 Add a "Follow build" toggle to the Build Output header in `workspace-detail.html` (next to Clear All Logs), with `aria-pressed` and a stable ID.
- [x] 1.2 Add follow-mode state plus `localStorage` read/write helpers (namespaced key, try/catch guarded), reading the stored value on script init and applying it to the toggle.
- [x] 1.3 Wire the toggle's change handler to update the state, persist it, and reflect the on/off styling.

## 2. Follow Behavior

- [x] 2.1 In `appendLog`, when follow is enabled and the incoming line has a non-`Global` label, switch to that project's tab unless it is already active.
- [x] 2.2 Keep the existing behavior unchanged when follow is off and when the label is `Global`.
- [x] 2.3 Preserve the existing `createTab` "switch from Global" behavior so the first project still opens.

## 3. Close Tab with the Mouse Wheel

- [x] 3.1 In `createTab`, add an `auxclick` handler (middle button) on the tab group/label that calls `closeTab(label, event)`.
- [x] 3.2 Add a `mousedown` handler for the middle button that calls `preventDefault()` to suppress the browser's autoscroll.
- [x] 3.3 Confirm `closeTab` (shared by the ✕ button and middle click) still removes the tab, and falls back to Global when the active tab is closed.

## 4. Tests and Verification

- [x] 4.1 Add a headless-Chrome regression that drives the production script: toggle defaults off, enabling persists and is re-read on load, follow switches the active tab to the building project, follow off does not switch, and Global lines do not switch.
- [x] 4.2 Extend that regression to cover middle-click closing a tab, autoscroll suppression, fallback to Global when the active tab is closed, and the ✕ button still closing a tab.
- [x] 4.3 Run the full test suite (including the existing frontend regressions) and `openspec validate build-log-follow-and-wheel-close`.
