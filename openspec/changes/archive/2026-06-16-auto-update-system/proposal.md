## Why

Currently, MvnBuilder does not provide a mechanism for users to know if they are running the latest version, nor a way to update the application to new releases easily. Users running either the packaged JAR or the GraalVM native binary must manually check the website or repository and download new versions, which hinders adoption and user experience. 

Adding a manual update checker and an in-app self-update system will allow users to keep their installations up-to-date with a single click, directly from the UI.

## What Changes

- **Update Check UI**: A new "Update Center" section in the configuration page (`settings.html`) that shows the current version and features a manual "Check for Updates" button.
- **Update Notification**: Visual notification (banners/badges) in the UI when a newer version is detected after checking.
- **Easily Mockable Endpoint**: The update URL will be configurable via application properties, making it simple to mock or redirect to a local server for testing.
- **Self-Update Implementation**: Support for downloading, replacing, and restarting the application in both JAR and GraalVM native binary modes on Windows, Linux, and macOS.
- **Testing Documentation**: A developer guide (`TESTING_UPDATES.md`) detailing how to mock the update server and run local update tests without uploading files to GitHub.

## Capabilities

### New Capabilities
- `updater-system`: Manages checking for updates against a configurable URL, downloading new versions, generating local update scripts, executing the update process, and updating the UI state.

### Modified Capabilities
*(None)*

## Impact

- **UI**: Modifies `/settings` and `settings.html` to add the Update Center section.
- **Configuration**: Introduces new application properties (e.g., `app.version`, `update.check-url`).
- **Filesystem & OS**: Access to temporary directory to download new files and write/execute temporary scripts (`update.bat` or `update.sh`). Requires starting child processes.
