## Why

The version shown for a project is read from base POM properties when the workspace is imported or refreshed. Some projects define a different `${revision}` in a Maven profile. In the supplied example, `batsdlc` sets `revision` through `${r01f.version}`, so the web UI shows `R01F-BASE-VERSION` even when the user wants to inspect the `batsdlc` version. The selected build profile should carry an explicit, independent choice of which Maven profile supplies displayed versions.

## What Changes

- Add an optional Maven profile ID for displayed versions to each saved build profile. An empty value keeps the current base POM behavior, including for existing profiles.
- Let users set or change that ID in the build profile manager. Switching the active build profile updates displayed versions without requiring a build or manual workspace refresh.
- Resolve version properties from the selected Maven profile in the project or its parent POMs, including chained expressions such as `revision` -> `r01f.version`.
- Use the same version selection on import, workspace refresh, and subsequent project views. If the selected profile does not apply to a project, display its base POM version.

## Capabilities

### New Capabilities

- `build-profile-version-selection`: Configure a Maven profile ID per build profile and use it to select the version shown in the web UI.

### Modified Capabilities

- `recursive-modules-display`: Resolve and display parent and child module versions with properties from the selected Maven profile when available.

## Impact

- Build profile persistence, controller, and manager fragment gain an optional displayed-version profile ID.
- Maven version resolution and workspace import/refresh paths gain selected-profile awareness; existing project versions are recalculated when the active selection changes.
- The workspace project list, module navigator, and project properties continue reading the stored version, now synchronized with the active selection.
