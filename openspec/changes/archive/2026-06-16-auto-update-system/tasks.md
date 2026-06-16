## 1. Setup and Configuration

- [x] 1.1 Add `app.version=1.1.0` and `app.update.check-url=https://api.github.com/repos/jonolabarrieta/maven-builder-web-ui/releases/latest` to `src/main/resources/application.properties`
- [x] 1.2 Create developer documentation file `TESTING_UPDATES.md` in the project root explaining how to run a local mock update server and trigger test updates


## 2. Models and DTOs

- [x] 2.1 Create `UpdateInfo` and `AssetInfo` DTO classes in `net.olaba.mvnbuilder.model` to map the update API response (both for GitHub and local mock JSON)


## 3. Backend Service Implementation

- [x] 3.1 Create `UpdateService` class in `net.olaba.mvnbuilder.service` to handle update checking, comparison of SemVer versions, and asset downloading
- [x] 3.2 Implement dynamic detection of running environment (JAR vs native binary) in `UpdateService`
- [x] 3.3 Implement `executeUpdaterScript` method in `UpdateService` to write the platform-specific updater script (`update.bat` / `update.sh`) and execute it detached before calling `System.exit(0)`

## 4. Controller Endpoints

- [x] 4.1 Update `SystemSettingsController` to inject `UpdateService` and add current version and update status attributes to the model in `getSettingsPage`
- [x] 4.2 Add `@PostMapping("/check")` to handle manual update checks, query `UpdateService`, and render an updated update center fragment
- [x] 4.3 Add `@PostMapping("/apply")` to download the asset, spawn the updater script, and trigger application shutdown/restart

## 5. UI Implementation

- [x] 5.1 Modify `src/main/resources/templates/settings.html` to append an "Update Center" card in the layout
- [x] 5.2 Implement HTMX attributes on the manual check button to trigger the check endpoint and load the result dynamically
- [x] 5.3 Implement the HTMX indicator and spinner in `settings.html` to display progress during download and install phase
- [x] 5.4 Expose `currentVersion` in WorkspaceController and display it next to the logo in index.html, settings.html and workspace-detail.html


