package net.olaba.mvnbuilder.service;

import net.olaba.mvnbuilder.entities.RunEntry;
import net.olaba.mvnbuilder.repository.*;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.mockito.Mockito.*;

class WorkspaceDeleteRunEntriesTest {
    @Mock WorkspaceRepository workspaceRepository;
    @Mock MavenProjectRepository mavenProjectRepository;
    @Mock MavenService mavenService;
    @Mock GitService gitService;
    @Mock BuildProfileRepository buildProfileRepository;
    @Mock RunEntryRepository runEntries;
    @InjectMocks WorkspaceService service;

    @Test
    void deletingAWorkspaceDeletesItsRunEntriesFirst() throws Exception {
        try (var mocks = MockitoAnnotations.openMocks(this)) {
            org.springframework.test.util.ReflectionTestUtils.setField(service, "runEntries", runEntries);
            List<RunEntry> entries = List.of(new RunEntry());
            when(runEntries.findByWorkspaceIdOrderByPositionAscIdAsc(4L)).thenReturn(entries);
            service.deleteWorkspace(4L);
            InOrder order = inOrder(runEntries, workspaceRepository);
            order.verify(runEntries).deleteAll(entries);
            order.verify(workspaceRepository).deleteById(4L);
        }
    }
}
