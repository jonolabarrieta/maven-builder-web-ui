## Context

The `mvnBuilder` project is a Spring Boot web application that manages Maven workspaces and builds. It uses Java 21, Spring Boot Starter Web, Thymeleaf, HTMX, and TailwindCSS CDN for styling. Currently, the `openspec/config.yaml` file lacks any project context or custom artifact rules. As a result, agents do not automatically know the stack, file structure, or design guidelines when proposing or planning new changes.

## Goals / Non-Goals

**Goals:**
- Add detailed project context to `openspec/config.yaml` covering the tech stack, directory structure, UI guidelines, and architecture.
- Add per-artifact rules to `openspec/config.yaml` to enforce standards on OpenSpec proposals, designs, and tasks.

**Non-Goals:**
- Modifying any Java source code or Thymeleaf templates.
- Changing the schema of the OpenSpec workflow.

## Decisions

### 1. Populate the `context` block in `openspec/config.yaml`
Provide a structured, multi-line string in `openspec/config.yaml` under `context` containing:
- **Tech Stack**: Java 21, Spring Boot 3.2.5, Thymeleaf, HTMX (v1.9.10), TailwindCSS (CDN), H2 Database, Lombok.
- **Directory Structure**:
  - `src/main/java/net/olaba/mvnbuilder/` for backend controllers, models, and service classes.
  - `src/main/resources/templates/` for Thymeleaf templates.
  - `src/main/resources/static/` for static assets (js, css, icons).
- **UI Guidelines**: Use a clean, modern design with an Indigo-based theme, modern typography (Inter), glassmorphism classes, and HTMX-powered asynchronous updates to avoid full page reloads.

### 2. Populate the `rules` block in `openspec/config.yaml`
- **proposal**: Keep proposals under 400 words, clearly list all new/modified capabilities.
- **design**: Include decisions and alternatives considered, focus on component boundaries.
- **tasks**: Break tasks into modular steps, ensure each Java controller method or template change is mapped to a specific task.

## Risks / Trade-offs

- **[Risk] Outdated context if dependencies change** &rarr; **[Mitigation]** Keep the context focused on the core architecture and stack which is stable.
