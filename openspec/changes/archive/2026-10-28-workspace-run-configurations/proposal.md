# Proposal

## Why

mvnBuilder can build a workspace but cannot start what it builds. Users still open separate terminals to launch services such as `mvn spring-boot:run`, `npm run dev` or `docker compose up`. The current process layer cannot host them either: it has one global process set, Stop kills every process, logs are not scoped to a workspace, and on Linux and macOS it kills only the parent process.

## What Changes

- Add a per-workspace **RUN configuration**: an ordered list of entries. Each entry has:
  - a name
  - an optional project, which can be Maven, folder or imported
  - a working directory, which defaults to the project's directory
  - a shell command
  - optional environment variables
  - an optional setting to use the workspace's JAVA_HOME

  One project may have several entries.
- Add a **RUN** action that opens a modal where the user chooses which entries to start. The modal preselects the last selection, and entries that are already running are shown as disabled.
- Add a **run process registry**, separate from builds, with these behaviours:
  - Each process has a state: running, stopped or crashed.
  - The user can stop or restart each process.
  - Stopping a process terminates its whole process tree.
  - Every RUN process is terminated when the application shuts down.
- Show **RUN log tabs** in the Build Output console:
  - A tab cannot be closed while its process is alive.
  - After a page reload, the tabs are restored from a bounded server-side log buffer.
  - A crashed tab stays open, marked as failed, and can be restarted or closed.
- Add a **build guard**: building a project that has a running entry asks whether to stop it, build, and then restart it.
- Include the RUN configuration in the workspace **export and import**.
- Stopping builds no longer terminates RUN processes.

## Capabilities

### New Capabilities
- `workspace-run-configurations`: persisted RUN entries per workspace, the editing UI, and the launch selection modal.
- `run-process-lifecycle`: the registry for long-running processes. It covers state, stop and restart, process-tree termination, shutdown cleanup, log buffering and scoping, and the build-while-running guard.

### Modified Capabilities
- `build-log-tabs`: adds RUN tabs that cannot be closed while their process is alive, are restored after a reload, and show the crashed state.
- `workspace-import-export`: the versioned export and import include RUN entries.

## Impact

- **Backend:**
  - New entities, a repository and services: `RunEntry`, `RunProcessRegistry` and `RunConfigurationService`.
  - New controller endpoints under `/workspaces/{id}/run`.
  - The build stop endpoint is scoped to build processes only.
  - `ProcessExecutionService` gets a tree-kill helper.
  - `WorkspaceExchangeService` changes for export and import.
- **Frontend:**
  - `workspace-detail.html` gets the RUN button, the modal, the configuration section and the tab states.
  - A new `run-processes.js`.
- **WebSocket:** a new `/topic/run/{workspaceId}` topic for RUN log lines and state changes.
- **Database:** new tables are created through JPA DDL. No existing data is migrated.
