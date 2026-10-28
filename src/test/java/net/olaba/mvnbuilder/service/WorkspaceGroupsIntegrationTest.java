package net.olaba.mvnbuilder.service;

import net.olaba.mvnbuilder.entities.*;
import net.olaba.mvnbuilder.model.*;
import net.olaba.mvnbuilder.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.mockito.ArgumentCaptor;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:workspace-groups;DB_CLOSE_DELAY=-1", "spring.jpa.hibernate.ddl-auto=create-drop", "logging.file.name=/tmp/mvnbuilder-group-tests.log"})
@AutoConfigureMockMvc @Transactional
class WorkspaceGroupsIntegrationTest {
    @Autowired WorkspaceService workspaces;
    @Autowired WorkspaceGroupService groups;
    @Autowired WorkspaceExchangeService exchange;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired MavenProjectRepository projects;
    @Autowired BuildService builds;
    @Autowired BuildRunRepository history;
    @Autowired JavaInstallationRepository installations;
    @Autowired MockMvc mvc;
    @Autowired WorkspaceGitProjectSource gitProjects;
    @Autowired net.olaba.mvnbuilder.service.git.WorkspaceGitDiscoveryService gitDiscovery;
    @Autowired net.olaba.mvnbuilder.service.git.GitCommandRunner git;
    @MockBean ProcessExecutionService processes;
    @MockBean SimpMessagingTemplate messages;
    @TempDir Path base;

    Workspace workspace(String name) { return workspaces.createWorkspace(name, null, true); }
    Workspace parent(String name) { return groups.createParent(name, null, true); }
    MavenProject add(Workspace owner, String name, boolean folder) throws Exception {
        Path directory = Files.createDirectories(base.resolve(owner.getId() + "/" + name));
        if (!folder) Files.writeString(directory.resolve("pom.xml"), "<project><modelVersion>4.0.0</modelVersion><groupId>test</groupId><artifactId>" + name + "</artifactId><version>1</version></project>");
        workspaces.addProjectByPath(owner.getId(), directory.toString(), folder);
        return projects.findByWorkspaceIdOrderByExecutionOrderAsc(owner.getId()).stream().filter(p -> directory.toString().equals(p.getAbsolutePath())).findFirst().orElseThrow();
    }
    @BeforeEach void commands() {
        when(processes.executeCommandWithJavaHome(anyString(), any(), any(), any(String[].class)))
            .thenReturn(CompletableFuture.completedFuture(new CommandResult(0, List.of(), 1)));
        when(processes.executeCommand(anyString(), any(), any(String[].class)))
            .thenReturn(CompletableFuture.completedFuture(new CommandResult(0, List.of(), 1)));
    }
    @Test void gitViewListsRepositoriesOfOwnDisabledAndImportedProjectsOnly() throws Exception {
        Workspace p=parent("Shared"), a=workspace("A");
        MavenProject enabled=add(a,"enabled",false), disabled=add(a,"disabled",false), folder=add(a,"folder",true), imported=add(p,"imported",false);
        disabled.setEnabled(false); projects.save(disabled);
        groups.create(a.getId(),"Libraries",p.getId());
        for (MavenProject project : List.of(enabled, disabled, folder, imported)) git.text(Path.of(project.getAbsolutePath()), "init", "-b", "main");
        Path unregistered=Files.createDirectories(base.resolve(a.getId() + "/unregistered")); git.text(unregistered, "init", "-b", "main");
        var listed=gitProjects.folders(a).stream().map(f -> f.folder().getFileName().toString()).sorted().toList();
        assertEquals(List.of("disabled","enabled","folder","imported"), listed);
        Workspace owner=workspaceRepository.findById(a.getId()).orElseThrow(); owner.setBasePath(base.resolve(a.getId().toString()).toString());
        var found=gitDiscovery.discover(owner);
        assertEquals(List.of("disabled","enabled","folder","imported"), found.repositories().stream().map(r -> r.displayName()).toList(), found.warnings().toString());
        workspaces.deleteProject(disabled.getId());
        assertTrue(gitDiscovery.last(owner).isEmpty(), "removing a project invalidates the last discovery");
        assertEquals(List.of("enabled","folder","imported"), gitDiscovery.discover(owner).repositories().stream().map(r -> r.displayName()).toList());
    }
    @Test void gitProjectFoldersFallBackToTheBaseFolderWhenNoAbsolutePathIsStored() {
        Workspace owner=new Workspace(); owner.setBasePath(base.toString());
        MavenProject legacy=MavenProject.builder().withId(7L).withWorkspace(owner).withProjectKind(ProjectKind.MAVEN).withArtifactId("legacy").withRelativePath("libs/legacy").build();
        assertEquals(base.resolve("libs/legacy").toAbsolutePath().normalize(), WorkspaceGitProjectSource.folder(legacy).folder());
        MavenProject orphan=MavenProject.builder().withId(8L).withProjectKind(ProjectKind.MAVEN).withArtifactId("orphan").build();
        assertNull(WorkspaceGitProjectSource.folder(orphan).folder());
    }
    @Test void sharedReferencesPropagateAndEnabledOverridesStayLocal() throws Exception {
        Workspace p=parent("Shared"), a=workspace("A"), b=workspace("B");
        MavenProject x=add(p,"X",false);
        var ga=groups.create(a.getId(),"Libraries",p.getId()); groups.create(b.getId(),"Common",p.getId());
        assertTrue(groups.toggle(a.getId(),ga.getId(),x.getId()) == false);
        assertFalse(groups.composition(a.getId()).roots().getFirst().isEnabled());
        assertTrue(groups.composition(b.getId()).roots().getFirst().isEnabled());
        assertTrue(x.isEnabled()); assertEquals(p.getId(),groups.composition(a.getId()).roots().getFirst().getWorkspace().getId());
        MavenProject y=add(p,"Y",false);
        assertEquals(List.of(x.getId(),y.getId()),groups.composition(a.getId()).roots().stream().map(MavenProject::getId).toList());
        assertTrue(groups.composition(a.getId()).roots().getLast().isEnabled());
        assertEquals(2,groups.consumers(p.getId()).size());
    }
    @Test void localGroupsUngroupInPlaceAndRejectForeignMembers() throws Exception {
        Workspace a=workspace("A"), foreign=workspace("B");
        var x=add(a,"X",false); var y=add(a,"Y",true); var z=add(foreign,"Z",false);
        var g=groups.create(a.getId(),"Local",null);
        groups.move(a.getId(),x.getId(),g.getId()); groups.move(a.getId(),y.getId(),g.getId());
        assertThrows(ResponseStatusException.class,()->groups.scope(a.getId(),g.getId(),List.of(x.getId(),z.getId()),false));
        groups.reorderMembers(a.getId(),g.getId(),List.of(y.getId(),x.getId())); groups.remove(a.getId(),g.getId());
        assertEquals(List.of(y.getId(),x.getId()),groups.composition(a.getId()).roots().stream().map(MavenProject::getId).toList());
        assertEquals(2,projects.findByWorkspaceIdOrderByExecutionOrderAsc(a.getId()).size());
        workspaces.updateProjectOrder(a.getId(),List.of(x.getId(),y.getId()));
        assertEquals(List.of(x.getId(),y.getId()),groups.composition(a.getId()).roots().stream().map(MavenProject::getId).toList());
    }
    @Test void removingConsumersPreservesParentAndBlocksReferencedDeletion() throws Exception {
        Workspace p=parent("Shared"), a=workspace("A"); var x=add(p,"X",false);
        var g=groups.create(a.getId(),"Imported",p.getId());
        assertThrows(ResponseStatusException.class,()->workspaces.deleteWorkspace(p.getId()));
        assertThrows(ResponseStatusException.class,()->groups.create(a.getId(),"Duplicate",p.getId()));
        assertThrows(ResponseStatusException.class,()->groups.create(p.getId(),"Nested",null));
        groups.remove(a.getId(),g.getId()); workspaces.deleteWorkspace(a.getId());
        assertTrue(projects.existsById(x.getId())); assertTrue(workspaceRepository.existsById(p.getId()));
    }
    @Test void duplicateConsumerKeepsSharedReferenceAndIndependentLocalDefinitions() throws Exception {
        Workspace p=parent("Shared"), a=workspace("A"); var x=add(a,"X",false); add(p,"P",false);
        var local=groups.create(a.getId(),"Local",null); groups.move(a.getId(),x.getId(),local.getId());
        groups.create(a.getId(),"Shared",p.getId());
        Workspace copy=workspaces.duplicateWorkspace(a.getId());
        assertEquals(2,groups.list(copy.getId()).size());
        assertNotEquals(x.getId(),groups.composition(copy.getId()).roots().getFirst().getId());
        assertEquals(2,groups.consumers(p.getId()).size());
    }
    @Test void portableRoundTripIncludesKindsOverridesOrderAndNewParentIdentity() throws Exception {
        Workspace p=parent("Shared"), a=workspace("A"); var x=add(a,"X",true); var shared=add(p,"P",false);
        var local=groups.create(a.getId(),"Folders",null); groups.move(a.getId(),x.getId(),local.getId());
        var imported=groups.create(a.getId(),"Shared",p.getId()); groups.toggle(a.getId(),imported.getId(),shared.getId());
        String text=workspaces.exportWorkspace(a.getId()); assertTrue(text.startsWith(WorkspaceExchangeService.HEADER));
        Workspace copy=workspaces.importWorkspace(text);
        assertEquals(List.of("X","P"),groups.composition(copy.getId()).roots().stream().map(MavenProject::getDisplayName).toList());
        var restored=groups.composition(copy.getId()).roots(); assertFalse(restored.getFirst().isMaven()); assertFalse(restored.getLast().isEnabled());
        assertNotEquals(p.getId(),restored.getLast().getWorkspace().getId());
        assertEquals(1,groups.consumers(p.getId()).size());
        assertTrue(copy.getImportWarnings().stream().anyMatch(s->s.contains("existing display name")));
    }
    @Test void malformedBundleHasNoPartialPersistence() throws Exception {
        Workspace p=parent("Shared"), a=workspace("A"); add(p,"P",false); groups.create(a.getId(),"Shared",p.getId());
        String text=exchange.export(a.getId()).replace("\"parent\" : \"parent0\"", "\"parent\" : \"missing\"");
        long count=workspaceRepository.count(); assertThrows(IllegalArgumentException.class,()->exchange.read(text)); assertEquals(count,workspaceRepository.count());
        assertThrows(IllegalArgumentException.class,()->workspaces.importWorkspace("MvnBuilderWorkspace: 999\n{}"));
    }
    @Test void renderedPagesExposeGroupsAndPropagateValidatedContext() throws Exception {
        Workspace p=parent("Shared"), a=workspace("A"); var x=add(p,"X",false); var g=groups.create(a.getId(),"Shared libraries",p.getId());
        String html=mvc.perform(get("/workspaces/"+a.getId())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("Open parent workspace"));
        assertTrue(html.contains("data-imported=\"true\""));
        assertTrue(html.contains("workspace-group-end"));
        assertTrue(html.contains("End of Shared"));
        assertFalse(html.contains("Group label"));
        assertTrue(html.contains("Create new group")); assertTrue(html.contains("Add parent workspace"));
        assertTrue(html.contains("data-context-group-id=\""+g.getId()));
        assertTrue(html.contains("workspace-groups.js"));
        mvc.perform(get("/workspaces/"+a.getId()+"/groups/parent-workspaces-modal")).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Shared")));
        mvc.perform(post("/workspaces/"+a.getId()+"/groups/"+g.getId()+"/refresh").header("X-Group-Refresh","true"))
            .andExpect(status().isOk()).andExpect(content().string("Group refreshed."));
        mvc.perform(get("/parent-workspaces")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Parent workspaces")));
        mvc.perform(post("/projects/"+x.getId()+"/toggle-enabled").param("consumerId",a.getId().toString()).param("contextGroupId",g.getId().toString())).andExpect(status().isOk()).andExpect(content().string("false"));
        assertTrue(x.isEnabled());
        mvc.perform(post("/projects/"+x.getId()+"/delete").param("consumerId",a.getId().toString()).param("contextGroupId",g.getId().toString())).andExpect(status().isBadRequest());
        mvc.perform(get("/workspaces/"+a.getId()+"/project-table")).andExpect(status().isOk());
        mvc.perform(post("/workspaces/"+a.getId()+"/groups/"+g.getId()+"/action").param("action","fetch"))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.startsWith("No eligible Git repositories")));
        Files.writeString(Path.of("/tmp/mvnbuilder-groups-rendered.html"),html);
    }
    @Test void localGroupDialogSelectionAddsMoreProjectsAndCanDeleteThem() throws Exception {
        Workspace a=workspace("A"); var x=add(a,"X",false); var y=add(a,"Y",false); var z=add(a,"Z",false);
        var g=groups.createWithProjects(a.getId(),"Selected",List.of(x.getId(),y.getId()));
        assertEquals(List.of(x.getId(),y.getId()),g.getMemberIds());
        assertEquals(3,groups.localRoots(a.getId()).size());
        mvc.perform(get("/workspaces/"+a.getId()+"/groups/new-modal")).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Create group")));
        mvc.perform(get("/workspaces/"+a.getId()+"/groups/"+g.getId()+"/edit-modal")).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Add projects")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Z")));
        groups.addMembers(a.getId(),g.getId(),List.of(z.getId()));
        assertEquals(List.of(x.getId(),y.getId(),z.getId()),g.getMemberIds());
        assertThrows(ResponseStatusException.class,()->groups.createWithProjects(a.getId(),"Again",List.of(x.getId())));
        groups.remove(a.getId(),g.getId(),true);
        assertFalse(projects.existsById(x.getId())); assertFalse(projects.existsById(y.getId())); assertFalse(projects.existsById(z.getId()));
        assertEquals(0,projects.findByWorkspaceIdOrderByExecutionOrderAsc(a.getId()).size());
    }
    @Test void modalEndpointsCreateEditAndDeleteGroups() throws Exception {
        Workspace a=workspace("A"); var x=add(a,"X",false); var y=add(a,"Y",false); var z=add(a,"Z",false);
        mvc.perform(post("/workspaces/"+a.getId()+"/groups/create").param("name","From dialog")
                .param("projectIds",x.getId().toString(),y.getId().toString()))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/workspaces/"+a.getId()));
        var g=groups.list(a.getId()).getFirst();
        assertEquals("From dialog",g.getName()); assertEquals(List.of(x.getId(),y.getId()),g.getMemberIds());
        mvc.perform(post("/workspaces/"+a.getId()+"/groups/"+g.getId()+"/add-projects").param("projectIds",z.getId().toString()))
            .andExpect(status().is3xxRedirection());
        assertEquals(List.of(x.getId(),y.getId(),z.getId()),g.getMemberIds());
        mvc.perform(post("/workspaces/"+a.getId()+"/groups/"+g.getId()+"/delete").param("deleteProjects","true"))
            .andExpect(status().is3xxRedirection());
        assertEquals(0,groups.list(a.getId()).size());
        assertEquals(0,projects.findByWorkspaceIdOrderByExecutionOrderAsc(a.getId()).size());
    }
    @Test void parentDialogImportsAvailableParentWithoutInternalLabel() throws Exception {
        Workspace p=parent("Shared parent"), a=workspace("A"); add(p,"X",false);
        assertEquals(List.of(p.getId()),groups.availableParents(a.getId()).stream().map(Workspace::getId).toList());
        mvc.perform(post("/workspaces/"+a.getId()+"/groups/import-parent").param("parentId",p.getId().toString()))
            .andExpect(status().is3xxRedirection());
        var imported=groups.importedGroups(a.getId()).getFirst();
        assertEquals("Shared parent",imported.getName());
        assertTrue(groups.availableParents(a.getId()).isEmpty());
        mvc.perform(post("/workspaces/"+a.getId()+"/groups/"+imported.getId()+"/delete"))
            .andExpect(status().is3xxRedirection());
        assertTrue(groups.importedGroups(a.getId()).isEmpty());
    }
    @Test void importedParentUsesChildOrderNumbers() throws Exception {
        Workspace p=parent("Shared"), a=workspace("A"); var x=add(a,"X",false); var y=add(a,"Y",false);
        var px=add(p,"PX",false); var py=add(p,"PY",false);
        groups.create(a.getId(),"Shared",p.getId());
        var roots=groups.composition(a.getId()).roots();
        assertEquals(List.of(x.getId(),y.getId(),px.getId(),py.getId()),roots.stream().map(MavenProject::getId).toList());
        assertEquals(List.of(0,1,2,3),roots.stream().map(MavenProject::getExecutionOrder).toList());
        assertEquals(List.of(x.getId(),y.getId(),px.getId(),py.getId()),groups.buildOrder(a.getId()).stream().map(MavenProject::getId).toList());
    }
    @Test void buildAllOrderIsTheFlatProjectOrderNotTheVisualGroupOrder() throws Exception {
        Workspace a=workspace("A"); var x=add(a,"X",false); var y=add(a,"Y",false); var z=add(a,"Z",false);
        groups.createWithProjects(a.getId(),"Visual",List.of(z.getId(),x.getId()));
        y.setExecutionOrder(0); z.setExecutionOrder(1); x.setExecutionOrder(2); projects.saveAll(List.of(x,y,z));
        assertEquals(List.of(y.getId(),z.getId(),x.getId()),groups.buildOrder(a.getId()).stream().map(MavenProject::getId).toList());
        groups.reorderBlocks(a.getId(),groups.composition(a.getId()).blocks().stream().map(WorkspaceGroupService.Block::key).toList().reversed());
        assertEquals(List.of(y.getId(),z.getId(),x.getId()),groups.buildOrder(a.getId()).stream().map(MavenProject::getId).toList());
    }
    @Test void groupBuildUsesConsumerJavaAndBoundsRange() throws Exception {
        Workspace p=parent("Shared"), a=workspace("A"); var x=add(p,"X",false); add(p,"Y",false); var outside=add(a,"Outside",false);
        a.setJavaInstallation(installations.save(JavaInstallation.builder().withName("Consumer JDK").withJavaHome("/consumer/java").build()));
        var g=groups.create(a.getId(),"Imported",p.getId());
        builds.buildWorkspaceRange(a.getId(),x.getId(),BuildRangeMode.FROM);
        verify(processes,times(2)).executeCommandWithJavaHome(anyString(),any(),eq("/consumer/java"),any(String[].class));
        verify(processes,never()).executeCommandWithJavaHome(eq(outside.getDisplayName()),any(),any(),any(String[].class));
        assertTrue(history.findAll().stream().allMatch(r->a.getId().equals(r.getWorkspaceId())));
    }
    @Test void removedImportInvalidatesContinuationAndOrderChangesKeepCapturedTail() throws Exception {
        Workspace p=parent("Shared"), a=workspace("A"); var x=add(p,"X",false); add(p,"Y",false);
        var g=groups.create(a.getId(),"Imported",p.getId());
        when(processes.executeCommandWithJavaHome(eq("X"),any(),any(),any(String[].class))).thenReturn(CompletableFuture.completedFuture(new CommandResult(1,List.of(),1)));
        builds.buildProjectsSequentially(groups.scope(a.getId(),g.getId(),null,false));
        ArgumentCaptor<BuildFailure> failure=ArgumentCaptor.forClass(BuildFailure.class);
        verify(messages).convertAndSend(eq("/topic/build-failure"),failure.capture());
        groups.remove(a.getId(),g.getId());
        assertThrows(ResponseStatusException.class,()->builds.continueBuild(a.getId(),failure.getValue().continuationId(),"retry"));
        verify(processes,times(1)).executeCommandWithJavaHome(anyString(),any(),any(),any(String[].class));
    }
    @Test void gitDeduplicatesNestedProjectsAndKeepsWorktreesIndependent() throws Exception {
        Workspace a=workspace("A"); var x=add(a,"repo",false);
        new ProcessBuilder("git","init",x.getAbsolutePath()).start().waitFor();
        Path child=Files.createDirectories(Path.of(x.getAbsolutePath(),"module"));
        MavenProject y=MavenProject.builder().withId(999L).withWorkspace(a).withAbsolutePath(child.toString()).build();
        MavenProject first=WorkspaceGroupService.projectView(x,a.getId(),null), second=WorkspaceGroupService.projectView(y,a.getId(),null);
        assertEquals(1,BuildService.gitTargets(List.of(first,second)).size());
        Path worktree=base.resolve("worktree");
        assertEquals(0,new ProcessBuilder("git","-C",x.getAbsolutePath(),"-c","user.name=Group Tests","-c","user.email=groups@invalid.example","-c","commit.gpgsign=false","commit","--allow-empty","-m","fixture").start().waitFor());
        assertEquals(0,new ProcessBuilder("git","-C",x.getAbsolutePath(),"worktree","add","-b","group-worktree",worktree.toString()).start().waitFor());
        y.setAbsolutePath(worktree.toString()); assertEquals(2,BuildService.gitTargets(List.of(first,WorkspaceGroupService.projectView(y,a.getId(),null))).size());
        builds.bulkGitFetch(List.of(first,second)); verify(processes,times(1)).executeCommand(anyString(),any(),eq("git"),eq("fetch"));
    }
    @Test void modulesStayWithRootAndOnlyTheirOwnSource() throws Exception {
        Workspace parent=parent("Reactor"), consumer=workspace("Consumer");
        MavenProject root=add(parent,"reactor",false);
        Path module=Files.createDirectories(Path.of(root.getAbsolutePath(),"module"));
        Files.writeString(module.resolve("pom.xml"),"<project><modelVersion>4.0.0</modelVersion><groupId>test</groupId><artifactId>module</artifactId><version>1</version></project>");
        workspaces.addProjectByPath(parent.getId(),root.getAbsolutePath());
        var group=groups.create(consumer.getId(),"Reactor",parent.getId());
        var composed=groups.composition(consumer.getId());
        assertEquals(1,composed.roots().size()); assertEquals(2,composed.all().size());
        assertTrue(composed.all().stream().allMatch(p->group.getId().equals(p.getContextGroupId())));
        builds.buildWorkspaceRange(consumer.getId(),root.getId(),BuildRangeMode.FROM);
        verify(processes,times(1)).executeCommandWithJavaHome(eq("reactor"),any(),any(),any(String[].class));
        verify(processes,never()).executeCommandWithJavaHome(eq("module"),any(),any(),any(String[].class));
        Workspace copy=workspaces.duplicateWorkspace(parent.getId());
        assertTrue(copy.isParent()); assertTrue(copy.getBlocks().isEmpty());
    }
    @Test void dependencyConflictPreservesAllGroupsAndOrder() throws Exception {
        Workspace a=workspace("A"); var x=add(a,"X",false); var y=add(a,"Y",false); var z=add(a,"Z",false);
        var g=groups.create(a.getId(),"XZ",null); groups.move(a.getId(),x.getId(),g.getId()); groups.move(a.getId(),z.getId(),g.getId());
        x.getInternalDependencies().add("test:Y"); y.getInternalDependencies().add("test:Z");
        var before=groups.composition(a.getId()).blocks().stream().map(WorkspaceGroupService.Block::key).toList();
        assertThrows(ResponseStatusException.class,()->groups.calculateOrder(a.getId()));
        assertEquals(before,groups.composition(a.getId()).blocks().stream().map(WorkspaceGroupService.Block::key).toList());
    }
}
