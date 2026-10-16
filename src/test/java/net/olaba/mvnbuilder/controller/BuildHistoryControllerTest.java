package net.olaba.mvnbuilder.controller;

import net.olaba.mvnbuilder.entities.BuildRunType;
import net.olaba.mvnbuilder.entities.Workspace;
import net.olaba.mvnbuilder.model.BuildHistoryPage;
import net.olaba.mvnbuilder.model.BuildProjectView;
import net.olaba.mvnbuilder.model.BuildRunView;
import net.olaba.mvnbuilder.model.ProjectBuildStat;
import net.olaba.mvnbuilder.service.BuildHistoryService;
import net.olaba.mvnbuilder.service.UpdateService;
import net.olaba.mvnbuilder.service.WorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Renders the build history page and checks its content and empty state. */
class BuildHistoryControllerTest {

    private final WorkspaceService workspaceService = mock(WorkspaceService.class);
    private final BuildHistoryService buildHistoryService = mock(BuildHistoryService.class);
    private final UpdateService updateService = mock(UpdateService.class);
    private MockMvc mvc;

    /** Builds a standalone MockMvc rendering the real Thymeleaf template. */
    @BeforeEach
    void setUp() {
        when(updateService.getCurrentVersion()).thenReturn("1.6.0");
        final BuildHistoryController controller = new BuildHistoryController(workspaceService, buildHistoryService,
                updateService);
        final ClassLoaderTemplateResolver templates = new ClassLoaderTemplateResolver();
        templates.setPrefix("templates/");
        templates.setSuffix(".html");
        templates.setCharacterEncoding("UTF-8");
        final SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(templates);
        final ThymeleafViewResolver views = new ThymeleafViewResolver();
        views.setTemplateEngine(engine);
        views.setCharacterEncoding("UTF-8");
        mvc = MockMvcBuilders.standaloneSetup(controller).setViewResolvers(views).build();
    }

    /** A populated history renders runs, timings and averages. */
    @Test
    void rendersRunsAndAverages() throws Exception {
        when(workspaceService.getWorkspace(1L)).thenReturn(Optional.of(workspace()));
        final BuildRunView run = new BuildRunView(1L, "2026-10-16T10:00:00Z", BuildRunType.FULL, 2, 3_600_000L, true,
                List.of(new BuildProjectView("mod-a", 1200, true, 0),
                        new BuildProjectView("mod-b", 60_000, false, 1)));
        final ProjectBuildStat stat = new ProjectBuildStat("mod-a", 1200.0, 3, 1000, 1500);
        when(buildHistoryService.load(1L, 0))
                .thenReturn(new BuildHistoryPage(0, 1, 1, List.of(run), List.of(stat)));

        mvc.perform(get("/workspaces/1/build-history"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Build history")))
                .andExpect(content().string(containsString("Build All")))
                .andExpect(content().string(containsString("mod-a")))
                .andExpect(content().string(containsString("1.2 s")))
                .andExpect(content().string(containsString("1h 0m")))
                .andExpect(content().string(containsString("Average per project")));
    }

    /** An empty workspace shows the empty state rather than an empty table. */
    @Test
    void rendersEmptyState() throws Exception {
        when(workspaceService.getWorkspace(1L)).thenReturn(Optional.of(workspace()));
        when(buildHistoryService.load(1L, 0))
                .thenReturn(new BuildHistoryPage(0, 0, 0, List.of(), List.of()));

        mvc.perform(get("/workspaces/1/build-history"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No builds recorded yet")));
    }

    /** An unknown workspace returns 404. */
    @Test
    void unknownWorkspaceReturnsNotFound() throws Exception {
        when(workspaceService.getWorkspace(99L)).thenReturn(Optional.empty());

        mvc.perform(get("/workspaces/99/build-history")).andExpect(status().isNotFound());
    }

    private Workspace workspace() {
        return Workspace.builder().withId(1L).withName("Platform").build();
    }
}
