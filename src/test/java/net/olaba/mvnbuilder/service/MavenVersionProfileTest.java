package net.olaba.mvnbuilder.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MavenVersionProfileTest {

    @TempDir
    Path directory;

    private final MavenService mavenService = new MavenService();

    @Test
    void resolvesProfileRevisionFromParentForModules() throws Exception {
        final Path parent = Files.createDirectory(directory.resolve("parent"));
        final Path child = Files.createDirectory(directory.resolve("child"));
        final Path parentPom = parent.resolve("pom.xml");
        final Path childPom = child.resolve("pom.xml");
        Files.writeString(parentPom, """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>parent</artifactId>
                  <version>${revision}</version>
                  <properties><revision>BASE-VERSION</revision></properties>
                  <profiles><profile><id>batsdlc</id><properties>
                    <r01f.version>0.3.20-SNAPSHOT</r01f.version>
                    <revision>${r01f.version}</revision>
                  </properties></profile></profiles>
                </project>
                """);
        Files.writeString(childPom, """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent><groupId>example</groupId><artifactId>parent</artifactId>
                    <version>${revision}</version><relativePath>../parent/pom.xml</relativePath>
                  </parent>
                  <artifactId>child</artifactId>
                </project>
                """);

        assertEquals("BASE-VERSION", mavenService.resolveVersion(parentPom.toFile(), null));
        assertEquals("0.3.20-SNAPSHOT", mavenService.resolveVersion(parentPom.toFile(), "batsdlc"));
        assertEquals("BASE-VERSION", mavenService.resolveVersion(childPom.toFile(), null));
        assertEquals("0.3.20-SNAPSHOT", mavenService.resolveVersion(childPom.toFile(), "batsdlc"));
        assertEquals("BASE-VERSION", mavenService.resolveVersion(childPom.toFile(), "unknown"));
        assertEquals("0.3.20-SNAPSHOT", mavenService.parsePom(childPom.toFile(), directory.toString(), "batsdlc").getVersion());
    }

    @Test
    void resolvesParentVersionExpressionAndFallsBackOnCycle() throws Exception {
        final Path parent = Files.createDirectory(directory.resolve("parent"));
        final Path child = Files.createDirectory(directory.resolve("child"));
        Files.writeString(parent.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>parent</artifactId><version>${revision}</version>
                  <properties><revision>1.0</revision></properties>
                  <profiles><profile><id>broken</id><properties>
                    <revision>${a}</revision><a>${revision}</a>
                  </properties></profile></profiles>
                </project>
                """);
        final Path childPom = child.resolve("pom.xml");
        Files.writeString(childPom, """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent><groupId>example</groupId><artifactId>parent</artifactId>
                    <version>1.0</version><relativePath>../parent/pom.xml</relativePath>
                  </parent>
                  <artifactId>child</artifactId><version>${project.parent.version}</version>
                </project>
                """);

        assertEquals("1.0", mavenService.resolveVersion(childPom.toFile(), "broken"));
        assertEquals("1.0", mavenService.resolveVersion(parent.resolve("pom.xml").toFile(), "broken"));
    }
}
