package net.olaba.mvnbuilder.controller;

import net.olaba.mvnbuilder.entities.MavenProject;
import net.olaba.mvnbuilder.repository.JavaInstallationRepository;
import net.olaba.mvnbuilder.repository.MavenProjectRepository;
import net.olaba.mvnbuilder.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Contract checks for bulk-action dispatch of the Git stash command. */
class WorkspaceBulkActionStashTest {

    @Mock private WorkspaceService workspaceService;
    @Mock private BuildService buildService;
    @Mock private TopologicalSortService topologicalSortService;
    @Mock private MavenProjectRepository mavenProjectRepository;
    @Mock private GitService gitService;
    @Mock private MavenRepositoryService mavenRepositoryService;
    @Mock private FileSystemService fileSystemService;
    @Mock private MavenService mavenService;
    @Mock private SystemSettingService systemSettingService;
    @Mock private JavaInstallationRepository javaInstallationRepository;
    @Mock private UpdateService updateService;
    @Mock private DesktopLauncherService desktopLauncherService;
    @InjectMocks private WorkspaceController controller;

    /** Initializes the controller with isolated collaborators. */
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    /** The stash action forwards the untracked-files choice for both values. */
    @Test
    void stashActionDispatchesWithUntrackedFlag() {
        final MavenProject project = MavenProject.builder().withId(2L).withArtifactId("stashed")
                .withWorkspace(net.olaba.mvnbuilder.entities.Workspace.builder().withId(1L).build()).build();
        when(mavenProjectRepository.findAllById(List.of(2L))).thenReturn(List.of(project));

        controller.bulkAction(1L, List.of(2L), "stash", true, null);
        verify(buildService).bulkGitStash(List.of(project), true);

        controller.bulkAction(1L, List.of(2L), "stash", false, null);
        verify(buildService).bulkGitStash(List.of(project), false);
    }

    /** An unrecognized action does not start any build or Git operation. */
    @Test
    void unknownActionDispatchesNothing() {
        when(mavenProjectRepository.findAllById(anyList())).thenReturn(List.of());

        controller.bulkAction(1L, List.of(2L), "not-an-action", false, null);

        verifyNoInteractions(buildService);
    }
}
