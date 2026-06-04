# graalvm-compatibility Specification

## Purpose
TBD - created by archiving change fix-graalvm-reflection-and-runtime-hints. Update Purpose after archive.
## Requirements
### Requirement: GraalVM Native Compatibility
The application MUST support compiling to a GraalVM native binary and running successfully without throwing reflection, serialization, or template engine SpEL exceptions.

#### Scenario: Successful compilation and execution
- **WHEN** the application is compiled with `mvn -Pnative native:compile` and the resulting binary is started
- **THEN** the server starts successfully and endpoints like `/` and `/settings` are rendered without `SpelEvaluationException` or other reflection-related exceptions

