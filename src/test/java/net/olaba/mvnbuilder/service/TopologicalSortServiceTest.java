package net.olaba.mvnbuilder.service;

import net.olaba.mvnbuilder.entities.MavenProject;
import net.olaba.mvnbuilder.repository.MavenProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TopologicalSortServiceTest {

    @Mock
    private MavenProjectRepository mavenProjectRepository;

    @Mock
    private WorkspaceService workspaceService;

    @InjectMocks
    private TopologicalSortService topologicalSortService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCalculateOrderSimple() {
        // Simple case: A depends on B. Build order should be B then A.
        MavenProject projectA = MavenProject.builder()
                .withId(1L)
                .withGroupId("com.test")
                .withArtifactId("project-a")
                .withRelativePath("project-a")
                .withInternalDependencies(Arrays.asList("com.test:project-b"))
                .build();

        MavenProject projectB = MavenProject.builder()
                .withId(2L)
                .withGroupId("com.test")
                .withArtifactId("project-b")
                .withRelativePath("project-b")
                .withInternalDependencies(Collections.emptyList())
                .build();

        List<MavenProject> allProjects = Arrays.asList(projectA, projectB);
        when(mavenProjectRepository.findByWorkspaceIdOrderByExecutionOrderAsc(1L)).thenReturn(allProjects);
        
        // Mock isChildOf (no nesting in this simple case)
        when(workspaceService.isChildOf(any(), any())).thenReturn(false);

        topologicalSortService.calculateOrder(1L);

        // Verify that save was called for both projects
        verify(mavenProjectRepository, times(1)).save(projectA);
        verify(mavenProjectRepository, times(1)).save(projectB);

        // B should be built before A
        assertTrue(projectB.getExecutionOrder() < projectA.getExecutionOrder(), 
                "Project B should have a lower execution order than Project A");
    }

    @Test
    void testCalculateOrderWithCycle() {
        // Cycle: A depends on B, B depends on A
        MavenProject projectA = MavenProject.builder()
                .withId(1L)
                .withGroupId("com.test")
                .withArtifactId("project-a")
                .withRelativePath("project-a")
                .withInternalDependencies(Arrays.asList("com.test:project-b"))
                .build();

        MavenProject projectB = MavenProject.builder()
                .withId(2L)
                .withGroupId("com.test")
                .withArtifactId("project-b")
                .withRelativePath("project-b")
                .withInternalDependencies(Arrays.asList("com.test:project-a"))
                .build();

        List<MavenProject> allProjects = Arrays.asList(projectA, projectB);
        when(mavenProjectRepository.findByWorkspaceIdOrderByExecutionOrderAsc(1L)).thenReturn(allProjects);
        when(workspaceService.isChildOf(any(), any())).thenReturn(false);

        // Should not throw exception and should output some order
        assertDoesNotThrow(() -> topologicalSortService.calculateOrder(1L));
        
        assertNotNull(projectA.getExecutionOrder());
        assertNotNull(projectB.getExecutionOrder());
    }

    @Test
    void testCalculateOrderWithDuplicateCandidates() {
        // Project A depends on "com.test:project-b".
        // There are two candidates for "com.test:project-b":
        // 1. projectB1 (id=2, enabled=true, relativePath="path/b1")
        // 2. projectB2 (id=3, enabled=false, relativePath="path/b2")
        // The resolution should select the enabled one (projectB1) and order should be B1 -> A.
        MavenProject projectA = MavenProject.builder()
                .withId(1L)
                .withGroupId("com.test")
                .withArtifactId("project-a")
                .withRelativePath("project-a")
                .withInternalDependencies(Arrays.asList("com.test:project-b"))
                .build();

        MavenProject projectB1 = MavenProject.builder()
                .withId(2L)
                .withGroupId("com.test")
                .withArtifactId("project-b")
                .withRelativePath("path/b1")
                .withEnabled(true)
                .withInternalDependencies(Collections.emptyList())
                .build();

        MavenProject projectB2 = MavenProject.builder()
                .withId(3L)
                .withGroupId("com.test")
                .withArtifactId("project-b")
                .withRelativePath("path/b2")
                .withEnabled(false)
                .withInternalDependencies(Collections.emptyList())
                .build();

        List<MavenProject> allProjects = Arrays.asList(projectA, projectB1, projectB2);
        when(mavenProjectRepository.findByWorkspaceIdOrderByExecutionOrderAsc(1L)).thenReturn(allProjects);
        when(workspaceService.isChildOf(any(), any())).thenReturn(false);

        topologicalSortService.calculateOrder(1L);

        // Verify save was called on A and B1, and A's execution order is after B1's
        verify(mavenProjectRepository, times(1)).save(projectA);
        verify(mavenProjectRepository, times(1)).save(projectB1);
        
        assertTrue(projectB1.getExecutionOrder() < projectA.getExecutionOrder(),
                "Project B1 should have a lower execution order than Project A");
    }

    @Test
    void testCalculateOrderPrioritizesTransitiveDependentsCountAscending() {
        // We have two independent hierarchies:
        // H1: P1_core -> P1_api (P1_api has 1 dependent: P1_core)
        // H2: P2_impl -> P2_core -> P2_api (P2_api has 2 dependents: P2_core, P2_impl)
        // At the start, both P1_api and P2_api are ready (in-degree 0).
        // Since both have transitive dependencies count = 0, we prioritize transitive dependents count ascending.
        // Thus, P1_api (1 transitive dependent) should be scheduled before P2_api (2 transitive dependents).
        
        MavenProject p1Api = MavenProject.builder()
                .withId(10L)
                .withGroupId("com.test")
                .withArtifactId("p1-api")
                .withRelativePath("p1-api")
                .withInternalDependencies(Collections.emptyList())
                .build();
                
        MavenProject p1Core = MavenProject.builder()
                .withId(11L)
                .withGroupId("com.test")
                .withArtifactId("p1-core")
                .withRelativePath("p1-core")
                .withInternalDependencies(Arrays.asList("com.test:p1-api"))
                .build();

        MavenProject p2Api = MavenProject.builder()
                .withId(20L)
                .withGroupId("com.test")
                .withArtifactId("p2-api")
                .withRelativePath("p2-api")
                .withInternalDependencies(Collections.emptyList())
                .build();

        MavenProject p2Core = MavenProject.builder()
                .withId(21L)
                .withGroupId("com.test")
                .withArtifactId("p2-core")
                .withRelativePath("p2-core")
                .withInternalDependencies(Arrays.asList("com.test:p2-api"))
                .build();

        MavenProject p2Impl = MavenProject.builder()
                .withId(22L)
                .withGroupId("com.test")
                .withArtifactId("p2-impl")
                .withRelativePath("p2-impl")
                .withInternalDependencies(Arrays.asList("com.test:p2-core"))
                .build();

        List<MavenProject> allProjects = Arrays.asList(p1Api, p1Core, p2Api, p2Core, p2Impl);
        when(mavenProjectRepository.findByWorkspaceIdOrderByExecutionOrderAsc(1L)).thenReturn(allProjects);
        when(workspaceService.isChildOf(any(), any())).thenReturn(false);

        topologicalSortService.calculateOrder(1L);

        // Verify that P1_api was scheduled before P2_api (due to ascending dependents logic)
        assertTrue(p1Api.getExecutionOrder() < p2Api.getExecutionOrder(),
                "P1_api (1 transitive dependent) should be scheduled before P2_api (2 transitive dependents)");
    }

    @Test
    void testWorkspace6BuildOrder() {
        // Setup mock parent projects for Workspace 6 (22 projects)
        List<MavenProject> mockProjects = new ArrayList<>();
        
        // 1. r01f
        mockProjects.add(createMockProject(101L, "r01.fabric.base", "r01f",
                "../../../AdminDigital/AdminDigitalCommon/e80a076-fabric/e80a076-fabric-r01f/e80a076-fabric-r01f-base",
                "/mock/e80a076-fabric-r01f/r01f",
                Collections.emptyList()));
                
        // 2. r01fConfigByEnv
        mockProjects.add(createMockProject(102L, "r01.fabric.config", "r01fConfigByEnv",
                "../../../AdminDigital/AdminDigitalCommon/e80a076-fabric/e80a076-fabric-r01f/e80a076-fabric-r01f-config-byenv",
                "/mock/e80a076-fabric-r01f/r01fConfigByEnv",
                Collections.emptyList()));
                
        // 3. r01fBusinessServices
        mockProjects.add(createMockProject(103L, "r01.fabric.businessservices", "r01fBusinessServices",
                "../../../AdminDigital/AdminDigitalCommon/e80a076-fabric/e80a076-fabric-r01f/e80a076-fabric-r01f-business-services",
                "/mock/e80a076-fabric-r01f/r01fBusinessServices",
                Arrays.asList("r01.fabric.base:r01f")));
                
        // 4. r01fCOREServices
        mockProjects.add(createMockProject(104L, "r01.fabric.coreservices", "r01fCOREServices",
                "../../../AdminDigital/AdminDigitalCommon/e80a076-fabric/e80a076-fabric-r01f/e80a076-fabric-r01f-core-services",
                "/mock/e80a076-fabric-r01f/r01fCOREServices",
                Arrays.asList("r01.fabric.base:r01f", "r01.fabric.businessservices:r01fBusinessServices")));
                
        // 5. r01fSecurity
        mockProjects.add(createMockProject(105L, "r01.fabric.security", "r01fSecurity",
                "../../../AdminDigital/AdminDigitalCommon/e80a076-fabric/e80a076-fabric-r01f/e80a076-fabric-r01f-security",
                "/mock/e80a076-fabric-r01f/r01fSecurity",
                Arrays.asList("r01.fabric.base:r01f", "r01.fabric.businessservices:r01fBusinessServices", "r01.fabric.coreservices:r01fCOREServices")));
                
        // 6. r01fEJIE
        mockProjects.add(createMockProject(106L, "r01.fabric.ejie", "r01fEJIE",
                "../../../AdminDigital/AdminDigitalCommon/e80a076-fabric/e80a076-fabric-r01f/e80a076-fabric-r01f-ejie",
                "/mock/e80a076-fabric-r01f/r01fEJIE",
                Arrays.asList("r01.fabric.base:r01f", "r01.fabric.businessservices:r01fBusinessServices", "r01.fabric.coreservices:r01fCOREServices", "r01.fabric.security:r01fSecurity")));
                
        // 7. denaCommonAPI
        mockProjects.add(createMockProject(107L, "dena.api.common", "denaCommonAPI",
                "../e80a021-dena-common/e80a021-dena-common-api",
                "/mock/e80a021-dena-common/denaCommonAPI",
                Arrays.asList("r01.fabric.base:r01f", "r01.fabric.businessservices:r01fBusinessServices", "r01.fabric.security:r01fSecurity")));
                
        // 8. denaCommonCORE
        mockProjects.add(createMockProject(108L, "dena.core.common", "denaCommonCORE",
                "../e80a021-dena-common/e80a021-dena-common-core",
                "/mock/e80a021-dena-common/denaCommonCORE",
                Arrays.asList("r01.fabric.base:r01f", "r01.fabric.businessservices:r01fBusinessServices", "dena.api.common:denaCommonAPI")));
                
        // 9. denaCommonInteropAPI
        mockProjects.add(createMockProject(109L, "dena.api.common", "denaCommonInteropAPI",
                "../e80a021-dena-common/e80a021-dena-common-interop-api",
                "/mock/e80a021-dena-common/denaCommonInteropAPI",
                Arrays.asList("r01.fabric.base:r01f", "dena.api.common:denaCommonAPI")));
                
        // 10. denaCommonInteropCORE
        mockProjects.add(createMockProject(110L, "dena.core.common", "denaCommonInteropCORE",
                "../e80a021-dena-common/e80a021-dena-common-interop-core",
                "/mock/e80a021-dena-common/denaCommonInteropCORE",
                Arrays.asList("r01.fabric.base:r01f", "dena.api.common:denaCommonInteropAPI")));
                
        // 11. denaOrgConfigAPI
        mockProjects.add(createMockProject(111L, "dena.api.orgconfig", "denaOrgConfigAPI",
                "../e80a021-dena-business/e80a021-dena-orgconfig-api",
                "/mock/e80a021-dena-business/denaOrgConfigAPI",
                Arrays.asList("r01.fabric.base:r01f", "dena.api.common:denaCommonAPI")));
                
        // 12. denaOrgConfigCORE
        mockProjects.add(createMockProject(112L, "dena.core.orgconfig", "denaOrgConfigCORE",
                "../e80a021-dena-business/e80a021-dena-orgconfig-core",
                "/mock/e80a021-dena-business/denaOrgConfigCORE",
                Arrays.asList("r01.fabric.base:r01f", "dena.core.common:denaCommonCORE", "dena.api.orgconfig:denaOrgConfigAPI")));
                
        // 13. denaInteropConfigAPI
        mockProjects.add(createMockProject(113L, "dena.api.interopconfig", "denaInteropConfigAPI",
                "../e80a021-dena-business/e80a021-dena-interopconfig-api",
                "/mock/e80a021-dena-business/denaInteropConfigAPI",
                Arrays.asList("r01.fabric.base:r01f", "dena.api.common:denaCommonAPI", "dena.api.orgconfig:denaOrgConfigAPI")));
                
        // 14. denaInteropConfigCORE
        mockProjects.add(createMockProject(114L, "dena.core.interopconfig", "denaInteropConfigCORE",
                "../e80a021-dena-business/e80a021-dena-interopconfig-core",
                "/mock/e80a021-dena-business/denaInteropConfigCORE",
                Arrays.asList("r01.fabric.base:r01f", "dena.core.common:denaCommonCORE", "dena.api.interopconfig:denaInteropConfigAPI")));
                
        // 15. denaS3FileStoreAPI
        mockProjects.add(createMockProject(115L, "dena.api.s3filestore", "denaS3FileStoreAPI",
                "dena/e80a021-dena-interop/e80a021-dena-internal-tools/e80a021-dena-s3-filestore-api",
                "/mock/e80a021-dena-internal-tools/denaS3FileStoreAPI",
                Arrays.asList("r01.fabric.base:r01f", "dena.api.common:denaCommonAPI")));
                
        // 16. denaS3FileStoreCORE
        mockProjects.add(createMockProject(116L, "dena.core.s3filestore", "denaS3FileStoreCORE",
                "dena/e80a021-dena-interop/e80a021-dena-internal-tools/e80a021-dena-s3-filestore-core",
                "/mock/e80a021-dena-internal-tools/denaS3FileStoreCORE",
                Arrays.asList("r01.fabric.base:r01f", "r01.fabric.coreservices:r01fCOREServices", "dena.core.common:denaCommonCORE", "dena.api.s3filestore:denaS3FileStoreAPI")));
                
        // 17. denaPersonAPI
        mockProjects.add(createMockProject(117L, "dena.api.person", "denaPersonAPI",
                "e80a021k-dena-person-api",
                "/mock/e80a021k-dena-person/denaPersonAPI",
                Arrays.asList("r01.fabric.base:r01f", "dena.api.common:denaCommonAPI", "dena.api.common:denaCommonInteropAPI")));
                
        // 18. denaPersonAdminSyncAPI
        mockProjects.add(createMockProject(118L, "dena.api.person", "denaPersonAdminSyncAPI",
                "e80a021k-dena-person-admin-sync-api",
                "/mock/e80a021k-dena-person/denaPersonAdminSyncAPI",
                Arrays.asList("r01.fabric.base:r01f", "dena.api.common:denaCommonAPI", "dena.api.common:denaCommonInteropAPI", "dena.api.orgconfig:denaOrgConfigAPI", "dena.api.person:denaPersonAPI")));
                
        // 19. denaPersonCORE
        mockProjects.add(createMockProject(119L, "dena.core.person", "denaPersonCORE",
                "e80a021k-dena-person-core",
                "/mock/e80a021k-dena-person/denaPersonCORE",
                Arrays.asList("r01.fabric.base:r01f", "r01.fabric.coreservices:r01fCOREServices", "dena.core.common:denaCommonCORE", "dena.api.person:denaPersonAPI", "dena.api.person:denaPersonAdminSyncAPI")));
                
        // 20. denaPersonAdminSyncCORE
        mockProjects.add(createMockProject(120L, "dena.core.person", "denaPersonAdminSyncCORE",
                "e80a021k-dena-person-admin-sync-core",
                "/mock/e80a021k-dena-person/denaPersonAdminSyncCORE",
                Arrays.asList("r01.fabric.base:r01f", "dena.core.common:denaCommonCORE", "dena.core.s3filestore:denaS3FileStoreCORE", "dena.api.orgconfig:denaOrgConfigAPI", "dena.core.interopconfig:denaInteropConfigCORE", "dena.core.person:denaPersonCORE", "dena.api.person:denaPersonAdminSyncAPI")));
                
        // 21. denaPersonAdminSyncREST
        mockProjects.add(createMockProject(121L, "dena.core.person", "denaPersonAdminSyncREST",
                "e80a021k-dena-person-admin-sync-rest",
                "/mock/e80a021k-dena-person/denaPersonAdminSyncREST",
                Arrays.asList("r01.fabric.base:r01f", "r01.fabric.businessservices:r01fBusinessServices", "dena.api.common:denaCommonAPI", "dena.core.common:denaCommonCORE", "dena.core.s3filestore:denaS3FileStoreCORE", "dena.api.orgconfig:denaOrgConfigAPI", "dena.core.orgconfig:denaOrgConfigCORE", "dena.api.person:denaPersonAPI", "dena.core.person:denaPersonCORE", "dena.api.person:denaPersonAdminSyncAPI", "dena.core.person:denaPersonAdminSyncCORE", "dena.api.s3filestore:denaS3FileStoreAPI")));
                
        // 22. denaPersonAdminREST
        mockProjects.add(createMockProject(122L, "dena.core.person", "denaPersonAdminREST",
                "e80a021k-dena-person-admin-rest",
                "/mock/e80a021k-dena-person/denaPersonAdminREST",
                Arrays.asList("r01.fabric.base:r01f", "r01.fabric.businessservices:r01fBusinessServices", "dena.api.common:denaCommonAPI", "dena.core.common:denaCommonCORE", "dena.core.s3filestore:denaS3FileStoreCORE", "dena.api.orgconfig:denaOrgConfigAPI", "dena.core.orgconfig:denaOrgConfigCORE", "dena.api.person:denaPersonAPI", "dena.core.person:denaPersonCORE", "dena.api.person:denaPersonAdminSyncAPI", "dena.core.person:denaPersonAdminSyncCORE", "dena.api.s3filestore:denaS3FileStoreAPI", "dena.api.interopconfig:denaInteropConfigAPI", "dena.core.interopconfig:denaInteropConfigCORE", "dena.core.common:denaCommonInteropCORE")));

        when(mavenProjectRepository.findByWorkspaceIdOrderByExecutionOrderAsc(6L)).thenReturn(mockProjects);
        when(workspaceService.isChildOf(any(), any())).thenReturn(false);

        topologicalSortService.calculateOrder(6L);

        // Capture execution orders
        List<MavenProject> sorted = new ArrayList<>(mockProjects);
        sorted.sort(Comparator.comparing(MavenProject::getExecutionOrder));

        List<String> sortedNames = sorted.stream()
                .map(MavenProject::getArtifactId)
                .collect(Collectors.toList());

        List<String> expectedOrder = Arrays.asList(
                "r01f",
                "r01fConfigByEnv",
                "r01fBusinessServices",
                "r01fCOREServices",
                "r01fSecurity",
                "r01fEJIE",
                "denaCommonAPI",
                "denaCommonCORE",
                "denaCommonInteropAPI",
                "denaCommonInteropCORE",
                "denaS3FileStoreAPI",
                "denaS3FileStoreCORE",
                "denaOrgConfigAPI",
                "denaInteropConfigAPI",
                "denaOrgConfigCORE",
                "denaInteropConfigCORE",
                "denaPersonAPI",
                "denaPersonAdminSyncAPI",
                "denaPersonCORE",
                "denaPersonAdminSyncCORE",
                "denaPersonAdminSyncREST",
                "denaPersonAdminREST"
        );

        assertEquals(expectedOrder, sortedNames, "Calculated build order of Workspace 6 must match expected list exactly.");
    }

    private MavenProject createMockProject(Long id, String groupId, String artifactId, String relativePath, String absolutePath, List<String> deps) {
        return MavenProject.builder()
                .withId(id)
                .withGroupId(groupId)
                .withArtifactId(artifactId)
                .withRelativePath(relativePath)
                .withAbsolutePath(absolutePath)
                .withEnabled(true)
                .withInternalDependencies(deps)
                .build();
    }
}


