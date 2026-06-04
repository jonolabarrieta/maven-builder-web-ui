## 1. Modify release.yml Build Matrix

- [x] 1.1 Update build matrix in `.github/workflows/release.yml` to use `include` with explicit runner and suffix properties for Linux, Windows, macOS x86_64 (Intel), and macOS ARM64 (Apple Silicon).

## 2. Update Packaging and Versioning in Workflow

- [x] 2.1 Update the 'Prepare Artifact' step in `.github/workflows/release.yml` to copy native binaries to versioned names: `mvn-builder-${{ github.ref_name }}-<suffix>`.
- [x] 2.2 Update the 'Prepare Artifact' step to copy the Maven JAR file to `mvn-builder-${{ github.ref_name }}.jar` exclusively on the Linux runner.

## 3. Verification

- [x] 3.1 Verify that Docker login, build, and push steps correctly conditionalize on `ubuntu-latest`.
