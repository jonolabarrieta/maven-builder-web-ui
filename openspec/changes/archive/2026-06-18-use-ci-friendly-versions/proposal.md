## Why

The project version is currently hardcoded in `pom.xml`. To support clean and automated releases, we want to adopt Maven "CI Friendly Versions" using the `${revision}` property. This allows developers to use a default snapshot version locally while the CI/CD release workflow dynamically overrides it with the exact git tag during a release build.

## What Changes

- **Modify `pom.xml`** to use `<version>${revision}</version>` instead of a hardcoded version.
- **Define property `<revision>`** in `pom.xml` (defaulting to `1.3.0-SNAPSHOT` or similar) for local development compatibility.
- **Update `.github/workflows/release.yml`** to pass the version from the tag trigger using `-Drevision=<version>` when building the JAR and native images.

## Capabilities

### New Capabilities
<!-- None -->

### Modified Capabilities
- `release-pipeline-enhancements`: The release workflow now dynamically injects the build version tag via Maven's revision property instead of relying on a hardcoded version in the source repository.

## Impact

- Modifies [pom.xml](file:///mnt/datos/code/documentos/proyectos/mvnBuilder/pom.xml) version definition.
- Modifies [.github/workflows/release.yml](file:///mnt/datos/code/documentos/proyectos/mvnBuilder/.github/workflows/release.yml) compilation commands.
