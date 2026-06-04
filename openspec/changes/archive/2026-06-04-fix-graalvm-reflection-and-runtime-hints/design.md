## Context

When compiling the Spring Boot 3.2.5 application to a GraalVM native binary using `native-maven-plugin`, the compiler performs static analysis to find all reachable types and compiles them directly to machine code. Types that are loaded dynamically (e.g., via reflection, serialization, or SpEL expressions in Thymeleaf templates) must have explicit runtime hints. Without these hints, they are stripped from the native executable or inaccessible reflexively, causing runtime exceptions (e.g. `SpelEvaluationException: Method call: Method isEmpty(java.util.ArrayList) cannot be found on type org.thymeleaf.expression.Lists`).

Currently, `NativeRuntimeHints.java` exists but lacks definitions for several key JPA entities, DTOs (like `ActionSummary` and `KeyPropertyInfo`), Thymeleaf expression helpers, and Maven Model objects.

## Goals / Non-Goals

**Goals:**
- Provide full reflection registration for all missing database entities and DTOs to prevent runtime mapping or serialization errors.
- Register Thymeleaf expression utility helper classes (`org.thymeleaf.expression.Lists` and `org.thymeleaf.expression.Strings`) for reflection.
- Register Maven Model classes (`Model`, `Parent`, `Dependency`) to prevent Plexus/Maven parser reflection errors.
- Ensure the native executable runs successfully on startup and correctly serves requests for `/` and `/settings` pages.

**Non-Goals:**
- Rewriting Thymeleaf templates or replacing SpEL expressions.
- Modifying business logic, build scripts, or workflows.
- Fixing general performance bugs unrelated to native execution.

## Decisions

### Decision 1: Register Dynamic Types in NativeRuntimeHints.java
Register the missing types in the existing `NativeRuntimeHints.java` class implementing `RuntimeHintsRegistrar`.
* **Rationale**: Spring Boot 3's programmatic `RuntimeHintsRegistrar` is the standard and most robust way to register runtime hints for native images. It is executed during ahead-of-time (AOT) compilation.
* **Alternatives Considered**:
  - *Annotation-based reflection (`@RegisterReflectionForBinding`)*: While simpler, it is limited in configuring member categories (e.g., declared constructors vs public methods). Programmatic registration provides precise control.
  - *JSON files (`reflect-config.json`)*: Works well for third-party libraries but is more error-prone to maintain than Java code within the source repository.

### Decision 2: Upgrade BuildProfile and FavoriteM2Folder to Full Reflection
* **Rationale**: JPA entities require constructor instantiations and field/method mappings. Restricting them to `INVOKE_PUBLIC_METHODS` is unsafe for Hibernate's proxying mechanism. Upgrading them to full reflection (constructors, fields, declared methods) aligns them with `Workspace` and `MavenProject`.

## Risks / Trade-offs

- **Risk**: Increased binary size or analysis time due to registering more classes for reflection.
- **Trade-off/Mitigation**: The additional classes are small DTOs and utilities. The binary size increase is negligible (<1 MB), while the mitigation prevents critical runtime crashes.
