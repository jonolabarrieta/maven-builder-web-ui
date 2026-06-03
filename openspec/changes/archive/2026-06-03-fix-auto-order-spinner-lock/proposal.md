## Why

The global loading spinner (`global-spinner.js`) gets permanently stuck when users click non-page-transition links, such as "Export Order" (which triggers a file download attachment). This blocks the entire user interface and makes other interactive features, like the "Auto-Order" button, appear broken and unresponsive.

## What Changes

- **Spinner Auto-Hide Timeout**: Update the global loading spinner script to automatically hide after a 2-second timeout on standard form submissions and link clicks, preventing it from remaining stuck when no page transition occurs (e.g., file downloads).
- **Synchronous Event Property Access**: Retrieve the `defaultPrevented` status of events synchronously in submit and click listeners to avoid relying on asynchronous event object persistence, ensuring compatibility across different browser environments.
- **Defensive Target Checks**: Verify that the clicked event target supports the `.closest()` method before executing lookup logic to prevent JavaScript runtime exceptions.

## Capabilities

### New Capabilities

<!-- No new capabilities are being introduced. -->

### Modified Capabilities

- `http-loading-spinner`: Refine the loading indicator's event handling to prevent UI lockout on file downloads or aborted/non-navigational standard requests.

## Impact

- **Affected Code**: `src/main/resources/static/js/global-spinner.js`
- **Dependencies**: None
- **Database**: No schema changes required.
