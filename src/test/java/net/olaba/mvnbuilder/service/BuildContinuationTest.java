package net.olaba.mvnbuilder.service;

import net.olaba.mvnbuilder.entities.*;
import net.olaba.mvnbuilder.model.*;
import net.olaba.mvnbuilder.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BuildContinuationTest {
    @TempDir Path base;
    ProcessExecutionService processes=mock(ProcessExecutionService.class);
    WorkspaceService workspaces=mock(WorkspaceService.class);
    BuildProfileRepository profiles=mock(BuildProfileRepository.class);
    SimpMessagingTemplate messages=mock(SimpMessagingTemplate.class);
    SystemSettingService settings=mock(SystemSettingService.class);
    NotificationService notifications=mock(NotificationService.class);
    BuildRunRepository runs=mock(BuildRunRepository.class);
    BuildHistoryService history=new BuildHistoryService(runs,mock(BuildRunProjectRepository.class));
    BuildService service=new BuildService(processes,workspaces,profiles,messages,settings,notifications,history);
    Workspace workspace;
    List<MavenProject> projects=new ArrayList<>();
    List<BuildFailure> failures=new ArrayList<>();
    List<BuildRun> recorded=new ArrayList<>();
    List<String> executed=new ArrayList<>();
    List<List<String>> commands=new ArrayList<>();
    AtomicReference<String> fail=new AtomicReference<>("B");
    @BeforeEach void setup() throws Exception {
        workspace=Workspace.builder().withId(1L).withName("Fixture").withBasePath(base.toString()).build();
        for(String name:List.of("A","B","C","D")) {
            Path directory=Files.createDirectory(base.resolve(name)); Files.writeString(directory.resolve("pom.xml"),"<project/>");
            projects.add(MavenProject.builder().withId((long)projects.size()+1).withArtifactId(name).withAbsolutePath(directory.toString()).withWorkspace(workspace).build());
        }
        when(workspaces.getProjectsForWorkspace(1L,false)).thenAnswer(i->List.copyOf(projects));
        when(profiles.findByIsDefaultTrue()).thenReturn(Optional.of(BuildProfile.builder().withCommand("-B validate").build()));
        when(runs.save(any())).thenAnswer(i->{BuildRun r=i.getArgument(0);recorded.add(r);return r;});
        doAnswer(i->{failures.add(i.getArgument(0));return null;}).when(notifications).publishBuildFailure(any(),any(),any());
        when(processes.executeCommandWithJavaHome(anyString(),any(),any(),any(String[].class))).thenAnswer(i->{
            String name=i.getArgument(0); executed.add(name); commands.add(List.of((String[])i.getRawArguments()[3]));
            return CompletableFuture.completedFuture(new CommandResult(name.equals(fail.get())?1:0,List.of(),10));
        });
    }
    BuildFailure stopped() { service.buildProjectsSequentially(List.copyOf(projects)); return failures.getLast(); }
    @Test void skipContinuesOriginalTailAndLeavesFailureHistoryAndEnabledState() {
        BuildFailure failure=stopped(); Collections.reverse(projects); fail.set(null);
        when(profiles.findByIsDefaultTrue()).thenReturn(Optional.of(BuildProfile.builder().withCommand("clean install").build()));
        assertEquals("accepted",service.continueBuild(1L,failure.continuationId(),"skip").state());
        assertEquals(List.of("A","B","C","D"),executed); assertTrue(projects.stream().allMatch(MavenProject::isEnabled));
        assertFalse(recorded.getFirst().isSuccess()); assertTrue(recorded.getLast().isSuccess());
        assertEquals(List.of("C","D"),recorded.getLast().getProjects().stream().map(BuildRunProject::getArtifactId).toList());
        assertTrue(commands.stream().allMatch(command->command.equals(List.of("mvn","-B","validate"))));
        verify(notifications).publish(argThat(summary->summary.getSkippedProjects().contains("B") && summary.isSuccess()),eq(1L),eq("Fixture"));
        assertThrows(ResponseStatusException.class,()->service.continueBuild(1L,failure.continuationId(),"retry"));
    }
    @Test void retryStartsAtFailureAndRetainsCapturedJavaHome() {
        workspace.setJavaInstallation(JavaInstallation.builder().withJavaHome("/original/jdk").build()); BuildFailure failure=stopped();
        workspace.getJavaInstallation().setJavaHome("/changed/jdk"); fail.set(null); service.continueBuild(1L,failure.continuationId(),"retry");
        assertEquals(List.of("A","B","B","C","D"),executed);
        verify(processes,times(5)).executeCommandWithJavaHome(anyString(),any(),eq("/original/jdk"),any(String[].class));
    }
    @Test void rejectsOtherWorkspaceAndChangedQueueWithoutConsumingValidAction() {
        BuildFailure failure=stopped();
        assertThrows(ResponseStatusException.class,()->service.continueBuild(2L,failure.continuationId(),"skip"));
        projects.getLast().setProjectKind(ProjectKind.FOLDER);
        assertThrows(ResponseStatusException.class,()->service.continueBuild(1L,failure.continuationId(),"skip"));
        assertEquals(List.of("A","B"),executed); projects.getLast().setProjectKind(ProjectKind.MAVEN); fail.set(null);
        service.continueBuild(1L,failure.continuationId(),"skip"); assertEquals(List.of("A","B","C","D"),executed);
    }
    @Test void finalSkipStartsNoEmptyRunAndLaterFailureGetsIndependentTail() {
        BuildFailure first=stopped(); fail.set("C"); service.continueBuild(1L,first.continuationId(),"skip");
        BuildFailure second=failures.getLast(); assertNotEquals(first.continuationId(),second.continuationId());
        assertEquals(List.of(3L,4L),second.remainingProjectIds()); fail.set("D"); service.continueBuild(1L,second.continuationId(),"skip");
        int count=recorded.size(); assertEquals("empty",service.continueBuild(1L,failures.getLast().continuationId(),"skip").state());
        assertEquals(count,recorded.size()); assertEquals(List.of("A","B","C","D"),executed);
    }
    @Test void canceledActiveRunCannotPublishUsableRecoveryAndPendingActionIsInvalidated() {
        BuildFailure failure=stopped(); service.cancelContinuations();
        assertThrows(ResponseStatusException.class,()->service.continueBuild(1L,failure.continuationId(),"retry"));
        CompletableFuture<CommandResult> pending=new CompletableFuture<>();
        when(processes.executeCommandWithJavaHome(anyString(),any(),any(),any(String[].class))).thenReturn(pending);
        service.buildProjectsSequentially(List.copyOf(projects)); service.cancelContinuations(); pending.complete(new CommandResult(1,List.of(),10));
        assertNull(failures.getLast().continuationId()); assertFalse(recorded.getLast().isSuccess());
    }
    @Test void mixedBuildSkipsFoldersAndFolderOnlyBuildCreatesNoHistory() {
        projects.get(0).setProjectKind(ProjectKind.FOLDER); projects.get(2).setEnabled(false); fail.set(null);
        service.buildProjectsSequentially(List.copyOf(projects)); assertEquals(List.of("B","D"),executed);
        int count=recorded.size(); service.buildProjectsSequentially(List.of(projects.getFirst())); assertEquals(count,recorded.size());
        assertThrows(IllegalArgumentException.class,()->service.buildProject(projects.getFirst()));
    }
    @Test void registryExpiresEvictsAndAcceptsOnlyOnceAcrossConcurrentRequests() throws Exception {
        AtomicReference<Instant> now=new AtomicReference<>(Instant.parse("2026-10-24T00:00:00Z"));
        Clock clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now.get();}};
        var registry=new BuildContinuationRegistry(clock,2);
        var plan=new BuildContinuationRegistry.Plan(1L,"run",List.of(new BuildContinuationRegistry.Target(1L,"/tmp")),List.of("mvn"),null,List.of());
        String old=registry.register(plan); registry.register(plan); String current=registry.register(plan);
        assertThrows(ResponseStatusException.class,()->registry.accept(1L,old,"retry",p->{}));
        AtomicInteger accepted=new AtomicInteger();
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> calls=new ArrayList<>();
            for(int i=0;i<8;i++)calls.add(executor.submit(()->{try{registry.accept(1L,current,"skip",p->{});accepted.incrementAndGet();}catch(ResponseStatusException expected){}}));
            for(Future<?> call:calls)call.get();
        }
        assertEquals(1,accepted.get()); String expiring=registry.register(plan); now.set(now.get().plusSeconds(3601));
        assertThrows(ResponseStatusException.class,()->registry.accept(1L,expiring,"retry",p->{}));
    }
}
