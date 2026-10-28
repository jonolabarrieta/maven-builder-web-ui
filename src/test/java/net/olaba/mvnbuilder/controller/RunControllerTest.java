package net.olaba.mvnbuilder.controller;

import net.olaba.mvnbuilder.entities.RunEntry;
import net.olaba.mvnbuilder.entities.RunEnvVar;
import net.olaba.mvnbuilder.entities.Workspace;
import net.olaba.mvnbuilder.service.RunConfigurationService;
import net.olaba.mvnbuilder.service.RunProcessRegistry;
import net.olaba.mvnbuilder.service.WorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RunControllerTest {
    RunConfigurationService configurations = mock(RunConfigurationService.class);
    RunProcessRegistry processes = mock(RunProcessRegistry.class);
    WorkspaceService workspaces = mock(WorkspaceService.class);
    MockMvc mvc;
    Workspace workspace = Workspace.builder().withId(1L).withName("ws").build();

    static RunConfigurationService.LaunchSpec spec(long id, String name) {
        return new RunConfigurationService.LaunchSpec(1L, id, name, "/tmp", "run", Map.of(), null);
    }

    static RunEntry entry(long id, String name, boolean lastSelected) {
        RunEntry e = new RunEntry(); e.setId(id); e.setName(name); e.setCommand("run"); e.setWorkingDirectory("/tmp");
        e.setLastSelected(lastSelected); e.getEnv().add(new RunEnvVar("A", "1"));
        return e;
    }

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new RunController(configurations, processes, workspaces)).build();
        when(workspaces.getWorkspace(1L)).thenReturn(Optional.of(workspace));
        when(workspaces.getWorkspace(2L)).thenReturn(Optional.empty());
        when(processes.runningEntries(1L)).thenReturn(Set.of(5L));
        when(configurations.views(1L)).thenReturn(List.of(
            new RunConfigurationService.EntryView(entry(4L, "api", true), "api-service", false),
            new RunConfigurationService.EntryView(entry(5L, "front", true), null, false)));
        when(configurations.entry(eq(1L), eq(99L))).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "RUN entry does not belong to this workspace."));
        when(configurations.launchSpec(eq(1L), eq(99L))).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "RUN entry does not belong to this workspace."));
    }

    @Test
    void entriesFragmentHasEmptyFormAndRunningState() throws Exception {
        mvc.perform(get("/workspaces/1/run/entries")).andExpect(status().isOk())
            .andExpect(view().name("fragments/run :: run-config-content"))
            .andExpect(model().attribute("editingId", nullValue()))
            .andExpect(model().attribute("running", Set.of(5L)))
            .andExpect(model().attribute("errors", Map.of()));
    }

    @Test
    void createBindsEnvironmentRowsAndCheckbox() throws Exception {
        mvc.perform(post("/workspaces/1/run/entries").param("name", "api").param("projectId", "7").param("command", "mvn spring-boot:run")
                .param("envName", "SERVER_PORT", "DEBUG").param("envValue", "8081", "").param("useWorkspaceJava", "true"))
            .andExpect(status().isOk()).andExpect(model().attribute("errors", Map.of()));
        verify(configurations).create(1L, new RunConfigurationService.Form("api", 7L, null, "mvn spring-boot:run",
            List.of("SERVER_PORT", "DEBUG"), List.of("8081", ""), true));
    }

    @Test
    void validationErrorsAreRenderedWithTheSubmittedValues() throws Exception {
        when(configurations.create(eq(1L), any())).thenThrow(new RunConfigurationService.ValidationException(Map.of("command", "Enter a command.")));
        mvc.perform(post("/workspaces/1/run/entries").param("name", "api").param("workingDirectory", "/x"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("errors", Map.of("command", "Enter a command.")))
            .andExpect(model().attribute("form", new RunConfigurationService.Form("api", null, "/x", null, List.of(), List.of(), false)));
    }

    @Test
    void editAndUpdateTargetTheEntry() throws Exception {
        when(configurations.entry(1L, 4L)).thenReturn(entry(4L, "api", false));
        mvc.perform(get("/workspaces/1/run/entries/4/edit")).andExpect(model().attribute("editingId", 4L));
        mvc.perform(put("/workspaces/1/run/entries/4").contentType(MediaType.APPLICATION_FORM_URLENCODED).content("name=api2&command=x&workingDirectory=%2Ftmp"))
            .andExpect(status().isOk());
        verify(configurations).update(eq(1L), eq(4L), argThat(f -> f.name().equals("api2") && !f.useWorkspaceJava()));
    }

    @Test
    void entryOfAnotherWorkspaceIsRejected() throws Exception {
        mvc.perform(get("/workspaces/1/run/entries/99/edit")).andExpect(status().isNotFound());
        mvc.perform(post("/workspaces/1/run/99/stop")).andExpect(status().isNotFound());
        mvc.perform(post("/workspaces/1/run/99/restart")).andExpect(status().isNotFound());
        mvc.perform(delete("/workspaces/1/run/entries/99")).andExpect(status().isNotFound());
        verify(processes, never()).stop(any(), any());
    }

    @Test
    void duplicateDeleteAndOrder() throws Exception {
        when(configurations.entry(1L, 4L)).thenReturn(entry(4L, "api", false));
        mvc.perform(post("/workspaces/1/run/entries/4/duplicate")).andExpect(status().isOk());
        verify(configurations).duplicate(1L, 4L);
        mvc.perform(delete("/workspaces/1/run/entries/4")).andExpect(status().isOk());
        var order = inOrder(processes, configurations);
        order.verify(processes).stop(1L, 4L);
        order.verify(processes).dismiss(1L, 4L);
        order.verify(configurations).delete(1L, 4L);
        mvc.perform(post("/workspaces/1/run/entries/order").contentType(MediaType.APPLICATION_JSON).content("[5,4]")).andExpect(status().isOk());
        verify(configurations).reorder(1L, List.of(5L, 4L));
    }

    @Test
    void modalShowsEntriesAndRunningState() throws Exception {
        mvc.perform(get("/workspaces/1/run/modal")).andExpect(status().isOk())
            .andExpect(view().name("fragments/run :: run-launch-content"))
            .andExpect(model().attribute("running", Set.of(5L)))
            .andExpect(model().attribute("entries", hasSize(2)));
    }

    @Test
    void startLaunchesEverySelectedEntryAndRemembersSelection() throws Exception {
        when(configurations.launchSpec(1L, 4L)).thenReturn(spec(4, "api"));
        when(configurations.launchSpec(1L, 6L)).thenReturn(spec(6, "worker"));
        when(processes.start(spec(4, "api"))).thenReturn(true);
        when(processes.start(spec(6, "worker"))).thenThrow(new RunProcessRegistry.StartException("Cannot start \"worker\": working directory /x does not exist."));
        mvc.perform(post("/workspaces/1/run/start").param("entryIds", "4", "6"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.started", contains(4)))
            .andExpect(jsonPath("$.errors[0]", containsString("worker")));
        verify(configurations).markSelected(1L, List.of(4L, 6L));
    }

    @Test
    void startWithoutSelectionIsRejected() throws Exception {
        mvc.perform(post("/workspaces/1/run/start")).andExpect(status().isBadRequest());
        verify(processes, never()).start(any());
    }

    @Test
    void stopRestartStateAndDismiss() throws Exception {
        when(configurations.entry(1L, 4L)).thenReturn(entry(4L, "api", false));
        when(configurations.launchSpec(1L, 4L)).thenReturn(spec(4, "api"));
        mvc.perform(post("/workspaces/1/run/4/stop")).andExpect(status().isNoContent());
        verify(processes).stop(1L, 4L);
        mvc.perform(post("/workspaces/1/run/4/restart")).andExpect(status().isNoContent());
        verify(processes).restart(spec(4, "api"));

        when(processes.snapshot(1L)).thenReturn(List.of(new RunProcessRegistry.Snapshot(4L, "api", RunProcessRegistry.State.RUNNING, null, 42L, 3,
            List.of(new RunProcessRegistry.Line(3, "ready")))));
        mvc.perform(get("/workspaces/1/run/state")).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].state").value("RUNNING"))
            .andExpect(jsonPath("$[0].lastSeq").value(3))
            .andExpect(jsonPath("$[0].lines[0].content").value("ready"));
        mvc.perform(get("/workspaces/2/run/state")).andExpect(status().isNotFound());

        doThrow(new IllegalStateException("Stop \"api\" before closing its log.")).when(processes).dismiss(1L, 4L);
        mvc.perform(delete("/workspaces/1/run/4/log")).andExpect(status().isConflict());
        doNothing().when(processes).dismiss(1L, 5L);
        mvc.perform(delete("/workspaces/1/run/5/log")).andExpect(status().isNoContent());
    }
}
