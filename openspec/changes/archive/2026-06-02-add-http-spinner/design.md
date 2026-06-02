## Context

Currently, the Maven Workspace Manager application performs asynchronous operations using both HTMX and standard JavaScript `fetch`. These actions trigger background tasks (such as checking out git branches, rebuilding projects, etc.) but lack unified visual loading indicators. Adding a global spinner will improve the user experience by providing clear visual feedback that a background HTTP operation is active.

## Goals / Non-Goals

**Goals:**
- Create a global spinner overlay/indicator that automatically triggers and displays when any HTTP request is in progress.
- Support both HTMX and native `window.fetch` requests.
- Prevent the spinner from getting stuck by correctly handling success, failure, abort, and error scenarios.
- Keep the implementation clean and modular by injecting the spinner dynamically using a single external JS script included in the main template files.

**Non-Goals:**
- Disable or block page interactions via a full-page modal/backdrop (unless specific forms already do so). The spinner should be a non-blocking floating indicator.
- Track non-HTTP traffic such as active WebSocket connections.

## Decisions

### Decision 1: Shared JavaScript Loader File
To avoid repeating HTML and JS boilerplates across `index.html` and `workspace-detail.html`, we will create a dedicated JS file: `src/main/resources/static/js/global-spinner.js`. This script will:
- Dynamically inject the spinner UI into the DOM once the page loads.
- Track active HTTP requests from both HTMX and `fetch`.
- Toggle the visibility of the spinner.

### Decision 2: Tracking HTMX Requests
We will register event listeners for:
- `htmx:beforeRequest` to increment the active request count.
- `htmx:afterRequest` (which handles both success and failure/completion of HTMX calls) to decrement the active request count.

### Decision 3: Tracking Fetch Requests
We will monkey-patch (override) `window.fetch`. The overridden method will:
1. Increment the active request count.
2. Delegate to the original `fetch`.
3. In a `.finally()` block, decrement the active request count and update the UI visibility.

### Decision 4: Spinner UI Style and Location
We will place a floating glassmorphic indicator in the top-right corner of the page:
- It will have a fixed z-index (`z-[9999]`) to float above all other content.
- We will style it using Tailwind CSS (e.g. `bg-white/80`, `backdrop-blur-sm`, `shadow-lg`, `rounded-xl`, `border-gray-200`) and standard Tailwind animation (`animate-spin`).
- We will include fallback CSS rules in the script (or templates) just in case Tailwind CDN behaves unexpectedly with dynamically generated content.

## Risks / Trade-offs

- **[Risk] Spinner getting stuck due to unhandled exceptions** → **[Mitigation]** Use `.finally()` blocks for `fetch` overrides, and listen to all terminal HTMX events (errors/completions). Always clamp the active request counter to a minimum of 0.
- **[Risk] Spinner flickering for ultra-fast requests** → **[Mitigation]** Standard behavior is to show/hide immediately. If flickering becomes an issue, we can introduce a brief debounce delay (e.g., 200ms) before showing the spinner.
