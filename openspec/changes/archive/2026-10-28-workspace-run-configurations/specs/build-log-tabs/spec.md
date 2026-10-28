# Spec Delta

## ADDED Requirements

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
