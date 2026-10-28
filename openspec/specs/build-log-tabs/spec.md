# build-log-tabs Specification

## Purpose
TBD - created by archiving change build-log-follow-and-wheel-close. Update Purpose after archive.
## Requirements
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

### Requirement: RUN log tabs
The Build Output console SHALL show one tab for each RUN entry that has a process or a retained log. Each RUN tab SHALL be visually distinct from build tabs and SHALL be labelled with the entry name. Each RUN tab SHALL show the entry's state (running, stopped or crashed) and SHALL provide the actions that apply to that state:

- Stop and Restart while the entry is running
- Restart once the entry is stopped or crashed

While its process is running, a RUN tab SHALL NOT be closable by the close button, by a middle click, or by any action that closes all tabs. Once the entry is stopped or crashed, the tab SHALL keep its output and SHALL become closable, and closing it SHALL dismiss that entry's retained log. A crashed tab SHALL be marked as failed and SHALL show the exit code. RUN output SHALL NOT trigger the follow toggle.

#### Scenario: Running tab cannot be closed
- **WHEN** "api" is running and the user middle-clicks its tab or uses close all tabs
- **THEN** the "api" tab SHALL remain open.

#### Scenario: Crashed tab keeps its log
- **WHEN** "front" exits with code 1
- **THEN** its tab SHALL remain open, marked as failed with exit code 1, with its output intact, and SHALL offer Restart and close.

#### Scenario: Restart from the tab
- **WHEN** the user activates Restart on a crashed tab
- **THEN** the entry SHALL start again, its tab SHALL return to running, and a restart marker SHALL separate the new output from the old output.

#### Scenario: Service output does not steal focus
- **WHEN** follow is enabled, a build is producing output, and "api" writes log lines
- **THEN** the active tab SHALL NOT switch to "api".

### Requirement: Restore RUN tabs after reload
When the workspace page loads, the console SHALL recreate a tab for every entry of that workspace that is running or has a retained log. Each tab SHALL be filled with that entry's buffered output and SHALL then continue with live output, without losing or duplicating lines at the boundary between the buffered and the live output.

#### Scenario: Reload with running services
- **WHEN** "api" and "worker" are running and the user presses F5
- **THEN** the reloaded page SHALL show the "api" and "worker" tabs in the running state with their recent output, and new lines SHALL continue to appear.

#### Scenario: Reload after closing a stopped tab
- **WHEN** the user closed the tab of a stopped entry and then reloads
- **THEN** that tab SHALL NOT reappear.
