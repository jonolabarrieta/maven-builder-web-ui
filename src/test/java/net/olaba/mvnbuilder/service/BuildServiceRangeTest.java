package net.olaba.mvnbuilder.service;

import net.olaba.mvnbuilder.entities.MavenProject;
import net.olaba.mvnbuilder.model.BuildRangeMode;
import net.olaba.mvnbuilder.repository.BuildProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BuildServiceRangeTest {

    @Mock private ProcessExecutionService processExecutionService;
    @Mock private WorkspaceService workspaceService;
    @Mock private BuildProfileRepository buildProfileRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private SystemSettingService systemSettingService;
    @InjectMocks private BuildService buildService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void selectsInclusiveRangesUsingExecutionOrder() {
        final List<MavenProject> projects = List.of(project(10L, "first"), project(20L, "selected"), project(30L, "last"));

        assertEquals(List.of("selected"), artifactIds(buildService.selectBuildRange(projects, 20L, BuildRangeMode.ONLY)));
        assertEquals(List.of("selected", "last"), artifactIds(buildService.selectBuildRange(projects, 20L, BuildRangeMode.FROM)));
        assertEquals(List.of("first", "selected"), artifactIds(buildService.selectBuildRange(projects, 20L, BuildRangeMode.THROUGH)));
    }

    @Test
    void rejectsProjectsOutsideTheWorkspaceRange() {
        final List<MavenProject> projects = List.of(project(10L, "first"));

        assertThrows(IllegalArgumentException.class,
                () -> buildService.selectBuildRange(projects, 99L, BuildRangeMode.FROM));
    }

    private static MavenProject project(final Long id, final String artifactId) {
        return MavenProject.builder().withId(id).withArtifactId(artifactId).build();
    }

    private static List<String> artifactIds(final List<MavenProject> projects) {
        return projects.stream().map(MavenProject::getArtifactId).toList();
    }
}
