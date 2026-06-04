## Context

Background: maven-builder-web-ui is a tool to scan, manage, and build multi-module Maven projects. Currently:
1. Submodule nesting is not shown recursively because button visibility in templates checks `project.modules.empty`. However, submodules (which are themselves child modules) may not have `<modules>` parsed or present, even though they contain physical sub-projects stored in the database.
2. Maven project versions defined via properties (like `${revision}`) that are inherited from parent POM files are not resolved, displaying raw property placeholder strings in the UI.

## Goals / Non-Goals

**Goals:**
- Dynamically determine child module presence using the workspace database projects hierarchy via a `hasChildren()` method.
- Correctly render recursive "View Recursive" and "Dive" buttons.
- Recursively resolve custom properties (such as `${revision}`) and standard Maven properties (such as `${project.parent.version}`) from parent/grandparent POM files.

**Non-Goals:**
- Fully implementing a complete Maven parser or engine.
- Altering the topological sorting or build execution order algorithms themselves.

## Decisions

### Decision 1: Database/Path-Driven `hasChildren()` Check
- **Rationale**: Instead of relying on `project.modules` parsed from the local POM, we check if any other project in the workspace database has a relative path that is nested under the current project's path.
- **Alternatives considered**: Storing a bi-directional JPA relationship (`parent` / `children`). This would require complex relational maintenance and database migrations. Computing it dynamically via the loaded workspace projects is fast, simple, and requires no schema changes.

### Decision 2: Recursive Parent/Grandparent Property Resolution
- **Rationale**: Extracted recursive parent POM lookup logic into `getParentPomProperties(File, Parent)`. When `resolveProperty` is called, it recursively retrieves properties from parent and grandparent POM files to resolve custom revision properties. It also implements resolution for standard Maven properties like `${project.parent.version}`, `${parent.version}`, `${project.groupId}`, and `${project.version}`.
- **Alternatives considered**: Running `mvn help:evaluate`. This is too slow and requires a full Maven installation on the host machine. The regex and file-based parser is extremely fast and lightweight.

## Risks / Trade-offs

- **[Risk]**: Lazy initialization exceptions when accessing `project.workspace.projects` inside `hasChildren()`.
  - **Mitigation**: The workspace controller loads workspace detail and accesses projects within a transactional boundary or eager loads them. In Thymeleaf rendering, the hibernate session is active (Open Session in View is default in Spring Boot), so it will execute safely.
