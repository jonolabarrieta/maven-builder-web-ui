package net.olaba.mvnbuilder.service;

import net.olaba.mvnbuilder.entities.MavenProject;
import net.olaba.mvnbuilder.entities.Workspace;
import net.olaba.mvnbuilder.repository.BuildProfileRepository;
import net.olaba.mvnbuilder.repository.MavenProjectRepository;
import net.olaba.mvnbuilder.repository.WorkspaceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The Git phase reads each repository once, in parallel, with a shared bound. */
class WorkspaceGitStatusRefreshTest {
    @TempDir Path base;
    private final WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
    private final MavenProjectRepository projects = mock(MavenProjectRepository.class);
    private final GitService git = mock(GitService.class);
    private final Map<Long, List<MavenProject>> stored = new HashMap<>();
    private final AtomicLong ids = new AtomicLong(1);
    private WorkspaceService service = service(6);

    @AfterEach
    void close() { service.closeBranchReaders(); }

    private WorkspaceService service(final int workers) {
        final WorkspaceService created = new WorkspaceService(workspaces, projects, new MavenService(), git,
                mock(BuildProfileRepository.class));
        ReflectionTestUtils.setField(created, "branchWorkerCount", workers);
        created.configureBranchReaders();
        when(projects.findByWorkspaceIdOrderByExecutionOrderAsc(any()))
                .thenAnswer(call -> stored.getOrDefault(call.<Long>getArgument(0), List.of()));
        return created;
    }

    private Workspace workspace(final long id) {
        final Workspace workspace = Workspace.builder().withId(id).withBasePath(base.toString()).build();
        when(workspaces.findById(id)).thenReturn(Optional.of(workspace));
        return workspace;
    }

    private MavenProject folder(final Workspace workspace, final Path directory) throws Exception {
        Files.createDirectories(directory);
        final MavenProject project = MavenProject.builder().withId(ids.getAndIncrement())
                .withRelativePath(base.relativize(directory).toString()).withAbsolutePath(directory.toString())
                .withWorkspace(workspace).withProjectKind(net.olaba.mvnbuilder.entities.ProjectKind.FOLDER).build();
        stored.computeIfAbsent(workspace.getId(), key -> new ArrayList<>()).add(project);
        return project;
    }

    private Path repository(final String name) throws Exception {
        final Path root = base.resolve(name);
        Files.createDirectories(root.resolve(".git"));
        return root;
    }

    @Test
    void configuredSizeIsAppliedAndZeroIsClampedToOne() {
        assertEquals(6, service.branchReaderLimit());
        final WorkspaceService two = service(2), none = service(0);
        try {
            assertEquals(2, two.branchReaderLimit());
            assertEquals(1, none.branchReaderLimit());
        } finally {
            two.closeBranchReaders();
            none.closeBranchReaders();
        }
    }

    @Test
    void sharedRepositoryIsReadOnceAcrossWorkspaces() throws Exception {
        final Path repo = repository("mono");
        final Workspace consumer = workspace(1), parent = workspace(2);
        final List<MavenProject> rows = new ArrayList<>();
        for (int i = 0; i < 3; i++) rows.add(folder(consumer, repo.resolve("c" + i)));
        for (int i = 0; i < 2; i++) rows.add(folder(parent, repo.resolve("p" + i)));
        when(git.getCurrentBranch(repo.toFile())).thenReturn("develop");

        final List<MavenProject> result = service.refreshGitStatus(List.of(1L, 2L, 1L));

        verify(git, times(1)).getCurrentBranch(any(File.class));
        assertEquals(5, result.size());
        rows.forEach(row -> assertEquals("develop", row.getGitBranch()));
        verify(projects, times(1)).saveAll(any());
    }

    @Test
    void projectOutsideAnyRepositoryRunsNoGit() throws Exception {
        final MavenProject plain = folder(workspace(1), base.resolve("plain"));
        assumeNoEnclosingRepository(base);

        service.refreshGitStatus(1L);

        verifyNoInteractions(git);
        assertEquals("n/a", plain.getGitBranch());
    }

    @Test
    void oneFailingRepositoryLeavesTheOthersCorrect() throws Exception {
        final Workspace workspace = workspace(1);
        final Path good = repository("good"), bad = repository("bad");
        final MavenProject ok = folder(workspace, good), broken = folder(workspace, bad);
        when(git.getCurrentBranch(good.toFile())).thenReturn("main");
        when(git.getCurrentBranch(bad.toFile())).thenThrow(new IllegalStateException("boom"));

        service.refreshGitStatus(1L);

        assertEquals("main", ok.getGitBranch());
        assertEquals("n/a", broken.getGitBranch());
    }

    @Test
    void concurrentReadsNeverExceedTheLimitAndBeatSequentialTime() throws Exception {
        final Workspace workspace = workspace(1);
        for (int i = 0; i < 20; i++) folder(workspace, repository("repo" + i));
        final AtomicInteger running = new AtomicInteger(), peak = new AtomicInteger();
        when(git.getCurrentBranch(any(File.class))).thenAnswer(call -> {
            peak.accumulateAndGet(running.incrementAndGet(), Math::max);
            try {
                Thread.sleep(100);
            } finally {
                running.decrementAndGet();
            }
            return "main";
        });

        final long start = System.nanoTime();
        service.refreshGitStatus(1L);
        final long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        verify(git, times(20)).getCurrentBranch(any(File.class));
        assertTrue(peak.get() <= 6, "peak " + peak.get());
        assertTrue(peak.get() > 1, "reads ran in parallel");
        assertTrue(elapsedMs < 20 * 100, "took " + elapsedMs + " ms");
    }

    /** findGitRoot walks up; a temp directory inside a checkout would make every row a repository. */
    private static void assumeNoEnclosingRepository(final Path directory) {
        for (Path current = directory; current != null; current = current.getParent()) {
            org.junit.jupiter.api.Assumptions.assumeFalse(Files.exists(current.resolve(".git")),
                    "temporary directory lives inside a Git checkout");
        }
    }
}
