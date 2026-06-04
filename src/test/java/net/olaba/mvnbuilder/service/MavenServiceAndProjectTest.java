package net.olaba.mvnbuilder.service;

import net.olaba.mvnbuilder.entities.MavenProject;
import net.olaba.mvnbuilder.entities.Workspace;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class MavenServiceAndProjectTest {

    @Test
    void testHasChildren() {
        Workspace ws = Workspace.builder().withId(1L).withName("Test WS").build();
        
        MavenProject parent = MavenProject.builder()
                .withId(10L)
                .withArtifactId("parent-proj")
                .withRelativePath("parent-proj")
                .withWorkspace(ws)
                .build();

        MavenProject child = MavenProject.builder()
                .withId(11L)
                .withArtifactId("child-proj")
                .withRelativePath("parent-proj/child-proj")
                .withWorkspace(ws)
                .build();

        MavenProject other = MavenProject.builder()
                .withId(12L)
                .withArtifactId("other-proj")
                .withRelativePath("other-proj")
                .withWorkspace(ws)
                .build();

        ws.getProjects().addAll(Arrays.asList(parent, child, other));

        assertTrue(parent.hasChildren());
        assertFalse(child.hasChildren());
        assertFalse(other.hasChildren());
    }

    @Test
    void testHasChildrenRoot() {
        Workspace ws = Workspace.builder().withId(1L).withName("Test WS").build();
        
        MavenProject root = MavenProject.builder()
                .withId(10L)
                .withArtifactId("root-proj")
                .withRelativePath(".")
                .withWorkspace(ws)
                .build();

        MavenProject child = MavenProject.builder()
                .withId(11L)
                .withArtifactId("child-proj")
                .withRelativePath("child-proj")
                .withWorkspace(ws)
                .build();

        ws.getProjects().addAll(Arrays.asList(root, child));

        assertTrue(root.hasChildren());
        assertFalse(child.hasChildren());
    }

    @Test
    void testRecursivePropertyResolution() throws Exception {
        Path tempDir = Files.createTempDirectory("maven-service-test");
        Path parentDir = Files.createDirectory(tempDir.resolve("parent"));
        Path childDir = Files.createDirectory(tempDir.resolve("child"));

        File parentPom = parentDir.resolve("pom.xml").toFile();
        try (FileWriter fw = new FileWriter(parentPom)) {
            fw.write("<project>\n" +
                    "  <modelVersion>4.0.0</modelVersion>\n" +
                    "  <groupId>com.example</groupId>\n" +
                    "  <artifactId>parent-project</artifactId>\n" +
                    "  <version>1.2.3</version>\n" +
                    "  <packaging>pom</packaging>\n" +
                    "  <properties>\n" +
                    "    <revision>2.0.0-SNAPSHOT</revision>\n" +
                    "    <custom.prop>hello</custom.prop>\n" +
                    "  </properties>\n" +
                    "</project>");
        }

        File childPom = childDir.resolve("pom.xml").toFile();
        try (FileWriter fw = new FileWriter(childPom)) {
            fw.write("<project>\n" +
                    "  <modelVersion>4.0.0</modelVersion>\n" +
                    "  <parent>\n" +
                    "    <groupId>com.example</groupId>\n" +
                    "    <artifactId>parent-project</artifactId>\n" +
                    "    <version>1.2.3</version>\n" +
                    "    <relativePath>../parent/pom.xml</relativePath>\n" +
                    "  </parent>\n" +
                    "  <artifactId>child-project</artifactId>\n" +
                    "  <version>${revision}</version>\n" +
                    "  <properties>\n" +
                    "    <child.prop>${custom.prop}-world</child.prop>\n" +
                    "  </properties>\n" +
                    "</project>");
        }

        MavenService mavenService = new MavenService();
        MavenProject parsedChild = mavenService.parsePom(childPom, tempDir.toString());

        assertNotNull(parsedChild);
        assertEquals("2.0.0-SNAPSHOT", parsedChild.getVersion());
        assertEquals("com.example", parsedChild.getGroupId());

        // Clean up temp files
        Files.delete(childPom.toPath());
        Files.delete(childDir);
        Files.delete(parentPom.toPath());
        Files.delete(parentDir);
        Files.delete(tempDir);
    }
}
