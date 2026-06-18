## Context

Currently, the version in [pom.xml](file:///mnt/datos/code/documentos/proyectos/mvnBuilder/pom.xml) is statically defined as `1.3.0`. To publish release binaries with the correct tag name version automatically without manual changes, we want to adopt Maven CI Friendly Versions. This replaces the hardcoded version with `${revision}` and maps it to a property that defaults to a snapshot version for local development, but can be overridden in the GitHub Actions workflow.

## Goals / Non-Goals

**Goals:**
- Dynamically build and package Maven JAR, GraalVM native binary, and Docker images with the version from the Git tag.
- Keep the local development default version as a snapshot (`1.3.0-SNAPSHOT`) to avoid version conflicts.
- Do not modify files on disk during build steps.

**Non-Goals:**
- Automatically committing version increments back to the source code repository.
- Setting up the `flatten-maven-plugin` (not needed since this project is packaged as a final application rather than published as a library to a Maven repository).

## Decisions

### Decision: Overriding the `${revision}` Property in CI/CD
We will change the version element in [pom.xml](file:///mnt/datos/code/documentos/proyectos/mvnBuilder/pom.xml) to `${revision}` and define the property `<revision>1.3.0-SNAPSHOT</revision>` in the properties section. In the GitHub Actions workflow [.github/workflows/release.yml](file:///mnt/datos/code/documentos/proyectos/mvnBuilder/.github/workflows/release.yml), we will append `-Drevision=${VERSION}` to all Maven execution commands (`mvn clean package` and `mvn -Pnative native:compile`).

- **Alternative considered**: Running `mvn versions:set -DnewVersion=<version>`.
  - **Rationale**: While `versions:set` works, it rewrites `pom.xml` on the build agent's disk. Utilizing the `${revision}` property is the modern, native Maven approach that leaves files unmodified on disk, reducing risks and maintaining a cleaner build lifecycle.

## Risks / Trade-offs

- **Risk**: Local developers run `mvn clean package` and version is unresolved.
  - **Mitigation**: We define `<revision>1.3.0-SNAPSHOT</revision>` inside the `<properties>` block of [pom.xml](file:///mnt/datos/code/documentos/proyectos/mvnBuilder/pom.xml), ensuring Maven automatically resolves the version to the snapshot value during local development.
