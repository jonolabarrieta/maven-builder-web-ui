## MODIFIED Requirements

### Requirement: Versioned Artifact Naming
The release workflow SHALL build and package the Maven project, the generated binaries, and the JAR file with the build version tag from the trigger event using Maven CI Friendly Versions property overrides.

#### Scenario: Tag push triggers dynamic version compilation
- **WHEN** a git tag starting with `v` (e.g., `v1.2.0`) is pushed to the repository
- **THEN** the GitHub Actions workflow executes Maven builds passing `-Drevision=1.2.0` (extracted from the tag name)
- **AND** the compiled files are named:
  - `mvn-builder-v1.2.0-linux-amd64`
  - `mvn-builder-v1.2.0-windows-amd64.exe`
  - `mvn-builder-v1.2.0-macos-amd64`
  - `mvn-builder-v1.2.0-macos-arm64`
  - `mvn-builder-v1.2.0.jar`
