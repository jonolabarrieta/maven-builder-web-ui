# release-pipeline-enhancements Specification

## Purpose
TBD - created by archiving change gh-actions-versioning-mac-arm. Update Purpose after archive.
## Requirements
### Requirement: Versioned Artifact Naming
The release workflow SHALL name all generated binaries and the JAR file with the build version tag from the trigger event.

#### Scenario: Tag push triggers versioned artifacts packaging
- **WHEN** a git tag starting with `v` (e.g., `v1.2.0`) is pushed to the repository
- **THEN** the GitHub Actions workflow builds and renames the files to:
  - `mvn-builder-v1.2.0-linux-amd64`
  - `mvn-builder-v1.2.0-windows-amd64.exe`
  - `mvn-builder-v1.2.0-macos-amd64`
  - `mvn-builder-v1.2.0-macos-arm64`
  - `mvn-builder-v1.2.0.jar`

### Requirement: Apple Silicon Support
The release workflow SHALL build a macOS binary optimized for Apple Silicon processors (ARM64 architecture) in addition to Intel processors (x86_64 architecture).

#### Scenario: Apple Silicon Native Compilation
- **WHEN** the GitHub Actions workflow runs
- **THEN** it executes a GraalVM native image build on a macOS ARM64 runner (such as `macos-14`) and produces the `mvn-builder-v1.2.0-macos-arm64` artifact.

