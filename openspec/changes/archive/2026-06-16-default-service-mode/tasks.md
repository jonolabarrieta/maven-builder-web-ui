## 1. Implementation

- [x] 1.1 Extract a helper method `isPackagedJar()` in `MvnBuilderApplication.java` to detect if the running code is from a packaged JAR.
- [x] 1.2 Modify `main()` in `MvnBuilderApplication.java` to default to `runAsService = isPackagedJar()`.
- [x] 1.3 Add parsing logic in `main()` to check if `--no-service`, `no-service`, or `-no-service` is passed, and if so set `runAsService = false`.
- [x] 1.4 Update `registerWindowsAutostart()` to include `--no-service` in the startup `.bat` file execution command.
- [x] 1.5 Update `registerLinuxAutostart()` to include `--no-service` in the systemd service unit `ExecStart` command.
- [x] 1.6 Update `registerMacAutostart()` to include `--no-service` in the LaunchAgent plist `ProgramArguments` array.

## 2. Verification

- [x] 2.1 Verify compile and run tests using `mvn clean test`.
- [x] 2.2 Create a unit test to verify that the launcher logic correctly sets service mode by default and respects `--no-service`.
- [x] 2.3 Verify the generated startup configuration files output format contains the `--no-service` parameter.
