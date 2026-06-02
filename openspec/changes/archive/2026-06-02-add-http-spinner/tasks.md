## 1. Create Global Spinner Script

- [x] 1.1 Create the `global-spinner.js` file inside `src/main/resources/static/js`
- [x] 1.2 Implement the dynamically injected DOM element and styles for the glassmorphic spinner
- [x] 1.3 Implement tracking logic for HTMX requests using global HTMX event listeners
- [x] 1.4 Implement tracking logic for native JS `fetch` requests by wrapping the global `window.fetch` API


## 2. Integrate Spinner in HTML Templates

- [x] 2.1 Include the spinner script in `src/main/resources/templates/index.html`
- [x] 2.2 Include the spinner script in `src/main/resources/templates/workspace-detail.html`

## 3. Verification

- [x] 3.1 Verify that HTMX requests (e.g. workspace toggle, duplicate, settings update) trigger the spinner
- [x] 3.2 Verify that native `fetch` requests (e.g. bulk actions, update project order) trigger the spinner
- [x] 3.3 Confirm that network/JS errors do not cause the spinner to get permanently stuck on screen
