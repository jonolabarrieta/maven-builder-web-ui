package net.olaba.mvnbuilder.controller;

import net.olaba.mvnbuilder.entities.Workspace;
import net.olaba.mvnbuilder.service.WorkspaceService;
import net.olaba.mvnbuilder.service.git.*;
import static net.olaba.mvnbuilder.service.git.GitModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.file.*;
import java.util.*;

class WorkspaceGitControllerTest {
    @TempDir Path base;
    GitCommandRunner git; GitRepositoryActionService actions; Workspace workspace; WorkspaceGitDiscoveryService discovery;
    MockMvc mvc; String prefix; String repository;
    @BeforeEach void setup() throws Exception {
        git = new GitCommandRunner(); discovery = new WorkspaceGitDiscoveryService(git); var changes = new GitChangeService(git);
        actions = new GitRepositoryActionService(discovery, changes, git);
        workspace = new Workspace(); workspace.setId(1L); workspace.setName("Fixture"); workspace.setBasePath(base.toString());
        git.text(base, "init", "-b", "main"); Files.writeString(base.resolve("new.txt"), "content\n");
        repository = discovery.discover(workspace).repositories().getFirst().id();
        WorkspaceService workspaces = mock(WorkspaceService.class); when(workspaces.getWorkspace(1L)).thenReturn(Optional.of(workspace)); when(workspaces.getWorkspace(2L)).thenReturn(Optional.empty());
        mvc = MockMvcBuilders.standaloneSetup(new WorkspaceGitController(workspaces, discovery, changes, actions)).build(); prefix = "/workspaces/1/git";
    }
    @AfterEach void close() { actions.close(); git.close(); }
    @Test void pageAndReadEndpointsDoNotStageFiles() throws Exception {
        mvc.perform(get(prefix)).andExpect(status().isOk()).andExpect(view().name("workspace-git"));
        mvc.perform(get(prefix + "/repositories/" + repository + "/status")).andExpect(status().isOk()).andExpect(jsonPath("$.unborn").value(true));
        mvc.perform(get(prefix + "/repositories/" + repository + "/diff").param("path", "new.txt").param("area", "changes"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.partial").value(true));
        assertTrue(git.text(base, "ls-files").isEmpty());
    }
    @Test void validatesMembershipPathsAndStaleRequests() throws Exception {
        mvc.perform(get(prefix + "/repositories/unknown/status")).andExpect(status().isNotFound());
        mvc.perform(get(prefix + "/repositories/" + repository + "/diff").param("path", ".git/config").param("area", "changes")).andExpect(status().isForbidden());
        mvc.perform(get(prefix + "/repositories/" + repository + "/diff").param("path", "../outside").param("area", "changes")).andExpect(status().isForbidden());
        mvc.perform(post(prefix + "/repositories/" + repository + "/stage").contentType("application/json").content("{\"path\":\"new.txt\",\"area\":\"changes\",\"version\":\"stale\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").isNotEmpty());
        mvc.perform(get("/workspaces/2/git/repositories/" + repository + "/status")).andExpect(status().isNotFound());
        mvc.perform(post(prefix + "/actions").contentType("application/json").content("{\"action\":\"execute\",\"targets\":[]}")) .andExpect(status().isBadRequest());
        assertTrue(git.text(base, "ls-files").isEmpty());
    }
    @Test void selectedCommitEndpointValidatesScopeBeforeStartingOperations() throws Exception {
        mvc.perform(post(prefix + "/commits").contentType("application/json").content("{\"message\":\"Files\",\"targets\":[]}"))
                .andExpect(status().isBadRequest());
        String snapshot = new GitChangeService(git).status(discovery.repository(workspace, repository)).version();
        String target = "{\"id\":\"" + repository + "\",\"version\":\"" + snapshot + "\",\"files\":[{\"path\":\"../outside\",\"area\":\"changes\"}]}";
        mvc.perform(post(prefix + "/commits").contentType("application/json").content("{\"message\":\"Files\",\"targets\":[" + target + "]}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(prefix + "/commits").contentType("application/json").content("{\"message\":\"Files\",\"targets\":[{\"id\":\"other-workspace\",\"files\":[]}]}"))
                .andExpect(status().isNotFound());
        assertTrue(git.text(base, "ls-files").isEmpty());
    }
    @Test void operationsAreWorkspaceOwnedAndUnknownOperationsAreRejected() throws Exception {
        var operation = actions.discover(workspace);
        mvc.perform(get(prefix + "/operations/" + operation.id())).andExpect(status().isOk());
        Workspace another = new Workspace(); another.setId(3L); another.setBasePath(base.toString());
        assertThrows(GitProblem.class, () -> actions.operation(another, operation.id()));
        mvc.perform(get(prefix + "/operations/unknown")).andExpect(status().isNotFound());
    }
}
