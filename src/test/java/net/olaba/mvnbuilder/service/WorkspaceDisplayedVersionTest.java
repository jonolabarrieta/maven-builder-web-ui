package net.olaba.mvnbuilder.service;

import net.olaba.mvnbuilder.entities.MavenProject;
import net.olaba.mvnbuilder.repository.BuildProfileRepository;
import net.olaba.mvnbuilder.repository.MavenProjectRepository;
import net.olaba.mvnbuilder.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceDisplayedVersionTest {

    @TempDir
    Path directory;

    @Mock WorkspaceRepository workspaceRepository;
    @Mock MavenProjectRepository projectRepository;
    @Mock MavenService mavenService;
    @Mock GitService gitService;
    @Mock BuildProfileRepository buildProfileRepository;
    @InjectMocks WorkspaceService workspaceService;

    @Test
    void switchingProfilesUpdatesOnlyReadableProjectVersions() throws Exception {
        final Path present = Files.createDirectory(directory.resolve("present"));
        final File pom = Files.createFile(present.resolve("pom.xml")).toFile();
        final MavenProject changed = MavenProject.builder().withId(1L).withAbsolutePath(present.toString())
                .withVersion("BASE-VERSION").build();
        final MavenProject missing = MavenProject.builder().withId(2L)
                .withAbsolutePath(directory.resolve("missing").toString()).withVersion("LAST-KNOWN").build();
        when(projectRepository.findAll()).thenReturn(List.of(changed, missing));
        when(mavenService.resolveVersion(eq(pom), eq("batsdlc"))).thenReturn("0.3.20-SNAPSHOT");

        workspaceService.refreshDisplayedVersions("batsdlc");

        assertEquals("0.3.20-SNAPSHOT", changed.getVersion());
        assertEquals("LAST-KNOWN", missing.getVersion());
        verify(projectRepository).saveAll(List.of(changed));
    }
}
