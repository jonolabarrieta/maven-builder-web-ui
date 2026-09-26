## ADDED Requirements

### Requirement: Configure Displayed-Version Maven Profile
The system SHALL allow each saved build profile to specify an optional Maven profile ID used to resolve versions displayed in the web UI. The configured ID SHALL be independent of the build command arguments. An empty ID SHALL use the base POM version, and existing build profiles without this field SHALL retain that behavior.

#### Scenario: Create and edit a version source
- **WHEN** a user creates a build profile or edits an existing build profile and supplies `batsdlc` as the displayed-version Maven profile ID
- **THEN** the system stores `batsdlc` with that build profile and shows the saved value in the profile manager.

#### Scenario: No version profile configured
- **WHEN** the active build profile has no displayed-version Maven profile ID
- **THEN** the web UI displays versions resolved from base POM and inherited base properties as before.

### Requirement: Apply Active Version Source
The system SHALL use the active build profile's displayed-version Maven profile ID when calculating versions for imported, refreshed, and already imported projects. Activating a build profile or changing the active build profile's version ID SHALL update displayed project and module versions without requiring a Maven build or manual workspace refresh.

#### Scenario: Switch between configured build profiles
- **WHEN** a user activates a build profile configured with `batsdlc` and views an already imported workspace
- **THEN** project rows, child modules, and project properties display versions resolved with `batsdlc` where it applies.

#### Scenario: Switch back to base versions
- **WHEN** the user activates a build profile with no displayed-version Maven profile ID
- **THEN** the versions shown for existing projects return to their base POM values.

#### Scenario: Build execution remains independent
- **WHEN** a build profile has a displayed-version Maven profile ID that differs from the profiles in its Maven command arguments
- **THEN** builds still execute with the stored command arguments, while displayed versions use the configured ID.

### Requirement: Fallback for Unmatched Version Profile
The system SHALL show the base POM version for a project when the selected displayed-version Maven profile ID is not found in that project's POM or reachable parent POMs. It SHALL retain an existing stored version if the project's POM cannot be read during profile switching.

#### Scenario: Selected profile does not exist for a project
- **WHEN** `batsdlc` is selected and a project's POM tree has no `batsdlc` profile
- **THEN** that project continues to display its base resolved version.

#### Scenario: Project POM is unavailable during switch
- **WHEN** an already imported project's POM is missing or unreadable while changing build profiles
- **THEN** the system retains that project's last stored version and continues updating other projects.
