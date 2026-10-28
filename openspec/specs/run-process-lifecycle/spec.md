# run-process-lifecycle Specification

## Purpose
Manages long-running processes started from RUN entries independently of builds, from start through stop, restart, crash and application shutdown.

## Requirements
### Requirement: Independent RUN process state
The system SHALL track each started RUN entry as its own process with one of these states:

- **running**
- **stopped**, after the user stopped it
- **crashed**, after it exited on its own with any exit code, or failed to start

An entry SHALL have at most one live process at a time. RUN processes SHALL NOT count as active builds: the build process indicator and the build stop action SHALL ignore them, and stopping a build SHALL NOT terminate any RUN process. The state SHALL be scoped to the workspace that started the entry.

#### Scenario: Stopping a build leaves services running
- **WHEN** "api" is running and the user stops an in-progress workspace build
- **THEN** the build SHALL be terminated and "api" SHALL remain running.

#### Scenario: Running service does not look like a build
- **WHEN** only RUN processes are alive
- **THEN** the build process indicator SHALL show no active build.

#### Scenario: Process exits on its own
- **WHEN** a running entry's process exits with code 1
- **THEN** its state SHALL become crashed and the exit code SHALL be reported.

#### Scenario: Process exits cleanly
- **WHEN** a running entry's process exits with code 0 without being stopped
- **THEN** its state SHALL become crashed, because it was not stopped by the user, and exit code 0 SHALL be reported.

### Requirement: Stop and restart per entry
The system SHALL let the user stop and restart each running entry, and restart a stopped or crashed entry, without affecting other entries. Stop SHALL terminate the entry's whole process tree, including child processes such as the JVM forked by Maven or the node process started by npm, on every supported operating system. The system SHALL first request a graceful termination and SHALL force termination of the remaining processes after a bounded timeout. Restart SHALL stop the entry and then start it again using its current saved configuration.

#### Scenario: Stop kills child processes
- **WHEN** the user stops an entry running `mvn spring-boot:run` on Linux
- **THEN** the Maven process and the application JVM it forked SHALL both be terminated, and the port the application used SHALL be released.

#### Scenario: Restart one entry
- **WHEN** "api" and "front" are running and the user restarts "api"
- **THEN** "api" SHALL be stopped and started again with a new process, and "front" SHALL be unaffected.

#### Scenario: Restart uses edited configuration
- **WHEN** the user edits the command of a running entry and then restarts it
- **THEN** the new process SHALL use the edited command.

#### Scenario: Process ignores graceful termination
- **WHEN** a process tree is still alive after the graceful-termination timeout
- **THEN** the remaining processes SHALL be terminated forcibly and the entry SHALL become stopped.

### Requirement: RUN log streaming and buffering
The system SHALL deliver each RUN process's output, with stderr merged into stdout and ANSI escape sequences removed, only to clients viewing the workspace that owns the process. The system SHALL keep a bounded buffer of the most recent output lines for each entry's latest process in memory. The buffer SHALL keep at least 2,000 lines, and older lines SHALL be discarded first. The buffer SHALL remain available after the process stops or crashes until the entry is started again or its log is dismissed.

#### Scenario: Logs stay in their workspace
- **WHEN** workspace A runs "api" and the user is viewing workspace B
- **THEN** "api"'s output SHALL NOT appear in workspace B's console.

#### Scenario: Long-running output is bounded
- **WHEN** a process has written 50,000 lines
- **THEN** the server SHALL retain only the most recent buffer-sized portion for that entry.

### Requirement: Shutdown terminates RUN processes
When mvnBuilder shuts down, the system SHALL terminate the process tree of every RUN process before exiting. The system SHALL NOT keep RUN state across restarts: after startup, every entry SHALL be stopped.

#### Scenario: Closing the application
- **WHEN** "api" and "front" are running and the user exits mvnBuilder
- **THEN** both process trees SHALL be terminated, and no RUN child process SHALL remain alive.

### Requirement: Build guard for running projects
Before a build starts that includes a project linked to one or more running entries, the system SHALL ask the user to choose one of these actions:

- **Stop, build and restart**: stop those entries, run the build, and restart the same entries when the build finishes successfully.
- **Build anyway**: run the build and leave the entries running.
- **Cancel**: start nothing.

If the build fails or is stopped, the stopped entries SHALL remain stopped and the user SHALL be told which entries were not restarted. The guard SHALL apply to the single-project, range, selection, group and workspace build actions.

#### Scenario: Rebuild a running service
- **WHEN** "api" is running for project `api-service`, the user builds `api-service` and chooses Stop, build and restart
- **THEN** "api" SHALL be stopped, `api-service` SHALL be built, and "api" SHALL be started again after a successful build.

#### Scenario: Build fails after stopping
- **WHEN** the user chooses Stop, build and restart and the build fails
- **THEN** "api" SHALL remain stopped and the failure feedback SHALL state that "api" was not restarted.

#### Scenario: Build anyway
- **WHEN** the user chooses Build anyway
- **THEN** the build SHALL start and "api" SHALL keep running.

#### Scenario: No running entries involved
- **WHEN** a build includes no project linked to a running entry
- **THEN** the build SHALL start without the prompt.
