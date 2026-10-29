package net.olaba.mvnbuilder.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.olaba.mvnbuilder.controller.WorkspaceController;
import net.olaba.mvnbuilder.entities.MavenProject;
import net.olaba.mvnbuilder.entities.Workspace;
import net.olaba.mvnbuilder.model.WorkspaceProjectHierarchy;
import net.olaba.mvnbuilder.repository.BuildProfileRepository;
import net.olaba.mvnbuilder.repository.MavenProjectRepository;
import net.olaba.mvnbuilder.repository.WorkspaceRepository;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Reproducible, opt-in scale fixture for comparing workspace refresh work. */
class WorkspaceScaleBenchmarkTest {

    @TempDir
    Path temporaryDirectory;

    /**
     * Measures a representative scan, top-level lookup, and child-indicator pass.
     * Run with -Dworkspace.scale.benchmark=true and the test JVM agent configured.
     *
     * @throws IOException if the fixture cannot be created
     */
    @Test
    void measureWorkspaceRefresh() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean("workspace.scale.benchmark"));
        final Path base = temporaryDirectory.resolve("base");
        final Path external = temporaryDirectory.resolve("external-parent");
        createFixture(base, external, 160, 60);

        final Workspace workspace = Workspace.builder().withId(1L).withName("Scale fixture")
                .withBasePath(base.toString()).build();
        workspace.getExcludedPaths().add("excluded");
        final MavenProject externalParent = MavenProject.builder().withId(10_000L)
                .withRelativePath("../external-parent").withAbsolutePath(external.toString())
                .withWorkspace(workspace).build();
        final AtomicReference<List<MavenProject>> stored = new AtomicReference<>(List.of(externalParent));
        final AtomicInteger gitCalls = new AtomicInteger();

        final WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
        final MavenProjectRepository projectRepository = mock(MavenProjectRepository.class);
        final BuildProfileRepository profileRepository = mock(BuildProfileRepository.class);
        final GitService gitService = mock(GitService.class);
        when(workspaceRepository.findById(1L)).thenReturn(java.util.Optional.of(workspace));
        when(projectRepository.findByWorkspaceIdOrderByExecutionOrderAsc(anyLong()))
                .thenAnswer(invocation -> stored.get());
        when(projectRepository.saveAll(any())).thenAnswer(invocation -> {
            final List<MavenProject> saved = new ArrayList<>(invocation.getArgument(0));
            long nextId = 1;
            for (final MavenProject project : saved) {
                if (project.getId() == null) {
                    project.setId(nextId++);
                }
                project.setWorkspace(workspace);
            }
            stored.set(saved);
            workspace.getProjects().clear();
            workspace.getProjects().addAll(saved);
            return saved;
        });
        when(gitService.getCurrentBranch(any(File.class))).thenAnswer(invocation -> {
            Thread.sleep(5);
            gitCalls.incrementAndGet();
            return "main";
        });

        final WorkspaceService service = new WorkspaceService(workspaceRepository, projectRepository,
                new MavenService(), gitService, profileRepository);
        final long scanStart = System.nanoTime();
        service.scanAndImportProjects(workspace);
        final long scanMillis = elapsedMillis(scanStart);
        final int scanGitCalls = gitCalls.getAndSet(0);
        // The Git phase that follows every refresh, at the default branch-reader limit.
        final List<MavenProject> scanned = stored.get();
        final long gitStart = System.nanoTime();
        service.refreshGitStatus(1L);
        final long gitMillis = elapsedMillis(gitStart);
        // The mocked saveAll keeps only what was saved last; the Git phase saves top-level rows only.
        stored.set(scanned);
        service.closeBranchReaders();
        final long hierarchyStart = System.nanoTime();
        final WorkspaceProjectHierarchy hierarchy = service.getProjectHierarchyForWorkspace(1L);
        final List<MavenProject> parents = hierarchy.topLevelProjects();
        final long hierarchyMillis = elapsedMillis(hierarchyStart);
        final long childrenStart = System.nanoTime();
        for (final MavenProject parent : parents) {
            parent.hasChildren();
        }
        final long childrenMillis = elapsedMillis(childrenStart);
        renderTemplate(workspace, hierarchy, false);
        final long templateStart = System.nanoTime();
        final String renderedPage = renderTemplate(workspace, hierarchy, false);
        final long templateMillis = elapsedMillis(templateStart);
        renderTemplate(workspace, hierarchy, true);
        final long fragmentStart = System.nanoTime();
        final String renderedFragment = renderTemplate(workspace, hierarchy, true);
        final long fragmentMillis = elapsedMillis(fragmentStart);
        final byte[] gitPayload = new ObjectMapper().writeValueAsBytes(parents.stream()
                .map(project -> new WorkspaceController.GitBranchUpdate(project.getId(), project.getGitBranch()))
                .toList());
        System.out.printf("WORKSPACE_SCALE projects=%d parents=%d scan_ms=%d scan_git_calls=%d git_calls=%d git_ms=%d "
                        + "hierarchy_ms=%d children_ms=%d template_ms=%d html_bytes=%d "
                        + "fragment_ms=%d fragment_bytes=%d git_payload_bytes=%d%n",
                stored.get().size(), parents.size(), scanMillis, scanGitCalls, gitCalls.get(),
                gitMillis, hierarchyMillis, childrenMillis,
                templateMillis, renderedPage.getBytes(StandardCharsets.UTF_8).length,
                fragmentMillis, renderedFragment.getBytes(StandardCharsets.UTF_8).length,
                gitPayload.length);
    }

    /**
     * Creates ordinary, nested, excluded, and external Maven POMs.
     *
     * @param base workspace base directory
     * @param external external parent directory
     * @param projectCount number of top-level projects
     * @param excludedCount number of POMs beneath the excluded subtree
     * @throws IOException if a file cannot be written
     */
    private void createFixture(final Path base, final Path external, final int projectCount,
            final int excludedCount) throws IOException {
        for (int index = 0; index < projectCount; index++) {
            final Path project = base.resolve("project-" + index);
            writePom(project, "project-" + index);
            Files.createDirectory(project.resolve(".git"));
            if (index % 8 == 0) {
                writePom(project.resolve("module"), "module-" + index);
            }
        }
        for (int index = 0; index < excludedCount; index++) {
            writePom(base.resolve("excluded").resolve("ignored-" + index), "ignored-" + index);
        }
        writePom(external, "external-parent");
        Files.createDirectory(external.resolve(".git"));
        writePom(external.resolve("module"), "external-module");
    }

    /**
     * Writes one minimal, valid Maven POM.
     *
     * @param directory project directory
     * @param artifactId Maven artifact identifier
     * @throws IOException if the directory or POM cannot be written
     */
    private void writePom(final Path directory, final String artifactId) throws IOException {
        Files.createDirectories(directory);
        final String pom = "<project><modelVersion>4.0.0</modelVersion><groupId>test</groupId>"
                + "<artifactId>" + artifactId + "</artifactId><version>1</version></project>";
        Files.writeString(directory.resolve("pom.xml"), pom, StandardCharsets.UTF_8);
    }

    /**
     * Renders a workspace template or fragment with the fixture's stored projects.
     *
     * @param workspace fixture workspace
     * @param hierarchy top-level projects and child indicators to display
     * @param fragment whether to render only the project-table-body fragment
     * @return rendered HTML
     */
    private String renderTemplate(final Workspace workspace, final WorkspaceProjectHierarchy hierarchy,
            final boolean fragment) {
        final ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        final SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);

        final MockServletContext servletContext = new MockServletContext();
        final MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(servletContext);
        final WebContext context = new WebContext(application.buildExchange(request, response));
        context.setVariable("workspace", workspace);
        context.setVariable("projects", hierarchy.topLevelProjects());
        context.setVariable("projectIdsWithChildren", hierarchy.projectIdsWithChildren());
        context.setVariable("blocks", hierarchy.topLevelProjects().stream()
                .map(p -> new WorkspaceGroupService.Block("p:" + p.getId(), null, List.of(p))).toList());
        context.setVariable("groups", List.of());
        context.setVariable("parents", List.of());
        context.setVariable("consumers", List.of());
        context.setVariable("availableGroupIds", List.of());
        context.setVariable("javaInstallations", List.of());
        context.setVariable("currentVersion", "benchmark");
        if (fragment) {
            return engine.process("workspace-detail", Set.of("project-table-body"), context);
        }
        return engine.process("workspace-detail", context);
    }

    /**
     * Converts elapsed monotonic time to milliseconds.
     *
     * @param startNanos start time from System.nanoTime
     * @return elapsed milliseconds
     */
    private long elapsedMillis(final long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
