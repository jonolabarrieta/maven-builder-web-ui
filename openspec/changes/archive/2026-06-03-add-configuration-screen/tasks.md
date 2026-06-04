## 1. Database & Entities

- [x] 1.1 Create JavaInstallation entity with name, javaHome, and isDefault fields
- [x] 1.2 Create SystemSetting entity with favoritePath field
- [x] 1.3 Update Workspace entity to add an optional relationship to JavaInstallation
- [x] 1.4 Create JavaInstallationRepository and SystemSettingRepository Jpa repositories

## 2. Service & Process Injection

- [x] 2.1 Create SystemSettingService to manage configuration properties and provide default settings
- [x] 2.2 Overload executeCommand in ProcessExecutionService to support passing an optional javaHome parameter
- [x] 2.3 Update BuildService to retrieve the active Java version for a project's workspace, falling back to the default active global Java installation, and pass it to ProcessExecutionService

## 3. Web Controllers

- [x] 3.1 Create SystemSettingsController mapping endpoints under /settings for retrieving the page, updating the favorite path, and managing Java versions
- [x] 3.2 Update WorkspaceController to load and populate the favorite path in index model
- [x] 3.3 Update WorkspaceController edit and create endpoints to handle selecting a JavaVersion association
- [x] 3.4 Implement a global /explorer endpoint in WorkspaceController that handles directory listings, defaulting to the configured favorite path

## 4. UI Views & Integration

- [x] 4.1 Create settings.html template displaying favorite path form and a table to manage Java installations
- [x] 4.2 Add configuration navigation link in index.html and workspace-detail.html header nav bars
- [x] 4.3 Update the workspace edit modal in workspace-detail.html to include a Java Version dropdown selector
- [x] 4.4 Update workspace creation base path input in index.html to default to the configured favorite path
- [x] 4.5 Add a file explorer modal to index.html and a "Browse" button in the "New Workspace" modal linked to the global /explorer endpoint

## 5. Verification

- [x] 5.1 Run the Spring Boot application and manually verify that settings are saved properly
- [x] 5.2 Verify that a Maven build is spawned with the correct JAVA_HOME environment variable and outputs to the console log tab
