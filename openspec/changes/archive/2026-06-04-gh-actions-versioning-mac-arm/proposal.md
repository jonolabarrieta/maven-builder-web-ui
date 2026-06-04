## Why

Currently, release binaries and JARs produced by the GitHub Actions workflow do not include the build version in their filenames, making it difficult to distinguish between releases. Furthermore, the workflow does not target macOS ARM (Apple Silicon) runners, limiting native image compatibility and performance on modern Mac computers (M1, M2, M3, M4).

## What Changes

- Rename binary and JAR release artifacts to include the build version (tag name) in their filenames.
- Add macOS ARM64 compilation targeting Apple Silicon to the build matrix.
- Retain macOS x86_64 compilation for Intel-based Macs.

## Capabilities

### New Capabilities
- `release-pipeline-enhancements`: Automate versioned release artifact packaging and support Apple Silicon build targets in the CI/CD pipeline.

### Modified Capabilities
