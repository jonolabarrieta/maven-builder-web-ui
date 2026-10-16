package net.olaba.mvnbuilder.controller;

import net.olaba.mvnbuilder.entities.MavenProject;
import net.olaba.mvnbuilder.entities.Workspace;
import net.olaba.mvnbuilder.repository.*;
import net.olaba.mvnbuilder.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises the HTTP contract, real Git, rendered Thymeleaf, and workspace refresh together. */
class WorkspaceGitReferenceTest {
    @TempDir Path directory;
    private final GitService git = new GitService();
    private final MavenProjectRepository projects = mock(MavenProjectRepository.class);
    private final WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
    private final AtomicReference<List<MavenProject>> stored = new AtomicReference<>();
    private WorkspaceService workspaceService;
    private MavenProject project;
    private MockMvc mvc;
    private String tagCommit;
    private String remoteCommit;

    @BeforeEach
    void setUp() throws Exception {
        command("init", "-b", "main");
        command("config", "user.name", "HTTP Reference Tests");
        command("config", "user.email", "reference-tests@invalid.example");
        command("config", "commit.gpgsign", "false");
        command("config", "tag.gpgsign", "false");
        command("config", "core.hooksPath", directory.resolve("disabled-hooks").toString());
        Files.writeString(directory.resolve("pom.xml"), "<project><modelVersion>4.0.0</modelVersion>"
                + "<groupId>test</groupId><artifactId>reference-test</artifactId><version>1.0</version></project>");
        tagCommit = commit("tag");
        command("tag", "v1.0.0");
        command("tag", "-a", "v2.0.0", "-m", "annotated");
        remoteCommit = commit("remote");
        command("remote", "add", "origin", ".");
        command("update-ref", "refs/remotes/origin/feature/demo", remoteCommit);
        command("symbolic-ref", "refs/remotes/origin/HEAD", "refs/remotes/origin/feature/demo");
        commit("main");

        final Workspace workspace = Workspace.builder().withId(1L).withName("Reference Test")
                .withBasePath(directory.toString()).build();
        project = MavenProject.builder().withId(2L).withArtifactId("reference-test").withGroupId("test")
                .withVersion("1.0").withRelativePath(".").withAbsolutePath(directory.toString())
                .withGitBranch("main").withWorkspace(workspace).withEnabled(true).build();
        stored.set(List.of(project));
        workspace.getProjects().add(project);
        when(workspaces.findById(1L)).thenReturn(Optional.of(workspace));
        when(projects.findById(2L)).thenAnswer(invocation -> Optional.of(stored.get().getFirst()));
        when(projects.findByWorkspaceIdOrderByExecutionOrderAsc(1L)).thenAnswer(invocation -> stored.get());
        when(projects.saveAll(any())).thenAnswer(invocation -> {
            final List<MavenProject> saved = invocation.getArgument(0);
            saved.forEach(p -> { if (p.getId() == null) p.setId(2L); });
            stored.set(saved);
            return saved;
        });
        workspaceService = new WorkspaceService(workspaces, projects, new MavenService(), git,
                mock(BuildProfileRepository.class));
        final var controller = new WorkspaceController(workspaceService, mock(BuildService.class),
                mock(TopologicalSortService.class), projects, git, mock(MavenRepositoryService.class),
                mock(FileSystemService.class), new MavenService(), mock(SystemSettingService.class),
                mock(JavaInstallationRepository.class), mock(UpdateService.class), mock(DesktopLauncherService.class));
        final var templates = new ClassLoaderTemplateResolver();
        templates.setPrefix("templates/");
        templates.setSuffix(".html");
        templates.setCharacterEncoding("UTF-8");
        final var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(templates);
        final var views = new ThymeleafViewResolver();
        views.setTemplateEngine(engine);
        views.setCharacterEncoding("UTF-8");
        mvc = MockMvcBuilders.standaloneSetup(controller).setViewResolvers(views).build();
    }

    @Test
    void trackingBranchSurvivesHttpReloadAndRefreshWithoutResettingLocalCommits() throws Exception {
        postCheckout("refs/remotes/origin/feature/demo", "REMOTE").andExpect(status().isOk());
        assertEquals(remoteCommit, command("rev-parse", "HEAD"));
        assertEquals("refs/heads/feature/demo", command("symbolic-ref", "HEAD"));
        assertEquals("refs/remotes/origin/feature/demo", command("rev-parse", "--symbolic-full-name", "@{upstream}"));
        assertEquals("feature/demo", project.getGitBranch());
        final String localTip = commit("extra-local-commit");
        command("checkout", "main");
        postCheckout("refs/remotes/origin/feature/demo", "REMOTE").andExpect(status().isOk());
        assertEquals(localTip, command("rev-parse", "HEAD"));
        final String html = selector();
        writeBrowserFixture("remote", html);
        assertTrue(buttonFor(html, "refs/heads/feature/demo").contains("bg-indigo-50"));
        assertTrue(buttonFor(html, "refs/remotes/origin/feature/demo").contains("bg-indigo-50"));
        assertFalse(html.contains("data-ref=\"refs/remotes/origin/HEAD\""));
        mvc.perform(get("/workspaces/1/git-status-table")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].projectId").value(2)).andExpect(jsonPath("$[0].branch").value("feature/demo"));
        explicitRefresh();
        assertEquals("feature/demo", stored.get().getFirst().getGitBranch());
        assertEquals(localTip, command("rev-parse", "HEAD"));
    }

    @Test
    void tagIdentityAndActiveMarkersSurviveReloadStatusAndRescan() throws Exception {
        for (final String tag : List.of("v1.0.0", "v2.0.0")) {
            postCheckout("refs/tags/" + tag, "TAG").andExpect(status().isOk());
            assertEquals(tagCommit, command("rev-parse", "HEAD"));
            assertEquals("v2.0.0 · detached HEAD", stored.get().getFirst().getGitBranch());
            final String html = selector();
            writeBrowserFixture("tag", html);
            assertTrue(html.contains("v2.0.0 · detached HEAD"));
            assertTrue(buttonFor(html, "refs/tags/v1.0.0").contains("bg-indigo-50"));
            assertTrue(buttonFor(html, "refs/tags/v2.0.0").contains("bg-indigo-50"));
            assertFalse(buttonFor(html, "refs/heads/main").contains("bg-indigo-50"));
            mvc.perform(get("/workspaces/1/git-status-table")).andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].branch").value("v2.0.0 · detached HEAD"));
            explicitRefresh();
            assertEquals("v2.0.0 · detached HEAD", stored.get().getFirst().getGitBranch());
        }
        // An outside checkout must replace, rather than retain, the previous tag label.
        command("checkout", "main");
        mvc.perform(get("/workspaces/1/git-status-table")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].branch").value("main"));
        assertTrue(buttonFor(selector(), "refs/heads/main").contains("bg-indigo-50"));
    }

    @Test
    void conflictValidationAndGitFailuresDoNotPersistRequestedNames() throws Exception {
        command("branch", "feature/demo", tagCommit);
        postCheckout("refs/remotes/origin/feature/demo", "REMOTE").andExpect(status().isConflict())
                .andExpect(content().string(containsString("Select it in Local")));
        assertEquals("main", project.getGitBranch());
        assertEquals("refs/heads/main", command("symbolic-ref", "HEAD"));
        postCheckout("refs/tags/v1.0.0", "LOCAL").andExpect(status().isBadRequest());
        postCheckout("refs/tags/missing", "TAG").andExpect(status().isBadRequest());
        postCheckout("refs/tags/v1.0.0", "WRONG").andExpect(status().isBadRequest());
        Files.writeString(directory.resolve("version.txt"), "uncommitted");
        postCheckout("refs/tags/v1.0.0", "TAG").andExpect(status().isInternalServerError())
                .andExpect(content().string(containsString("would be overwritten")));
        assertEquals("uncommitted", Files.readString(directory.resolve("version.txt")));
        assertEquals("main", project.getGitBranch());
        verify(projects, never()).save(any());
    }

    @Test
    void legacyLocalRequestWorksAndNeverGuessesRemote() throws Exception {
        command("branch", "local-topic");
        mvc.perform(post("/projects/2/checkout").param("branch", "local-topic")).andExpect(status().isOk());
        assertEquals("local-topic", project.getGitBranch());
        mvc.perform(post("/projects/2/checkout").param("branch", "feature/demo")).andExpect(status().isBadRequest());
        assertEquals("local-topic", project.getGitBranch());
    }

    @Test
    void renderingEscapesReferenceNamesAndFetchUsesSameTypedModel() throws Exception {
        final String name = "v3'\"<img>";
        command("tag", name, tagCommit);
        mvc.perform(post("/projects/2/branches/fetch")).andExpect(status().isOk())
                .andExpect(content().string(containsString("data-ref-type=\"TAG\"")));
        final String html = selector();
        assertFalse(html.contains("<img>"));
        assertTrue(html.contains("&lt;img&gt;"));
        assertTrue(html.contains("&quot;"));
        assertFalse(html.contains("confirmAndCheckout('"));
        writeBrowserFixture("escaped", html);
    }

    private org.springframework.test.web.servlet.ResultActions postCheckout(final String ref, final String type) throws Exception {
        return mvc.perform(post("/projects/2/checkout").param("branch", ref).param("referenceType", type));
    }

    private String selector() throws Exception {
        return mvc.perform(get("/projects/2/branches")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private void writeBrowserFixture(final String name, final String html) throws Exception {
        final Path fixtures = Files.createDirectories(Path.of("target", "git-reference-browser"));
        Files.writeString(fixtures.resolve(name + ".html"), html);
    }

    private String buttonFor(final String html, final String ref) {
        final var matcher = java.util.regex.Pattern.compile("<button[^>]*data-ref=\""
                + java.util.regex.Pattern.quote(ref) + "\"[^>]*>").matcher(html);
        assertTrue(matcher.find(), "Missing selector choice: " + ref);
        return matcher.group();
    }

    private void explicitRefresh() throws Exception {
        mvc.perform(post("/workspaces/1/refresh")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/workspaces/1"));
    }

    private String commit(final String content) throws Exception {
        Files.writeString(directory.resolve("version.txt"), content);
        command("add", ".");
        command("commit", "-m", content);
        return command("rev-parse", "HEAD");
    }

    private String command(final String... arguments) throws Exception {
        final var args = new java.util.ArrayList<String>();
        args.add("git");
        args.addAll(List.of(arguments));
        final Process process = new ProcessBuilder(args).directory(directory.toFile()).redirectErrorStream(true).start();
        assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Git fixture timed out");
        final String result = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).strip();
        assertEquals(0, process.exitValue(), result);
        return result;
    }
}
