## MODIFIED Requirements

### Requirement: Automated Background Service Registration
The application MUST support command-line arguments to choose between running as a background service or running in the foreground attached to the terminal.

#### Scenario: Running in service mode by default
- **WHEN** the application is started from a packaged JAR without the `--no-service` argument
- **THEN** it registers the system startup configurations, launches the background service/daemon instance, and the foreground process immediately terminates to free the terminal

#### Scenario: Running in normal mode with --no-service argument
- **WHEN** the application is started from a packaged JAR with the `--no-service` argument
- **THEN** it runs in the foreground attached to the terminal, and no service/autostart configuration is generated or registered
