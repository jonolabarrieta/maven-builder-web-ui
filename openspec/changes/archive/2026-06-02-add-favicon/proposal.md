## Why

The Maven Builder web interface currently does not have a favicon. Adding a modern, themed favicon will improve the user experience, making the web application easily identifiable in browser tabs, and presenting a more polished and professional interface.

## What Changes

- Add a favicon image file to the static resources of the web application.
- Link the favicon in the HTML templates (`index.html` and `workspace-detail.html`) so that it is loaded by browsers.

## Capabilities

### New Capabilities
- `favicon`: Add a favicon to the web application so that it displays in browser tabs.

### Modified Capabilities
<!-- Existing capabilities whose REQUIREMENTS are changing (not just implementation).
     Only list here if spec-level behavior changes. Each needs a delta spec file.
     Use existing spec names from openspec/specs/. Leave empty if no requirement changes.
-->

## Impact

- `src/main/resources/templates/index.html`: Update `<head>` section to include favicon link.
- `src/main/resources/templates/workspace-detail.html`: Update `<head>` section to include favicon link.
- `src/main/resources/static/favicon.ico` or `src/main/resources/static/img/favicon.png`: Add the icon resource.
