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
    GitBranchService branchService; net.olaba.mvnbuilder.service.DesktopLauncherService launcher = mock(net.olaba.mvnbuilder.service.DesktopLauncherService.class); net.olaba.mvnbuilder.repository.MavenProjectRepository projects;
    GitCommandRunner git; GitRepositoryActionService actions; Workspace workspace; WorkspaceGitDiscoveryService discovery;
    MockMvc mvc; String prefix; String repository;
    @BeforeEach void setup() throws Exception {
        git = new GitCommandRunner(); discovery = new WorkspaceGitDiscoveryService(git); var changes = new GitChangeService(git);
        actions = new GitRepositoryActionService(discovery, changes, git);
        workspace = new Workspace(); workspace.setId(1L); workspace.setName("Fixture"); workspace.setBasePath(base.toString());
        git.text(base, "init", "-b", "main"); Files.writeString(base.resolve("new.txt"), "content\n");
        repository = discovery.discover(workspace).repositories().getFirst().id();
        projects = mock(net.olaba.mvnbuilder.repository.MavenProjectRepository.class);
        branchService = new GitBranchService(new net.olaba.mvnbuilder.service.GitService(), git, projects);
        WorkspaceService workspaces = mock(WorkspaceService.class); when(workspaces.getWorkspace(1L)).thenReturn(Optional.of(workspace)); when(workspaces.getWorkspace(2L)).thenReturn(Optional.empty());
        mvc = MockMvcBuilders.standaloneSetup(new WorkspaceGitController(workspaces, discovery, changes, actions, new GitSyncService(discovery, changes, git), new GitHistoryService(git, changes), branchService, launcher, new GitFileEditService(git, changes))).build(); prefix = "/workspaces/1/git";
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
        mvc.perform(get(prefix + "/repositories/unknown/status")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("unknown-repository"));
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
        String snapshot = new GitChangeService(git).status(discovery.repository(workspace, repository), true).version();
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
    @Test void untrackedFolderExpansionListsFilesAndRejectsTraversal() throws Exception {
        Files.createDirectories(base.resolve("generated/deep")); Files.writeString(base.resolve("generated/deep/a.txt"), "a\n");
        mvc.perform(get(prefix + "/repositories/" + repository + "/status")).andExpect(jsonPath("$.files[?(@.path=='generated/')].kind").value("untracked-dir"));
        mvc.perform(get(prefix + "/repositories/" + repository + "/untracked").param("dir", "generated/"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].path").value("generated/deep/a.txt")).andExpect(jsonPath("$[0].kind").value("untracked"));
        mvc.perform(get(prefix + "/repositories/" + repository + "/untracked").param("dir", "../outside/")).andExpect(status().isForbidden());
        mvc.perform(get(prefix + "/repositories/" + repository + "/untracked").param("dir", "missing/")).andExpect(status().isConflict());
        mvc.perform(get(prefix + "/repositories/" + repository + "/status").param("full", "true")).andExpect(status().isOk()).andExpect(jsonPath("$.version").isNotEmpty());
    }
    @Test void discoveryReusesTheLastScanUnlessRefreshedOrTheBaseChanges() throws Exception {
        var first = actions.discover(workspace, true);
        while (actions.operation(workspace, first.id()).state().equals("running")) Thread.sleep(10);
        git.text(Files.createDirectory(base.resolve("added")), "init", "-b", "main");
        mvc.perform(get(prefix + "/repositories")).andExpect(jsonPath("$.state").value("done")).andExpect(jsonPath("$.discovery.repositories.length()").value(1));
        String refreshed = new com.fasterxml.jackson.databind.ObjectMapper().readTree(mvc.perform(get(prefix + "/repositories").param("refresh", "true")).andReturn().getResponse().getContentAsString()).get("id").asText();
        while (actions.operation(workspace, refreshed).state().equals("running")) Thread.sleep(10);
        assertEquals(2, actions.operation(workspace, refreshed).discovery().repositories().size());
        workspace.setBasePath(base.resolve("added").toString());
        mvc.perform(get(prefix + "/repositories")).andExpect(jsonPath("$.outcomes[0].message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.startsWith("Showing the last"))));
    }
    @Test void operationTransitionsArePublishedToTheWorkspaceTopic() throws Exception {
        var template = mock(org.springframework.messaging.simp.SimpMessagingTemplate.class); actions.setMessaging(template);
        var operation = actions.discover(workspace, true);
        while (actions.operation(workspace, operation.id()).state().equals("running")) Thread.sleep(10);
        var captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(template, atLeast(3)).convertAndSend(eq("/topic/workspace-git/1"), captor.capture());
        var published = captor.getAllValues().stream().map(Operation.class::cast).filter(op -> op.id().equals(operation.id())).toList();
        assertEquals(List.of("running", "running", "done"), published.stream().map(Operation::state).toList());
        assertEquals("success", published.getLast().outcomes().getFirst().state()); assertNotNull(published.getLast().discovery());
    }
    @Test void remotesEndpointListsConfiguredRemotes() throws Exception {
        mvc.perform(get(prefix + "/repositories/" + repository + "/remotes")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        git.text(base, "remote", "add", "upstream", base.resolve("elsewhere.git").toString());
        mvc.perform(get(prefix + "/repositories/" + repository + "/remotes")).andExpect(jsonPath("$[0]").value("upstream"));
    }
    @Test void syncEndpointSummarizesDiscoveredRepositories() throws Exception {
        mvc.perform(get(prefix + "/sync")).andExpect(status().isOk()).andExpect(jsonPath("$.repositories[0].id").value(repository))
                .andExpect(jsonPath("$.repositories[0].changes").value(1)).andExpect(jsonPath("$.repositories[0].lastFetch").doesNotExist());
    }
    @Test void historyEndpointsReadCommitsAndRejectRevisionExpressions() throws Exception {
        mvc.perform(get(prefix + "/repositories/" + repository + "/log")).andExpect(status().isOk()).andExpect(jsonPath("$.commits.length()").value(0));
        git.text(base, "config", "user.name", "Fixture"); git.text(base, "config", "user.email", "fixture@example.invalid");
        git.text(base, "add", "new.txt"); git.text(base, "commit", "-m", "First");
        String sha = git.text(base, "rev-parse", "HEAD");
        mvc.perform(get(prefix + "/repositories/" + repository + "/log")).andExpect(jsonPath("$.commits[0].subject").value("First")).andExpect(jsonPath("$.commits[0].pushed").value(false));
        mvc.perform(get(prefix + "/repositories/" + repository + "/commits/" + sha + "/files")).andExpect(jsonPath("$[0].path").value("new.txt")).andExpect(jsonPath("$[0].status").value("A"));
        mvc.perform(get(prefix + "/repositories/" + repository + "/commits/" + sha + "/diff").param("path", "new.txt")).andExpect(jsonPath("$.lines[0].type").value("+")).andExpect(jsonPath("$.partial").value(false));
        mvc.perform(get(prefix + "/repositories/" + repository + "/commits/HEAD/files")).andExpect(status().isBadRequest());
    }
    @Test void branchEndpointsSwitchCreateAndRefreshProjectLabels() throws Exception {
        git.text(base, "config", "user.name", "Fixture"); git.text(base, "config", "user.email", "fixture@example.invalid");
        git.text(base, "add", "new.txt"); git.text(base, "commit", "-m", "First"); git.text(base, "branch", "feature/demo"); git.text(base, "tag", "v1");
        Path nested = Files.createDirectories(base.resolve("nested")); git.text(nested, "init", "-b", "main");
        var inside = new net.olaba.mvnbuilder.entities.MavenProject(); inside.setAbsolutePath(base.toString());
        var other = new net.olaba.mvnbuilder.entities.MavenProject(); other.setAbsolutePath(nested.toString());
        when(projects.findByWorkspaceIdOrderByExecutionOrderAsc(1L)).thenReturn(List.of(inside, other));
        mvc.perform(get(prefix + "/repositories/" + repository + "/references")).andExpect(status().isOk())
                .andExpect(jsonPath("$.current").value("main")).andExpect(jsonPath("$.local[?(@.name=='feature/demo')].ref").value("refs/heads/feature/demo"))
                .andExpect(jsonPath("$.tags[0].name").value("v1"));
        mvc.perform(post(prefix + "/repositories/" + repository + "/checkout").contentType("application/json").content("{\"reference\":\"refs/heads/feature/demo\",\"type\":\"LOCAL\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.current").value("feature/demo"));
        assertEquals("feature/demo", git.text(base, "branch", "--show-current"));
        assertEquals("feature/demo", inside.getGitBranch()); assertNull(other.getGitBranch(), "projects of a nested repository keep their own branch");
        mvc.perform(post(prefix + "/repositories/" + repository + "/branches").contentType("application/json").content("{\"name\":\"feature/login\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.current").value("feature/login"));
        assertEquals("feature/login", git.text(base, "branch", "--show-current")); assertEquals("feature/login", inside.getGitBranch());
        mvc.perform(post(prefix + "/repositories/" + repository + "/branches").contentType("application/json").content("{\"name\":\"bad..name\"}")).andExpect(status().isBadRequest());
        mvc.perform(post(prefix + "/repositories/" + repository + "/branches").contentType("application/json").content("{\"name\":\"-f\"}")).andExpect(status().isBadRequest());
        mvc.perform(post(prefix + "/repositories/" + repository + "/branches").contentType("application/json").content("{\"name\":\"main\"}")).andExpect(status().isConflict());
    }
    @Test void openEditorLaunchesVsCodeAtTheRepositoryRoot() throws Exception {
        mvc.perform(post(prefix + "/repositories/" + repository + "/open-editor")).andExpect(status().isOk()).andExpect(jsonPath("$.opened").value("."));
        verify(launcher).openVsCode(base.toRealPath().toFile());
        doThrow(new java.io.IOException("missing")).when(launcher).openVsCode(any());
        mvc.perform(post(prefix + "/repositories/" + repository + "/open-editor")).andExpect(status().isUnprocessableEntity());
    }
    @Test void fileEndpointsReadAndSaveWithTheOpenedIdentity() throws Exception {
        String body = mvc.perform(get(prefix + "/repositories/" + repository + "/file").param("path", "new.txt")).andExpect(status().isOk())
                .andExpect(jsonPath("$.editable").value(true)).andExpect(jsonPath("$.content").value("content\n")).andReturn().getResponse().getContentAsString();
        String hash = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("hash").asText();
        mvc.perform(put(prefix + "/repositories/" + repository + "/file").contentType("application/json").content("{\"path\":\"new.txt\",\"content\":\"edited\\n\",\"hash\":\"" + hash + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").value("edited\n"));
        assertEquals("edited\n", Files.readString(base.resolve("new.txt")));
        mvc.perform(put(prefix + "/repositories/" + repository + "/file").contentType("application/json").content("{\"path\":\"new.txt\",\"content\":\"again\",\"hash\":\"" + hash + "\"}"))
                .andExpect(status().isConflict());
    }
}
