## Context

Currently, the `.github/workflows/release.yml` builds releases for three platforms: Linux (ubuntu-latest), Windows (windows-latest), and macOS (macos-latest). However:
1. The binaries and JAR are packaged with generic static filenames (e.g. `mvn-builder.jar`, `mvn-builder-windows-amd64.exe`).
2. There is no Apple Silicon-native (macOS ARM64) build configuration. Under GitHub Actions, `macos-latest` currently runs on Apple Silicon (`macos-14`), but the build script packages it as `mvn-builder-macos-amd64` which is incorrect. Furthermore, there is no separate build for Intel-based Macs.

To ensure compatibility and clear versioning, we need to restructure the workflow matrix and name the artifacts properly.

## Goals / Non-Goals

**Goals:**
- Include the release tag version (e.g. `v1.2.0`) in the filename of all native binaries and the JAR file (e.g. `mvn-builder-v1.2.0-linux-amd64`).
- Add a specific `macos-14` runner target to compile macOS ARM64 binaries.
- Add a specific `macos-13` runner target to compile macOS Intel (x86_64) binaries.

**Non-Goals:**
- Compiling Windows or Linux ARM64 binaries.
- Changing the underlying GraalVM setup or Maven plugins.

## Decisions

### 1. Build Matrix Strategy
Expand the build matrix to specify the exact runner and suffix for each platform configuration:
- `ubuntu-latest` -> `linux-amd64`
- `windows-latest` -> `windows-amd64.exe`
- `macos-13` (Intel) -> `macos-amd64`
- `macos-14` (ARM64) -> `macos-arm64`

*Rationale:* A single matrix in the existing workflow file keeps compilation, artifact collection, and release uploading centralized and clean.

### 2. Version Injecting
Use `${{ github.ref_name }}` which represents the tag name that triggered the build (e.g., `v1.2.0`) to name the files in the artifact prep step.
*Rationale:* The workflow triggers on tag pushes (`v*`), so `github.ref_name` is guaranteed to contain the correct release version.

## Risks / Trade-offs

- **[Risk]** Running four parallel runners instead of three increases GitHub Actions usage.
  - *Mitigation:* This is a lightweight project where releases are infrequent, so the minor extra runner time is negligible.
