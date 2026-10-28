package net.olaba.mvnbuilder.service;

import net.olaba.mvnbuilder.entities.*;
import net.olaba.mvnbuilder.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:run-entries;DB_CLOSE_DELAY=-1", "spring.jpa.hibernate.ddl-auto=create-drop", "logging.file.name=/tmp/mvnbuilder-run-tests.log"})
@AutoConfigureMockMvc @Transactional
class RunEntriesIntegrationTest {
    @Autowired WorkspaceService workspaces;
    @Autowired WorkspaceGroupService groups;
    @Autowired RunConfigurationService runs;
    @Autowired RunProcessRegistry registry;
    @Autowired MavenProjectRepository projects;
    @Autowired RunEntryRepository entries;
    @Autowired MockMvc mvc;
    @MockBean ProcessExecutionService processes;
    @MockBean SimpMessagingTemplate messages;
    @TempDir Path base;

    @AfterEach
    void stopEverything() { registry.close(); }

    Workspace workspace(String name) { return workspaces.createWorkspace(name, null, true); }
    MavenProject add(Workspace owner, String name, boolean folder) throws Exception {
        Path directory = Files.createDirectories(base.resolve(owner.getId() + "/" + name));
        if (!folder) Files.writeString(directory.resolve("pom.xml"), "<project><modelVersion>4.0.0</modelVersion><groupId>test</groupId><artifactId>" + name + "</artifactId><version>1</version></project>");
        workspaces.addProjectByPath(owner.getId(), directory.toString(), folder);
        return projects.findByWorkspaceIdOrderByExecutionOrderAsc(owner.getId()).stream().filter(p -> directory.toString().equals(p.getAbsolutePath())).findFirst().orElseThrow();
    }
    RunConfigurationService.Form form(String name, Long project, String dir, String command) {
        return new RunConfigurationService.Form(name, project, dir, command, List.of("SERVER_PORT"), List.of("8081"), true);
    }

    @Test
    void ungroupedWorkspaceWithEntriesRoundTripsThroughTheVersionedFormat() throws Exception {
        Workspace a = workspace("Services");
        MavenProject api = add(a, "api", false);
        runs.create(a.getId(), form("api", api.getId(), null, "mvn spring-boot:run"));
        runs.create(a.getId(), new RunConfigurationService.Form("infra", null, base.toString(), "docker compose up", List.of(), List.of(), false));

        String text = workspaces.exportWorkspace(a.getId());
        assertTrue(text.startsWith(WorkspaceExchangeService.HEADER), "RUN entries require the versioned format");
        assertTrue(text.contains("\"runs\""));
        String paths = mvc.perform(get("/workspaces/" + a.getId() + "/export-paths")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(paths.contains("spring-boot") || paths.contains("infra"), "paths-only export never includes RUN entries");

        Workspace copy = workspaces.importWorkspace(text);
        List<RunEntry> restored = entries.findByWorkspaceIdOrderByPositionAscIdAsc(copy.getId());
        assertEquals(List.of("api", "infra"), restored.stream().map(RunEntry::getName).toList());
        RunEntry restoredApi = restored.getFirst();
        Long newApiId = projects.findByWorkspaceIdOrderByExecutionOrderAsc(copy.getId()).getFirst().getId();
        assertNotEquals(api.getId(), newApiId);
        assertEquals(newApiId, restoredApi.getProjectId(), "relinked to the imported copy of its project");
        assertEquals("mvn spring-boot:run", restoredApi.getCommand());
        assertTrue(restoredApi.isUseWorkspaceJava());
        assertEquals("SERVER_PORT", restoredApi.getEnv().getFirst().getName());
        assertEquals("8081", restoredApi.getEnv().getFirst().getValue());
        assertNull(restored.get(1).getProjectId());
        assertEquals(base.toString(), restored.get(1).getWorkingDirectory());
        assertTrue(copy.getImportWarnings().isEmpty(), copy.getImportWarnings().toString());
    }

    @Test
    void entryOfAnImportedProjectKeepsItsLinkThroughTheParentKey() throws Exception {
        Workspace parent = groups.createParent("Shared", null, true), a = workspace("Consumer");
        MavenProject shared = add(parent, "lib", false);
        groups.create(a.getId(), null, parent.getId());
        runs.create(a.getId(), form("lib", shared.getId(), null, "run"));
        Workspace copy = workspaces.importWorkspace(workspaces.exportWorkspace(a.getId()));
        RunEntry restored = entries.findByWorkspaceIdOrderByPositionAscIdAsc(copy.getId()).getFirst();
        Long importedParentProject = groups.composition(copy.getId()).all().getFirst().getId();
        assertEquals(importedParentProject, restored.getProjectId());
        assertNotEquals(shared.getId(), restored.getProjectId());
    }

    @Test
    void unresolvableProjectIsImportedUnlinkedWithAWarning() throws Exception {
        Workspace a = workspace("Services");
        MavenProject api = add(a, "api", false);
        runs.create(a.getId(), form("api", api.getId(), null, "run"));
        String text = workspaces.exportWorkspace(a.getId());
        // The project directory disappears before the import, so the registration is skipped.
        Path apiDir = Path.of(api.getAbsolutePath());
        Files.delete(apiDir.resolve("pom.xml")); Files.delete(apiDir);
        Workspace copy = workspaces.importWorkspace(text);
        RunEntry restored = entries.findByWorkspaceIdOrderByPositionAscIdAsc(copy.getId()).getFirst();
        assertNull(restored.getProjectId());
        assertEquals(api.getAbsolutePath(), restored.getWorkingDirectory());
        assertTrue(copy.getImportWarnings().stream().anyMatch(w -> w.contains("\"api\"") && w.contains("project link")), copy.getImportWarnings().toString());
    }

    @Test
    void versionedFileWithoutRunsStillImports() throws Exception {
        Workspace parent = groups.createParent("Shared", null, true), a = workspace("Old");
        add(parent, "lib", false);
        groups.create(a.getId(), null, parent.getId());
        String text = workspaces.exportWorkspace(a.getId());
        assertFalse(text.contains("\"runs\""), "empty RUN lists are omitted, matching files written before this change");
        Workspace copy = workspaces.importWorkspace(text);
        assertTrue(entries.findByWorkspaceIdOrderByPositionAscIdAsc(copy.getId()).isEmpty());
    }

    @Test
    void invalidRunEntryRejectsTheWholeImport() throws Exception {
        Workspace a = workspace("Services");
        runs.create(a.getId(), new RunConfigurationService.Form("infra", null, base.toString(), "up", List.of(), List.of(), false));
        String text = workspaces.exportWorkspace(a.getId()).replace("\"command\" : \"up\"", "\"command\" : \"\"");
        long before = entries.count();
        assertThrows(IllegalArgumentException.class, () -> workspaces.importWorkspace(text));
        assertEquals(before, entries.count());
    }

    @Test
    void fragmentsAndPageRenderWithRealTemplates() throws Exception {
        Workspace a = workspace("Services");
        MavenProject api = add(a, "api", false);
        RunEntry entry = runs.create(a.getId(), form("api", api.getId(), null, "mvn spring-boot:run"));
        runs.markSelected(a.getId(), List.of(entry.getId()));

        String page = mvc.perform(get("/workspaces/" + a.getId())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(page.contains("data-run-open") && page.contains("run-processes.js"));

        String config = mvc.perform(get("/workspaces/" + a.getId() + "/run/entries")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(config.contains("mvn spring-boot:run") && config.contains("Project: api") && config.contains("plain text"));
        String edit = mvc.perform(get("/workspaces/" + a.getId() + "/run/entries/" + entry.getId() + "/edit")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(edit.contains("hx-put=\"/workspaces/" + a.getId() + "/run/entries/" + entry.getId() + "\""));
        assertTrue(edit.contains("value=\"SERVER_PORT\""));
        String invalid = mvc.perform(post("/workspaces/" + a.getId() + "/run/entries").param("name", "api").param("command", "x"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(invalid.contains("Another RUN entry already uses this name."));

        String modal = mvc.perform(get("/workspaces/" + a.getId() + "/run/modal")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(modal.contains("name=\"entryIds\"") && modal.contains("checked"));
    }

    @Test
    void emptyLaunchModalOffersTheConfiguration() throws Exception {
        Workspace a = workspace("Empty");
        String modal = mvc.perform(get("/workspaces/" + a.getId() + "/run/modal")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(modal.contains("no RUN entries yet") && modal.contains("Open RUN configuration"));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void buildingARunningProjectAsksFirstAndDeletingTheWorkspaceStopsIt() throws Exception {
        Workspace a = workspace("Services");
        MavenProject api = add(a, "api", false);
        RunEntry entry = runs.create(a.getId(), form("api", api.getId(), null, "sleep 30"));
        mvc.perform(post("/workspaces/" + a.getId() + "/run/start").param("entryIds", entry.getId().toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.started[0]").value(entry.getId()));
        assertTrue(registry.isRunning(a.getId(), entry.getId()));

        mvc.perform(post("/projects/" + api.getId() + "/build")).andExpect(status().isConflict())
            .andExpect(jsonPath("$.entries[0].name").value("api"));
        verify(processes, never()).executeCommandWithJavaHome(anyString(), any(), any(), any(String[].class));

        // Stopping a build never touches RUN processes.
        mvc.perform(post("/build/kill")).andExpect(status().isOk());
        assertTrue(registry.isRunning(a.getId(), entry.getId()));

        workspaces.deleteWorkspace(a.getId());
        assertFalse(registry.isRunning(a.getId(), entry.getId()));
    }
}
