# Tasks

## 1. Shared Loading and Dialog Components

- [x] 1.1 Refactor `global-spinner.js` to use idempotent active-work handles for HTMX, `fetch`, forms, and eligible links; cover success, error, abort, navigation timeout, and `pagehide`/`pageshow`, and verify with overlapping mocked requests that the overlay remains until the last handle is released.
- [x] 1.2 Centralize the global-spinner exclusion predicate for contextual/background interactions, add accessible waiting semantics and duplicate-click blocking, and verify excluded HTMX controls do not show the global overlay while ordinary controls still do.
- [x] 1.3 Extend `confirmation-modal.js` with info, error, confirmation, and typed-confirmation variants while preserving existing callers; render dynamic values as text, consume callbacks once, and verify each variant's button set and severity styling from the browser console.
- [x] 1.4 Add modal semantics, focus trapping, Escape/backdrop rules, opener-focus restoration, and deterministic replacement of an already-open dialog; verify the complete flow using keyboard-only interaction.
- [x] 1.5 Add a shared HTMX response/send-error bridge with an explicit handled opt-out to avoid duplicate messages, and verify one actionable modal appears for both an HTTP failure and a network failure.

## 2. Top-Level Page Coverage and Native Dialog Removal

- [x] 2.1 Update `settings.html` to load both shared components and replace Java-installation deletion `confirm()` with a styled confirmation that continues through `requestSubmit()`; verify standard settings forms, update HTMX actions, cancellation, and confirmed deletion all show the correct loading/dialog state.
- [x] 2.2 Audit `index.html` shared-asset loading and confirmed workspace deletion, switching programmatic submission to `requestSubmit()` where needed; verify validation and the global spinner still run before navigation.
- [x] 2.3 Replace workspace explorer and bulk-branch validation `alert()` calls in `workspace-detail.html` with one-action informational dialogs; verify empty selection and empty branch input leave the underlying modal/form usable.
- [x] 2.4 Replace add-project, bulk-checkout, bulk-action, unstage/discard, and manual-order failure handling in `workspace-detail.html` with contextual error dialogs and complete non-success/network cleanup; verify each failure clears its spinner/loader, preserves or restores retryable controls, and does not show duplicate dialogs.
- [x] 2.5 Replace branch creation/checkout `alert()` calls in `fragments/branch-selector.html` with error dialogs, delaying any required reload until acknowledgement and resetting the branch loader first; verify HTTP and network failures are readable and keyboard-dismissible.
- [x] 2.6 Harden the profile activation `fetch` flow in `fragments/profile-selector.html` with non-success and network error dialogs, and verify activation success reloads while failure keeps the current page usable.
- [x] 2.7 Audit every remaining Thymeleaf fragment with HTMX or form actions (`explorer`, directory explorer, M2 favorites/info, project children/properties) for explicit global-versus-contextual loading ownership, add opt-outs only where a local loader owns the wait, and verify no interaction displays two competing spinners.

## 3. Regression Guards and Integration Verification

- [x] 3.1 Add a JUnit frontend-resource contract test that verifies all top-level templates include both shared assets and no executable native `alert()`, `confirm()`, or `prompt()` calls remain; run the focused test and confirm it passes.
- [x] 3.2 Add the spinner/modal manual matrix to `TESTING_UPDATES.md`, covering every top-level page, concurrent requests, non-success and network failures, contextual loaders, browser-history restoration, modal variants, typed confirmation, and keyboard focus; execute the matrix and record any environment-limited cases.
- [x] 3.3 Run `mvn test` and a final `rg` audit for request initiators and native dialogs, confirming the full suite passes and every `fetch`, HTMX action, standard form, and same-origin navigation has defined waiting and failure behavior.
