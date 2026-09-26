## Context

`BuildProfile` stores a name, Maven command arguments, and an active flag. `MavenService.parsePom` uses `MavenXpp3Reader` and resolves version expressions from base POM and parent properties. `WorkspaceService` persists the resolved value in `MavenProject.version`, which the workspace list, child-module fragment, and properties view render. The example POM declares `<version>${revision}</version>` and a `batsdlc` profile whose `revision` refers to `r01f.version`.

## Goals / Non-Goals

**Goals:**
- Configure the Maven profile used to display versions separately for each saved build profile.
- Apply the active selection immediately to existing projects and consistently to later imports and refreshes.
- Resolve profile properties from parent POMs for child modules, including chained references.
- Preserve the current base version behavior when no version profile is configured.

**Non-Goals:**
- Change the arguments or Maven profiles used to run a build.
- Implement the entire Maven effective-model lifecycle, including settings.xml, environment activation, or command-line property overrides.
- Modify project POM files.

## Decisions

### Store the Maven profile ID on `BuildProfile`

Add a nullable `versionProfileId` string. The selector uses the active build profile's value; a blank value means base POM properties. The manager offers an optional text input when adding a profile and a way to edit this value on saved profiles. This keeps the relationship explicit even when command arguments contain `-P`, and supports profiles configured before this feature exists.

**Alternative considered:** Parse `-P` from the build command. This can contain multiple profiles or other arguments and would couple display behavior to build execution, so it does not meet the requested independent configuration.

### Resolve versions from structured POM models

Extend Maven version resolution to accept an optional profile ID. For each project, read its POM and reachable parent POMs, gather base properties and the matching profile's properties in inheritance order, then resolve the effective version expression recursively. Project-local definitions take precedence over inherited definitions, with selected-profile values overriding base values at their corresponding level. Detect cycles and leave unresolved expressions visible rather than looping. This remains a read-only parse; it does not launch Maven for each project.

If the selected profile is absent from a project's POM and ancestors, retain its base resolved version. A missing or unreadable POM must not clear a previously stored version. A profile that exists but does not define a version-related property likewise leaves the base version intact.

**Alternative considered:** Run `mvn help:evaluate -P...` per module. That would handle more Maven features but is slow for a workspace scan and may require network or installed plugins. The requested POM-property case can be resolved with the model API already in use.

### Recalculate stored versions when selection changes

Place version synchronization in a service called after activating a build profile or editing the active profile's version ID. It iterates existing projects, updates only `version`, and leaves build state, ordering, and other metadata intact. Imports, manual additions, workspace refresh, and workspace import use the active version profile when parsing POMs. The activation UI reloads the current workspace view after the update so all visible version surfaces show the same values.

**Alternative considered:** Resolve on each template render. That would repeat file reads and leave other consumers of `MavenProject.version` inconsistent with the UI.

## Risks / Trade-offs

- A large workspace may make switching profiles noticeable because each POM is read. Keep the operation limited to version updates and avoid redundant project scans.
- A profile ID is a global setting on the selected build profile but may not exist in every project's POM tree. Those projects retain their base version.
- This resolver intentionally covers POM and parent profile properties, not every possible Maven activation or interpolation source; document that boundary in the UI or project documentation where appropriate.
