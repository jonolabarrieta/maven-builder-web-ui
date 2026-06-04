## Why

When compiling the application to a GraalVM native image, the Thymeleaf template engine fails at runtime during SpEL evaluation of `#lists.isEmpty(...)` and `#strings.escapeJava(...)`. This occurs because Thymeleaf expression utility classes and several entity/DTO classes lack the necessary runtime reflection metadata in the native binary. This change registers these classes for reflection, ensuring the application compiles successfully and runs without runtime SpEL or serialization failures under GraalVM.

## What Changes

- Register Thymeleaf expression utility helper classes (`org.thymeleaf.expression.Lists` and `org.thymeleaf.expression.Strings`) for reflection.
- Register all JPA entities (`net.olaba.mvnbuilder.entities.SystemSetting` and `net.olaba.mvnbuilder.entities.JavaInstallation`) for full reflection access.
- Upgrade existing JPA entities (`BuildProfile` and `FavoriteM2Folder`) to full reflection (constructors, fields, declared methods) for reliable database mapping.
- Register DTOs and internal classes used in controllers and templates (`net.olaba.mvnbuilder.model.ActionSummary`, `net.olaba.mvnbuilder.controller.WorkspaceController.KeyPropertyInfo`, and `net.olaba.mvnbuilder.service.FileSystemService.FileItem`) for reflection.
- Register Maven Model classes (`org.apache.maven.model.Model`, `org.apache.maven.model.Parent`, and `org.apache.maven.model.Dependency`) for reflection to prevent deserialization issues when parsing `pom.xml` files.

## Capabilities

### New Capabilities
- `graalvm-compatibility`: The system must be fully compatible with GraalVM Native Image compilation and execution.

### Modified Capabilities
None.

## Impact

- **Affected Code**: `net.olaba.mvnbuilder.config.NativeRuntimeHints` will be modified to include runtime hints for reflection.
- **Dependencies**: No new dependencies are introduced.
- **APIs**: Restores correct functionality of existing endpoints (like home `/` and workspace detail `/workspaces/{id}`) when served from the native executable.
