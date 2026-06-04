## Context

The recently introduced global spinner (`global-spinner.js`) manages UI loading indicators. For standard navigations (such as clicking the "Export Order" file download link or submitting form POST requests), the script intercepts the click/submit events and displays a screen-blocking overlay to mask the transition time. 

However, since file downloads (attachment responses) do not unload the current page, the overlay is never removed, permanently locking the screen and preventing interactions with all other buttons, such as "Auto-Order". Additionally, asynchronous access to event properties and non-Element event targets can throw exceptions that crash the script.

## Goals / Non-Goals

**Goals:**
- Implement a robust auto-hide mechanism for standard transitions that unlocks the screen if the page does not unload.
- Ensure event properties are captured safely and synchronously.
- Add target checks before calling `.closest()` to prevent TypeError exceptions.

**Non-Goals:**
- Replacing or overriding HTMX request tracking.
- Modifying backend controller responses or the topological sort service.

## Decisions

### Decision 1: Auto-Hide Timer Fallback for Standard Navigations
- **Rationale**: For standard link clicks and form submissions, show the spinner and schedule a `setTimeout` to decrement the active requests count after 2000ms. If the browser navigates to a new page, the current page unloads, and the timer is discarded. If it is a file download or a cancelled transition, the timer fires, decrements the active request counter, hides the spinner, and unlocks the screen.
- **Alternatives considered**: Filtering out URLs containing `/export-order`. This is fragile as it requires hardcoding URL patterns and does not scale if new downloads are added. A fallback timer is a self-cleaning solution that works for all non-unloading actions.

### Decision 2: Synchronous Capture of Event Properties
- **Rationale**: Access `event.defaultPrevented` and `event.target` synchronously in the event listener closure, storing them in local variables. These variables are then checked asynchronously in the `setTimeout` callback.
- **Alternatives considered**: Reading `event.defaultPrevented` inside the asynchronous `setTimeout`. In some environments, event object properties are cleared or reused once the Synchronous Event Loop completes, which could cause state errors.

### Decision 3: Safe Element Target Lookup
- **Rationale**: Add `typeof event.target.closest === 'function'` check before querying parent elements to prevent script crashes when events are dispatched on `document` or `window`.
- **Alternatives considered**: None. This is standard frontend defensive programming.

## Risks / Trade-offs

- **[Risk]**: A very slow page transition (taking > 2 seconds) will cause the spinner to hide before the page transitions.
  - **Mitigation**: A 2-second timeout is a good UX trade-off. It is better for the spinner to disappear slightly early on a very slow page than to permanently lock the UI on any download.
