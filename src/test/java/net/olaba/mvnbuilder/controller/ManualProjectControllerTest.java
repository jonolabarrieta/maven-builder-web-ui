package net.olaba.mvnbuilder.controller;

import net.olaba.mvnbuilder.entities.*;
import net.olaba.mvnbuilder.repository.*;
import net.olaba.mvnbuilder.service.*;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManualProjectControllerTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    @Mock WorkspaceService workspaceService;
    @Mock BuildService buildService;
    @Mock TopologicalSortService topologicalSortService;
    @Mock MavenProjectRepository mavenProjectRepository;
    @Mock GitService gitService;
    @Mock MavenRepositoryService mavenRepositoryService;
    @Mock FileSystemService fileSystemService;
    @Mock MavenService mavenService;
    @Mock SystemSettingService systemSettingService;
    @Mock JavaInstallationRepository javaInstallationRepository;
    @Mock UpdateService updateService;
    @Mock DesktopLauncherService desktopLauncherService;
    @InjectMocks WorkspaceController controller;
    AutoCloseable mocks;
    @BeforeEach void setup(){mocks=MockitoAnnotations.openMocks(this);}
    @AfterEach void close()throws Exception{mocks.close();}
    @Test void manualOptInAndErrorsAreForwarded() {
        assertEquals(200,controller.addProjects(1L,List.of("/tmp/web"),true).getStatusCode().value());
        verify(workspaceService).addProjectByPath(1L,"/tmp/web",true);
        doThrow(new IllegalArgumentException("Enable non-Maven folders")).when(workspaceService).addProjectByPath(1L,"/tmp/web",false);
        assertEquals(400,controller.addProjects(1L,List.of("/tmp/web"),false).getStatusCode().value());
    }
    @Test void folderCannotInvokeMavenOrM2Operations() {
        MavenProject folder=MavenProject.builder().withId(1L).withProjectKind(ProjectKind.FOLDER).build();
        when(mavenProjectRepository.findById(1L)).thenReturn(Optional.of(folder));
        assertThrows(ResponseStatusException.class,()->controller.buildProject(1L,null));
        assertThrows(ResponseStatusException.class,()->controller.getProjectProperties(1L,new ExtendedModelMap()));
        assertThrows(ResponseStatusException.class,()->controller.getM2Info(1L,new ExtendedModelMap()));
        assertThrows(ResponseStatusException.class,()->controller.deleteM2Version(1L,"1",new ExtendedModelMap()));
        assertThrows(ResponseStatusException.class,()->controller.deleteM2Project(1L,new ExtendedModelMap()));
        verifyNoInteractions(buildService,mavenService,mavenRepositoryService);
    }
    @Test void bulkCannotBuildAnotherWorkspacesProjects() {
        var project=MavenProject.builder().withId(2L).withWorkspace(Workspace.builder().withId(2L).build()).build();
        when(mavenProjectRepository.findAllById(List.of(2L))).thenReturn(List.of(project));
        assertThrows(ResponseStatusException.class,()->controller.bulkAction(1L,List.of(2L),"build",false,null)); verifyNoInteractions(buildService);
    }
    @Test void continuationEndpointUsesServerQueueAndReturnsOutcome() {
        when(buildService.continueBuild(1L,"token","skip")).thenReturn(new BuildService.ContinuationResult("empty","No remaining projects"));
        var response=controller.continueBuild(1L,"token","skip");
        assertEquals(202,response.getStatusCode().value()); assertEquals("empty",response.getBody().state());
    }
    @Test void folderShortcutsUseItsDirectoryAndUnavailablePathsAreRejected() throws Exception {
        var folder=MavenProject.builder().withId(1L).withProjectKind(ProjectKind.FOLDER).withAbsolutePath(directory.toString()).build();
        when(mavenProjectRepository.findById(1L)).thenReturn(Optional.of(folder));
        assertEquals(200,controller.openTerminal(1L).getStatusCode().value());
        verify(desktopLauncherService).openTerminal(directory.toFile());
        assertEquals(200,controller.openVsCode(1L).getStatusCode().value()); assertEquals(200,controller.openExplorer(1L).getStatusCode().value());
        folder.setAbsolutePath(directory.resolve("missing").toString());
        assertEquals(400,controller.openTerminal(1L).getStatusCode().value());
    }
}
