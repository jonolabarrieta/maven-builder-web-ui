## Context

Currently, the Maven Builder application doesn't provide a way to export or import workspaces. A workspace is tied to the in-memory or local database and must be reconstructed manually on other machines or installations. We need a simple, portable, and plain-text representation to allow users to export their workspace configuration (including project ordering) and import it elsewhere.

## Goals / Non-Goals

**Goals:**
- Provide a plain text workspace export containing the workspace name, base path, excluded paths, and the ordered list of project directories.
- Provide a UI on the homepage to import a workspace from an uploaded text file.
- Perform clean scans and metadata calculation (re-resolve Maven POM details and Git branches) during the import process so that database metadata is not copied or corrupted.
- Preserve the exact ordering of projects during the import.

**Non-Goals:**
- Storing or importing project-specific build history, logs, or other runtime database metadata.
- Automatically creating directories or downloading Git repositories that do not exist on the host filesystem.

## Decisions

### 1. Plain Text Format
We will serialize/deserialize workspaces using a simple key-value header followed by the list of project paths in execution order.

**Format:**
```
Workspace: <Workspace Name>
BasePath: <Base Directory Path>
Exclude: <Excluded Path 1>
Exclude: <Excluded Path 2>

# Projects in execution order (one path per line)
<Project Path 1>
<Project Path 2>
```

**Alternatives Considered:**
- **JSON**: More robust structure but harder for a user to quickly view, edit, or copy-paste in a simple text editor.
- **CSV / Properties**: A custom properties format, but we need an ordered list of paths, which is more naturally represented as plain text lines.

### 2. Recalculation of Project Metadata during Import
To prevent transferring stale Git branch names, dependencies, or versions, the export file will contain ONLY directory paths. During import, the backend will parse the `pom.xml` of each directory path and retrieve the active Git branch to compute the state from scratch.

### 3. File Upload Interface
We will add an "Import Workspace" modal on the homepage with a file upload field. The controller will accept a `MultipartFile` at POST `/workspaces/import`.

## Risks / Trade-offs

- **Missing Paths on the Host System** → If an imported project path does not exist on the target machine, the import process will skip it or log a warning rather than crashing the entire import.
- **Path Portability** → Paths in the export file might be absolute and system-specific. To mitigate this, if the imported path is relative or if the target machine uses a different base directory, we will attempt to resolve the path relative to the newly imported workspace's `BasePath`.
