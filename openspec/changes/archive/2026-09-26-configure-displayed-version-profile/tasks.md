## 1. Profile Configuration

- [x] 1.1 Add the optional displayed-version Maven profile ID to `BuildProfile`, preserving null or blank as the existing base-version behavior.
- [x] 1.2 Extend `BuildProfileController` add and update handlers to validate, save, and expose the optional ID; ensure the activate handler selects an existing profile and triggers version synchronization.
- [x] 1.3 Update `fragments/profile-selector.html` with the optional field for new profiles and an edit control for saved profiles; refresh the current workspace view after active-profile changes.

## 2. Version Resolution

- [x] 2.1 Extend `MavenService` with selected-profile-aware version resolution across project and parent POMs, including chained expressions, precedence, unmatched-profile fallback, and cycle detection.
- [x] 2.2 Add focused MavenService tests for the supplied `batsdlc`-style POM, inherited child versions, base fallback, and cyclic or unresolved properties.

## 3. Synchronization And Import

- [x] 3.1 Add a service operation that recalculates only the stored versions of existing projects after activation or editing the active profile; preserve prior values for missing or unreadable POMs.
- [x] 3.2 Pass the active version profile through all `WorkspaceService` parse paths: workspace scan/refresh, manual project addition, and workspace import.
- [x] 3.3 Add controller/service tests for switching profiles, editing the active profile, and ensuring build commands are unaffected.
- [x] 3.4 Verify version text updates in `workspace-detail.html`, `fragments/project-children.html`, and `fragments/project-properties.html` after switching, including return to base versions.
