## ADDED Requirements

### Requirement: OpenSpec Project Context Configuration
The OpenSpec configuration file (`openspec/config.yaml`) SHALL contain the project's tech stack context, directory layout, UI guidelines, and coding standards.

#### Scenario: Read configuration context
- **WHEN** the OpenSpec agent reads the `openspec/config.yaml` file
- **THEN** it finds documentation detailing Java 21, Spring Boot, JPA, H2, Thymeleaf, HTMX, TailwindCSS CDN, and UI/architectural guidelines

### Requirement: OpenSpec Per-Artifact Rules
The OpenSpec configuration file (`openspec/config.yaml`) SHALL define custom validation and generation rules for `proposal`, `design`, and `tasks` artifacts to ensure style consistency.

#### Scenario: Verify artifact rules
- **WHEN** checking rules in `openspec/config.yaml`
- **THEN** it specifies rules enforcing concise proposals, clear technical layouts for designs, and modular task steps
