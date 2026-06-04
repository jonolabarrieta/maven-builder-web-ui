## 1. Implement GraalVM Runtime Hints

- [x] 1.1 Import missing entity, model, and utility classes in NativeRuntimeHints.java
- [x] 1.2 Upgrade BuildProfile and FavoriteM2Folder registration to full reflection (constructors, fields, declared methods)
- [x] 1.3 Register JavaInstallation and SystemSetting entities for full reflection access
- [x] 1.4 Register ActionSummary DTO for full reflection access
- [x] 1.5 Register KeyPropertyInfo internal class for public methods and constructors reflection access
- [x] 1.6 Register org.thymeleaf.expression.Lists and org.thymeleaf.expression.Strings for public methods reflection access
- [x] 1.7 Register org.apache.maven.model.Model, org.apache.maven.model.Parent, and org.apache.maven.model.Dependency for public methods and constructors reflection access

## 2. Verification

- [x] 2.1 Verify local standard compilation via mvn clean test-compile
- [x] 2.2 Verify GraalVM Native Image compilation via mvn -Pnative native:compile -DskipTests
- [x] 2.3 Verify the native binary execution by starting it on a non-conflicting port and checking the home page `/` and settings `/settings` pages for runtime exceptions
