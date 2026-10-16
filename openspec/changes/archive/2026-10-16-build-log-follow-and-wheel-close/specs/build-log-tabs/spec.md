## ADDED Requirements

### Requirement: Follow the Active Build
The Build Output console SHALL provide a "follow" toggle that, when enabled, keeps the active log tab on the project that is currently producing output. The toggle SHALL default to off, SHALL be remembered between page loads, and when it is off the active tab SHALL NOT change as projects produce output beyond the existing first-project behavior.

#### Scenario: Toggle is available and off by default
- **WHEN** the user opens a workspace detail page for the first time
- **THEN** the Build Output header shows a follow toggle in the off state and no project tab is auto-selected on its behalf.

#### Scenario: Toggle state is remembered
- **WHEN** the user enables the follow toggle and later reloads the page
- **THEN** the toggle is enabled again on load.

#### Scenario: Enabled toggle follows the building project
- **WHEN** follow is enabled and a project produces log output while another tab is active
- **THEN** the console switches the active tab to that project and scrolls its output to the bottom.

#### Scenario: Disabled toggle does not follow
- **WHEN** follow is disabled and a project produces log output while another tab is active
- **THEN** the active tab does not change.

#### Scenario: Global output does not trigger following
- **WHEN** a log line without a project label arrives
- **THEN** the console does not change the active tab, even when follow is enabled.

### Requirement: Close a Log Tab with the Mouse Wheel
The Build Output console SHALL close a project log tab when the user clicks the tab with the middle mouse button (the wheel), and the existing close button SHALL continue to close tabs.

#### Scenario: Middle click closes a project tab
- **WHEN** the user middle-clicks a project log tab
- **THEN** that tab and its output are removed.

#### Scenario: Middle click does not start browser autoscroll
- **WHEN** the user middle-clicks a project log tab
- **THEN** the browser's middle-click autoscroll is suppressed and only the tab is closed.

#### Scenario: Closing the active tab falls back to Global
- **WHEN** the user middle-clicks the currently active project tab
- **THEN** the Global tab becomes active.

#### Scenario: Close button still works
- **WHEN** the user activates the close (✕) button on a project tab
- **THEN** that tab is removed exactly as before.
