package net.olaba.mvnbuilder.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisabledOnOs(OS.WINDOWS)
class RunProcessRegistryTest {
    @TempDir Path directory;
    SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
    List<RunProcessRegistry.RunMessage> messages = new CopyOnWriteArrayList<>();
    List<String> destinations = new CopyOnWriteArrayList<>();
    RunProcessRegistry registry;

    {
        doAnswer(i -> { destinations.add(i.getArgument(0)); messages.add(i.getArgument(1)); return null; })
            .when(messaging).convertAndSend(anyString(), any(Object.class));
        registry = new RunProcessRegistry(messaging, Duration.ofSeconds(2));
    }

    @AfterEach
    void cleanup() { registry.close(); }

    RunConfigurationService.LaunchSpec spec(long entry, String command) {
        return spec(entry, command, Map.of());
    }

    RunConfigurationService.LaunchSpec spec(long entry, String command, Map<String, String> env) {
        return new RunConfigurationService.LaunchSpec(1L, entry, "entry" + entry, directory.toString(), command, env, null);
    }

    static void await(BooleanSupplier condition) throws InterruptedException {
        for (int i = 0; i < 200 && !condition.getAsBoolean(); i++) Thread.sleep(25);
        assertTrue(condition.getAsBoolean(), "condition not reached");
    }

    RunProcessRegistry.Snapshot snapshot(long entry) {
        return registry.snapshot(1L).stream().filter(s -> s.entryId() == entry).findFirst().orElseThrow();
    }

    @Test
    void cleanExitIsReportedAsCrashedWithExitCode() throws Exception {
        assertTrue(registry.start(spec(1, "echo hi")));
        await(() -> snapshot(1).state() == RunProcessRegistry.State.CRASHED);
        assertEquals(0, snapshot(1).exitCode());
        assertTrue(snapshot(1).lines().stream().anyMatch(l -> l.content().equals("hi")));
        assertTrue(destinations.stream().allMatch(d -> d.equals("/topic/run/1")));
    }

    @Test
    void failingExitReportsCode() throws Exception {
        registry.start(spec(1, "exit 3"));
        await(() -> snapshot(1).state() == RunProcessRegistry.State.CRASHED);
        assertEquals(3, snapshot(1).exitCode());
    }

    @Test
    void secondStartOfARunningEntryIsANoOp() throws Exception {
        assertTrue(registry.start(spec(1, "sleep 30")));
        long pid = snapshot(1).pid();
        assertFalse(registry.start(spec(1, "sleep 30")));
        assertEquals(pid, snapshot(1).pid());
        assertEquals(Set.of(1L), registry.runningEntries(1L));
    }

    @Test
    void environmentAndShellFeaturesAreAvailable() throws Exception {
        registry.start(spec(1, "echo \"$SERVER_PORT\" && echo piped | tr a-z A-Z", Map.of("SERVER_PORT", "8081")));
        await(() -> snapshot(1).state() == RunProcessRegistry.State.CRASHED);
        List<String> lines = snapshot(1).lines().stream().map(RunProcessRegistry.Line::content).toList();
        assertTrue(lines.contains("8081"));
        assertTrue(lines.contains("PIPED"));
    }

    @Test
    void bufferKeepsTheMostRecentLinesWithContiguousSequence() throws Exception {
        registry.start(spec(1, "i=1; while [ $i -le 2500 ]; do echo line$i; i=$((i+1)); done"));
        await(() -> snapshot(1).state() == RunProcessRegistry.State.CRASHED);
        var lines = snapshot(1).lines();
        assertEquals(RunProcessRegistry.BUFFER_LINES, lines.size());
        for (int i = 1; i < lines.size(); i++) assertEquals(lines.get(i - 1).seq() + 1, lines.get(i).seq());
        assertEquals(snapshot(1).lastSeq(), lines.getLast().seq());
        assertTrue(lines.stream().anyMatch(l -> l.content().equals("line2500")));
        assertTrue(lines.stream().noneMatch(l -> l.content().equals("line1")));
    }

    @Test
    void ansiSequencesAreStripped() throws Exception {
        registry.start(spec(1, "printf '\\033[31mred\\033[0m\\n'"));
        await(() -> snapshot(1).state() == RunProcessRegistry.State.CRASHED);
        assertTrue(snapshot(1).lines().stream().anyMatch(l -> l.content().equals("red")));
    }

    @Test
    void stopKillsTheTreeAndReportsStopped() throws Exception {
        registry.start(spec(1, "sleep 300 & sleep 300 & wait"));
        ProcessHandle handle = ProcessHandle.of(snapshot(1).pid()).orElseThrow();
        await(() -> handle.descendants().count() >= 2);
        List<ProcessHandle> children = handle.descendants().toList();
        registry.stop(1L, 1L);
        assertEquals(RunProcessRegistry.State.STOPPED, snapshot(1).state());
        for (ProcessHandle child : children) await(() -> !child.isAlive());
        assertFalse(handle.isAlive());
    }

    @Test
    void restartUsesTheNewConfigurationAndKeepsOtherEntries() throws Exception {
        registry.start(spec(1, "echo old; sleep 30"));
        registry.start(spec(2, "sleep 30"));
        long other = snapshot(2).pid();
        registry.restart(spec(1, "echo new; sleep 30"));
        await(() -> snapshot(1).lines().stream().anyMatch(l -> l.content().equals("new")));
        assertTrue(snapshot(1).lines().stream().noneMatch(l -> l.content().equals("old")));
        assertEquals(RunProcessRegistry.State.RUNNING, snapshot(1).state());
        assertEquals(other, snapshot(2).pid());
        assertEquals(RunProcessRegistry.State.RUNNING, snapshot(2).state());
        assertTrue(messages.stream().anyMatch(m -> m.type().equals("state") && m.started() && m.entryId() == 1L));
    }

    @Test
    void missingDirectoryStartsNothingAndNamesTheEntry() {
        var missing = new RunConfigurationService.LaunchSpec(1L, 9L, "api", directory.resolve("nope").toString(), "echo x", Map.of(), null);
        var error = assertThrows(RunProcessRegistry.StartException.class, () -> registry.start(missing));
        assertTrue(error.getMessage().contains("api"));
        assertTrue(error.getMessage().contains("nope"));
        assertTrue(registry.snapshot(1L).isEmpty());
    }

    @Test
    void dismissIsRejectedWhileRunningAndRemovesStoppedLogs() throws Exception {
        registry.start(spec(1, "sleep 30"));
        assertThrows(IllegalStateException.class, () -> registry.dismiss(1L, 1L));
        registry.stop(1L, 1L);
        registry.dismiss(1L, 1L);
        assertTrue(registry.snapshot(1L).isEmpty());
    }

    @Test
    void closeTerminatesEveryProcessTree() throws Exception {
        registry.start(spec(1, "sleep 300 & wait"));
        registry.start(spec(2, "sleep 300"));
        ProcessHandle first = ProcessHandle.of(snapshot(1).pid()).orElseThrow();
        await(() -> first.descendants().count() >= 1);
        List<ProcessHandle> all = new ArrayList<>(first.descendants().toList());
        all.add(first);
        all.add(ProcessHandle.of(snapshot(2).pid()).orElseThrow());
        registry.close();
        for (ProcessHandle h : all) await(() -> !h.isAlive());
        registry.close();
    }

    @Test
    void stoppingABuildLeavesRunEntriesAlive() throws Exception {
        registry.start(spec(1, "sleep 30"));
        new ProcessExecutionService(messaging).killCurrentProcess();
        assertTrue(registry.isRunning(1L, 1L));
        assertTrue(destinations.stream().noneMatch(d -> d.equals("/topic/process")));
    }
}
