## 1. Spinner Logic Improvements

- [x] 1.1 Update standard form submit event listener in `global-spinner.js` to synchronously capture `defaultPrevented` and safely query `closest` elements, and implement a 2-second auto-hide timeout wrapper.
- [x] 1.2 Update standard link click event listener in `global-spinner.js` to synchronously capture `defaultPrevented`, verify target `.closest` method support, and implement a 2-second auto-hide timeout wrapper.

## 2. Verification & Testing

- [x] 2.1 Manually verify that standard links (like "Export Order") do not permanently freeze the workspace dashboard.
- [x] 2.2 Verify that standard forms (like "Auto-Order" and "Refresh") display the spinner briefly and load the redirected page successfully.
- [x] 2.3 Verify that HTMX calls (like builds, fetch, pull) and fetch requests continue to operate with the loading spinner, starting and stopping correctly.
