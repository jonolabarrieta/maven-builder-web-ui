package net.olaba.mvnbuilder.service.git;

import net.olaba.mvnbuilder.entities.MavenProject;
import net.olaba.mvnbuilder.entities.Workspace;
import net.olaba.mvnbuilder.repository.MavenProjectRepository;
import net.olaba.mvnbuilder.service.GitService;
import org.springframework.stereotype.Service;
import static net.olaba.mvnbuilder.service.git.GitModels.*;
import java.io.File;
import java.nio.file.*;
import java.util.*;

/** Branch switching for Git view repositories, reusing the project reference selector's checkout rules. */
@Service
public class GitBranchService {
    private final GitService references;
    private final GitCommandRunner git;
    private final MavenProjectRepository projects;
    public GitBranchService(GitService references, GitCommandRunner git, MavenProjectRepository projects) {
        this.references = references; this.git = git; this.projects = projects;
    }
    public References list(Resolved repository) {
        File root = repository.root().toFile();
        String current;
        try { current = references.getCurrentReference(root).getLabel(); } catch (IllegalStateException e) { current = null; }
        return new References(choices(root, GitService.ReferenceType.LOCAL), choices(root, GitService.ReferenceType.REMOTE), choices(root, GitService.ReferenceType.TAG), current);
    }
    private List<ReferenceOption> choices(File root, GitService.ReferenceType type) {
        return references.listReferences(root, type).stream().map(choice -> new ReferenceOption(choice.type().name(), choice.ref(), choice.name())).toList();
    }
    /** Checks out a typed reference under the existing confirmation semantics, then refreshes cached project labels. */
    public String checkout(Workspace workspace, Resolved repository, String reference, String type) {
        if (reference == null || reference.isBlank() || type == null) throw new GitProblem(400, "Choose a branch or tag to check out.");
        try {
            String label = references.checkoutReference(repository.root().toFile(), reference, type).getLabel();
            updateProjects(workspace, repository, label); return label;
        } catch (GitService.ReferenceConflictException e) { throw new GitProblem(409, e.getMessage());
        } catch (IllegalArgumentException e) { throw new GitProblem(400, e.getMessage());
        } catch (IllegalStateException e) { throw new GitProblem(422, e.getMessage()); }
    }
    /** Creates a local branch from HEAD and checks it out; names must be valid and unused. */
    public String create(Workspace workspace, Resolved repository, String name) {
        if (name == null || name.isBlank() || name.startsWith("-") || git.run(repository.root(), "check-ref-format", "--branch", name).code() != 0)
            throw new GitProblem(400, "Enter a valid branch name, for example feature/login.");
        if (git.run(repository.root(), "show-ref", "--verify", "--quiet", "refs/heads/" + name).code() == 0) throw new GitProblem(409, "A branch named " + name + " already exists.");
        try { references.createBranch(repository.root().toFile(), name); }
        catch (IllegalStateException e) { throw new GitProblem(422, e.getMessage()); }
        updateProjects(workspace, repository, name); return name;
    }
    /** Projects whose nearest working-tree root is this repository show its branch on the Projects page. */
    private void updateProjects(Workspace workspace, Resolved repository, String label) {
        for (MavenProject project : projects.findByWorkspaceIdOrderByExecutionOrderAsc(workspace.getId())) {
            Path directory = directory(workspace, project);
            if (directory == null || !belongs(repository.root(), directory)) continue;
            project.setGitBranch(label); projects.save(project);
        }
    }
    private static Path directory(Workspace workspace, MavenProject project) {
        try {
            String path = project.getAbsolutePath() != null && !project.getAbsolutePath().isBlank() ? project.getAbsolutePath()
                    : project.getRelativePath() == null || workspace.getBasePath() == null ? null : Path.of(workspace.getBasePath(), project.getRelativePath()).toString();
            return path == null ? null : Path.of(path).toRealPath();
        } catch (Exception e) { return null; }
    }
    private static boolean belongs(Path root, Path directory) {
        if (!directory.startsWith(root)) return false;
        for (Path walk = directory; walk != null && !walk.equals(root); walk = walk.getParent())
            if (Files.exists(walk.resolve(".git"), LinkOption.NOFOLLOW_LINKS)) return false;
        return true;
    }
}
