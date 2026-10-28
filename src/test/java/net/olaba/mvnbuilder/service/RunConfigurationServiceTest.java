package net.olaba.mvnbuilder.service;

import net.olaba.mvnbuilder.entities.*;
import net.olaba.mvnbuilder.repository.RunEntryRepository;
import net.olaba.mvnbuilder.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class RunConfigurationServiceTest {
    RunEntryRepository repository = mock(RunEntryRepository.class);
    WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
    WorkspaceGroupService groups = mock(WorkspaceGroupService.class);
    SystemSettingService settings = mock(SystemSettingService.class);
    RunConfigurationService service = new RunConfigurationService(repository, workspaces, groups, settings);
    Workspace workspace = Workspace.builder().withId(1L).withName("ws").withBasePath("/base").build();
    List<RunEntry> stored = new ArrayList<>();
    List<MavenProject> projects = new ArrayList<>();
    AtomicLong ids = new AtomicLong(100);

    @BeforeEach
    void setup() {
        when(workspaces.findById(1L)).thenReturn(Optional.of(workspace));
        when(repository.findByWorkspaceIdOrderByPositionAscIdAsc(1L)).thenAnswer(i ->
            stored.stream().sorted(Comparator.comparingInt(RunEntry::getPosition)).toList());
        when(repository.save(any())).thenAnswer(i -> {
            RunEntry e = i.getArgument(0);
            if (e.getId() == null) { e.setId(ids.incrementAndGet()); stored.add(e); }
            return e;
        });
        when(repository.findById(anyLong())).thenAnswer(i -> stored.stream().filter(e -> e.getId().equals(i.getArgument(0))).findFirst());
        doAnswer(i -> stored.remove((RunEntry) i.getArgument(0))).when(repository).delete(any());
        when(groups.composition(1L)).thenAnswer(i -> new WorkspaceGroupService.Composition(List.of(), projects, projects));
        projects.add(MavenProject.builder().withId(7L).withArtifactId("api-service").withAbsolutePath("/base/api").withWorkspace(workspace).build());
        projects.add(MavenProject.builder().withId(8L).withProjectKind(ProjectKind.FOLDER).withAbsolutePath("/base/front").withWorkspace(workspace).build());
    }

    static RunConfigurationService.Form form(String name, Long project, String dir, String command) {
        return new RunConfigurationService.Form(name, project, dir, command, List.of(), List.of(), false);
    }

    @Test
    void projectEntryDefaultsToProjectDirectory() {
        RunEntry entry = service.create(1L, form("api", 7L, null, "mvn spring-boot:run"));
        assertEquals("/base/api", entry.getWorkingDirectory());
        assertEquals(7L, entry.getProjectId());
        assertEquals(0, entry.getPosition());
    }

    @Test
    void severalEntriesForOneProject() {
        service.create(1L, form("front dev", 8L, null, "npm run dev"));
        service.create(1L, form("front mock", 8L, null, "npm run mock"));
        assertEquals(List.of("front dev", "front mock"), service.list(1L).stream().map(RunEntry::getName).toList());
    }

    @Test
    void entryWithoutProjectUsesDirectory() {
        RunEntry entry = service.create(1L, form("infra", null, "/opt/infra", "docker compose up"));
        assertNull(entry.getProjectId());
        assertEquals("/opt/infra", entry.getWorkingDirectory());
    }

    @Test
    void invalidEntriesAreRejectedPerField() {
        service.create(1L, form("api", 7L, null, "x"));
        var empty = assertThrows(RunConfigurationService.ValidationException.class, () -> service.create(1L, form("other", 7L, null, " ")));
        assertTrue(empty.getErrors().containsKey("command"));
        var duplicate = assertThrows(RunConfigurationService.ValidationException.class, () -> service.create(1L, form("API", 7L, null, "x")));
        assertTrue(duplicate.getErrors().containsKey("name"));
        var nowhere = assertThrows(RunConfigurationService.ValidationException.class, () -> service.create(1L, form("x", null, null, "x")));
        assertTrue(nowhere.getErrors().containsKey("workingDirectory"));
        var foreign = assertThrows(RunConfigurationService.ValidationException.class, () -> service.create(1L, form("y", 55L, null, "x")));
        assertTrue(foreign.getErrors().containsKey("projectId"));
        assertEquals(1, stored.size());
    }

    @Test
    void environmentPairsAreValidatedAndOrdered() {
        RunEntry entry = service.create(1L, new RunConfigurationService.Form("api", 7L, null, "run",
            List.of("SERVER_PORT", "", "B"), List.of("8081", "", "2"), true));
        assertEquals(List.of("SERVER_PORT", "B"), entry.getEnv().stream().map(RunEnvVar::getName).toList());
        assertTrue(entry.isUseWorkspaceJava());
        assertThrows(RunConfigurationService.ValidationException.class, () -> service.create(1L,
            new RunConfigurationService.Form("bad", 7L, null, "run", List.of("1BAD"), List.of("x"), false)));
    }

    @Test
    void parentWorkspacesCannotHaveEntries() {
        workspace.setKind(WorkspaceKind.PARENT);
        assertThrows(ResponseStatusException.class, () -> service.create(1L, form("api", null, "/x", "x")));
    }

    @Test
    void duplicateReorderAndDelete() {
        RunEntry api = service.create(1L, form("api", 7L, null, "a"));
        RunEntry front = service.create(1L, form("front", 8L, null, "b"));
        RunEntry copy = service.duplicate(1L, api.getId());
        assertEquals("api (copy)", copy.getName());
        assertEquals(List.of("api", "api (copy)", "front"), service.list(1L).stream().map(RunEntry::getName).toList());
        service.reorder(1L, List.of(front.getId(), api.getId(), copy.getId()));
        assertEquals(List.of("front", "api", "api (copy)"), service.list(1L).stream().map(RunEntry::getName).toList());
        assertThrows(ResponseStatusException.class, () -> service.reorder(1L, List.of(front.getId())));
        service.delete(1L, copy.getId());
        assertEquals(2, stored.size());
    }

    @Test
    void entryOfAnotherWorkspaceIsRejected() {
        RunEntry other = new RunEntry(); other.setId(5L);
        other.setWorkspace(Workspace.builder().withId(2L).build()); stored.add(other);
        assertThrows(ResponseStatusException.class, () -> service.entry(1L, 5L));
    }

    @Test
    void removedProjectLeavesEntryUnlinkedWithItsDirectory() {
        RunEntry api = service.create(1L, form("api", 7L, null, "a"));
        projects.removeIf(p -> p.getId() == 7L);
        var view = service.views(1L).getFirst();
        assertTrue(view.unlinked());
        assertEquals("/base/api", view.entry().getWorkingDirectory());
        // Saving the entry again keeps the dangling link instead of failing.
        assertDoesNotThrow(() -> service.update(1L, api.getId(), form("api", 7L, "/base/api", "b")));
    }

    @Test
    void launchSpecUsesWorkspaceJavaOnlyWhenRequested() {
        workspace.setJavaInstallation(JavaInstallation.builder().withJavaHome("/jdk21").build());
        RunEntry api = service.create(1L, new RunConfigurationService.Form("api", 7L, null, "run", List.of("SERVER_PORT"), List.of("8081"), true));
        RunEntry plain = service.create(1L, form("plain", 7L, null, "run"));
        var spec = service.launchSpec(1L, api.getId());
        assertEquals("/jdk21", spec.javaHome());
        assertEquals(Map.of("SERVER_PORT", "8081"), spec.env());
        assertEquals("/base/api", spec.workingDirectory());
        assertNull(service.launchSpec(1L, plain.getId()).javaHome());
    }
}
