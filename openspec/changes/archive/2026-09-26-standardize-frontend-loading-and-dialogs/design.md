# Design

## Context

The UI has three top-level Thymeleaf pages. `index.html` and `workspace-detail.html` load the shared spinner and confirmation scripts, while `settings.html` loads neither. The spinner already intercepts HTMX, `fetch`, forms, and links, but uses a mutable counter and does not fully account for cancellation, browser-history restoration, programmatic form submission, or every opt-out path. Contextual loaders also exist in the branch and updater panels.

The reusable confirmation component already covers simple and typed confirmations, but always models a two-action confirmation, injects message HTML, and lacks complete dialog/focus semantics. Remaining native dialogs occur in workspace explorer, bulk branch, sortable-order, branch-selector, and Java-installation deletion flows. Several error paths reload immediately, which would make a replacement modal disappear before it can be read.

## Goals / Non-Goals

**Goals:**

- Establish one shared loading lifecycle and one shared dialog API across all top-level pages and dynamically swapped fragments.
- Preserve existing endpoint contracts and contextual progress UI while making success, failure, abort, and retry states deterministic.
- Make dialogs keyboard-accessible and safe for server- or user-derived text.
- Add automated source-level regression guards without introducing a JavaScript build toolchain.

**Non-Goals:**

- Tracking WebSocket lifetime or showing a blocking spinner for the duration of Maven/Git background execution after its initiating HTTP request returns.
- Redesigning ordinary page modals such as workspace editing, directory browsing, project properties, or update details.
- Changing controller responses, translating all existing English/Spanish copy, or adding a frontend framework.

## Decisions

### 1. Load shared UI infrastructure on every top-level page

`global-spinner.js` and `confirmation-modal.js` will be included once by `index.html`, `workspace-detail.html`, and `settings.html`. Dynamically returned fragments will consume the globals supplied by their host page and will not load duplicate scripts.

**Alternative considered:** create a Thymeleaf `<head>` fragment. That is a useful broader cleanup, but it expands this focused change and risks unrelated template differences. Explicit includes are easier to audit now.

### 2. Use idempotent work handles instead of an unqualified counter

The spinner module will retain automatic HTMX/`fetch`/form/link integration but internally acquire and release opaque work handles. Releasing the same handle twice is harmless, and all success, error, abort, timeout, `pagehide`, and `pageshow` paths converge on cleanup. A single overlay remains visible while the handle set is non-empty.

An explicit opt-out marker remains available for interactions that own a contextual indicator or intentionally start background work. The opt-out is applied at the initiating control/form, and automatic integrations use the same predicate. Standard navigation detection continues to ignore new-tab, modified, download, external, hash, and cancelled interactions. Confirmed forms use `requestSubmit()` so validation and spinner tracking still run.

**Alternatives considered:** rely only on per-button spinners, which duplicates lifecycle code; or keep a numeric counter, which cannot identify duplicate terminal events and is harder to reset safely.

### 3. Define ownership between global and contextual loading states

The global overlay is the default for waits. Existing local indicators remain only where the user needs to retain modal/panel context (branch lists, bulk checkout, updater). Their triggers opt out of the global overlay where showing both would be redundant. Every local indicator gets one reset routine used by success, HTTP error, thrown/network error, and cancellation paths. The spinner covers only the initiating HTTP round trip; WebSocket-streamed background jobs keep their existing logs/status UI.

**Alternative considered:** always show both global and local loaders. That is technically simple but obscures useful contextual progress and can feel like two unrelated operations.

### 4. Extend the existing modal rather than introduce a second component

`confirmation-modal.js` remains the single global component and gains explicit notice/error/confirm variants, while retaining typed confirmation. Notice and error variants expose one close action; confirmation variants expose confirm and cancel. Callbacks are consumed once and cleared on close so repeated openings cannot execute stale actions. A deterministic replace policy handles a new dialog request while one is open.

Dynamic values are assigned with `textContent`. Rich markup is not accepted from call sites; emphasis and severity are rendered by the component structure. A shared HTMX error bridge maps unhandled response/send errors to the error variant, while fetch call sites keep enough local handling to provide action-specific messages; an explicit handled marker prevents duplicate dialogs. Branch errors that currently reload immediately will wait for acknowledgement before any required reload. Contextual loaders are reset before opening an error dialog.

**Alternatives considered:** use native `<dialog>`, whose focus/backdrop behavior differs across embedded runtimes; or add SweetAlert, which adds an unnecessary CDN/runtime dependency.

### 5. Make modal accessibility part of the component boundary

The overlay/panel will expose `role="dialog"`, `aria-modal="true"`, labelled title/description, initial focus, Tab/Shift+Tab trapping, Escape handling, and restoration of the opener. Destructive confirmations retain visible severity and typed confirmation where already required. The spinner uses status semantics and blocks pointer activation behind its overlay while visible.

### 6. Guard the audited surface with Java tests and a manual matrix

A lightweight JUnit resource test will scan top-level templates for the shared asset includes and scan frontend resources for executable native dialog calls. Focus, keyboard, concurrent-request, contextual-loader, and failure/retry behavior will be covered by a concise manual matrix because the repository has no browser-test toolchain.

**Alternative considered:** introduce Playwright or another JavaScript test runner. That would materially expand dependencies and CI setup for a small vanilla-JS surface.

## Risks / Trade-offs

- **[Risk] Global interception can count an interaction twice** → Centralize the exclusion predicate and ensure each transport owns exactly one handle.
- **[Risk] A missing terminal event leaves the overlay visible** → Release on every transport terminal path and reset on page lifecycle restoration.
- **[Risk] Blocking overlays are intrusive for long background jobs** → Track only the initiating HTTP request and explicitly opt out when existing status/log UI owns progress.
- **[Risk] Replacing `innerHTML` removes inline formatting in current copy** → Move severity and emphasis into the component's fixed structure and render dynamic values safely as text.
- **[Risk] Source-level tests do not prove browser focus behavior** → Pair them with the manual interaction/accessibility matrix and keep the modal logic isolated for future browser tests.

## Migration Plan

1. Harden the shared spinner and dialog components while retaining the current `ConfirmationModal.show(...)` entry point.
2. Include both assets in `settings.html` and migrate native-dialog call sites one flow at a time, verifying local-loader cleanup.
3. Add regression guards and run the full Maven test suite plus the frontend manual matrix.
4. Roll back by reverting the template call sites and shared asset changes together; no data or backend migration is involved.
