# MvnBuilder v2.4.0

Date: 2026-10-28  
Changes since release `v2.3.0`.

This release lets each workspace start the services and tools its projects need, such as `mvn spring-boot:run`, `npm run dev` or `docker compose up`, and follow their output in the Build Output console. RUN processes are kept apart from builds: stopping a build no longer affects them, and building a project that is running asks first.

## RUN configuration

- Open **More actions → RUN configuration** to manage a workspace's RUN entries. Each entry has:
  - a **name**
  - an optional **project**, which can be a Maven, folder or imported project
  - a **working directory**, which defaults to the project's directory
  - a **command**
  - optional **environment variables**
  - an option to use the workspace's Java installation (**JAVA_HOME**)
- A project can have several entries, for example `npm run dev` and `npm run mock`. An entry without a project runs in any directory you choose.
- Commands run through the system shell (`sh -c`, or `cmd /c` on Windows), so pipes, `&&` and `$VARIABLES` work as they do in a terminal.
- Entries can be added, edited, duplicated, deleted and reordered by dragging.
- If a linked project is removed from the workspace, the entry is kept, marked **Unlinked**, and still runs in its working directory.
- RUN entries belong to each workspace and are not inherited from parent workspaces.

## Starting entries

- The new **Run** button next to **Build All** opens a dialog where you choose which entries to start now.
- The dialog preselects the entries you started last time. Entries that are already running are shown as running and cannot be selected again.
- The selected entries start at the same time. An entry whose working directory is missing does not start, and the dialog tells you which one failed.

## Output in the Build Output console

- Each started entry gets its own tab, marked with ▶ and its state:
  - **running**, with **Stop** and **Restart**
  - **stopped**, with **Restart** and close
  - **crashed (exit N)**, with **Restart** and close, keeping the output so you can see why it failed
- A tab cannot be closed while its process is running, either with the close button, a middle click or **Clear All Logs**.
- After a page reload, the tabs of running and recently stopped entries come back with their latest output. The server keeps the last 2,000 lines of each entry.
- Restarting adds a separator so the new output is easy to tell apart from the old.
- RUN output never moves the **Follow build** tab and is included in **Download all build logs**.
- Stop and Restart end the whole process tree. For example, stopping `mvn spring-boot:run` also stops the application it started, so its port is released.

## Building a project that is running

- When a build includes a project with running entries, a dialog lists them before anything starts. This applies to Build All, a single project or module, a range, selected projects and groups.
  - **Build** with **Stop them now and restart them after a successful build** checked stops those entries, builds, and starts them again when the build succeeds.
  - **Build** with the option unchecked builds and leaves them running.
  - **Cancel** starts nothing.
- If the build fails or is stopped, the entries stay stopped. Their tabs, the build log and a notification say which ones were not restarted.

## Builds and application shutdown

- **Fix:** **Kill** now stops a build's whole process tree on Linux and macOS. Before, child processes such as a forked JVM could keep running.
- **Kill** and the build PID indicator only concern builds. Running services are not stopped and do not make a build look active.
- Closing mvnBuilder stops every RUN process and its child processes. Deleting a workspace stops its RUN entries.

## Export and import

- **Export workspace** includes the RUN entries with their commands, directories, environment variables and Java option. A workspace with RUN entries is always exported in the versioned format, even without groups.
- On import, entries are linked again to the imported projects. An entry whose project or directory is missing is still imported and produces a warning.
- Environment variables are stored and exported in plain text. Avoid putting secrets in them if you share export files.
- **Export project paths** is unchanged and does not include RUN entries.

## Upgrading

The new RUN tables are created automatically on first start. No existing data changes.

- New endpoints are under `/workspaces/{id}/run/…`. RUN output and state are published on the WebSocket topic `/topic/run/{workspaceId}`.
- Build endpoints accept an optional `runGuard` parameter (`STOP_RESTART` or `IGNORE`). Without it, a build that includes running entries answers `409 Conflict` with the entries to decide about.
- Files exported by v2.3.0 import unchanged.

[Compare v2.3.0 with v2.4.0](https://github.com/jonolabarrieta/maven-builder-web-ui/compare/v2.3.0...v2.4.0)
