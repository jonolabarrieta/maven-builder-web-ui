## 1. Maven Configuration Updates

- [x] 1.1 Modify [pom.xml](file:///mnt/datos/code/documentos/proyectos/mvnBuilder/pom.xml) to use the `${revision}` placeholder in the `<version>` element.
- [x] 1.2 Add the `<revision>1.3.0-SNAPSHOT</revision>` property within the `<properties>` block of [pom.xml](file:///mnt/datos/code/documentos/proyectos/mvnBuilder/pom.xml) to serve as the default local development version.

## 2. CI/CD Workflow Adjustments

- [x] 2.1 Update the `Build JAR` step in [.github/workflows/release.yml](file:///mnt/datos/code/documentos/proyectos/mvnBuilder/.github/workflows/release.yml) to extract the tag name and run compilation passing the `-Drevision` override.
- [x] 2.2 Update the `Build Native Image` step in [.github/workflows/release.yml](file:///mnt/datos/code/documentos/proyectos/mvnBuilder/.github/workflows/release.yml) to pass the `-Drevision` override.
