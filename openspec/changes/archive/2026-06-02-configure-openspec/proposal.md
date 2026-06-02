## Why

The OpenSpec folder in this repository lacks project-specific context and artifact rules in its global configuration file (`config.yaml`). Populating this configuration enables all future OpenSpec changes and artifact generation steps to run with precise context about the Spring Boot, Java 21, Thymeleaf, TailwindCSS, and HTMX tech stack, resulting in higher quality design and tasks.

## What Changes

- Update `openspec/config.yaml` with comprehensive project context including tech stack, directory layout, UI guidelines, and coding standards.
- Add per-artifact constraints and rules for proposals, designs, and tasks.

## Capabilities

### New Capabilities
- `openspec-project-config`: Define project-wide configuration rules and context for OpenSpec agent execution.

### Modified Capabilities
<!-- None -->

## Impact

- Modifies `openspec/config.yaml` to include context and rules.
