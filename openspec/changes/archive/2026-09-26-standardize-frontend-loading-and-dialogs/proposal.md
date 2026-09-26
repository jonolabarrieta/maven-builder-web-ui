# Proposal

## Why

The frontend gives inconsistent feedback during waits and failures: `settings.html` does not load the global spinner, several asynchronous paths still use native `alert()`/`confirm()`, and some failed requests leave the user without an in-page explanation. A consistent loading and dialog layer will make every page predictable and keep browser-native popups out of the application.

## What Changes

- Load the shared spinner and dialog components on every top-level frontend page.
- Standardize spinner coverage for HTMX, `fetch`, normal forms, and same-origin navigation, including concurrent requests, failures, and explicit opt-outs for non-blocking background activity.
- Preserve contextual loading indicators where a modal or panel already owns the wait state, without double-counting or leaving stale UI behind.
- Extend the reusable HTML/CSS dialog to support confirmations, informational validation messages, and request errors with consistent Indigo/glass styling.
- Replace every remaining native `alert()` and `confirm()` call in workspace, branch, and settings flows.
- Define accessible dialog behavior: semantic roles, focus management, keyboard dismissal, safe message rendering, and restoration of the triggering control.
- Add frontend regression coverage and a manual audit checklist for every interactive template.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `http-loading-spinner`: Guarantee consistent, balanced loading feedback across all top-level pages and supported request/navigation paths.
- `confirmation-modal`: Expand the reusable modal contract to cover confirmation, information, validation, and error dialogs without native browser popups.

## Impact

- Frontend assets: `global-spinner.js` and `confirmation-modal.js`.
- Thymeleaf pages/fragments: `index.html`, `settings.html`, `workspace-detail.html`, `branch-selector.html`, and asynchronous explorer/profile flows.
- No backend API, persistence, or dependency changes are expected; existing endpoint response text/status remains the source of request-error detail.
