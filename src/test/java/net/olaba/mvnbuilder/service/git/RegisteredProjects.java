package net.olaba.mvnbuilder.service.git;

import net.olaba.mvnbuilder.entities.Workspace;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Test registry standing in for the workspace's project list. */
public class RegisteredProjects implements WorkspaceGitProjects {
    private final List<ProjectFolder> folders = new CopyOnWriteArrayList<>();
    private long next = 1;
    public synchronized Long add(Path folder) { long id = next++; folders.add(new ProjectFolder(id, folder.getFileName().toString(), folder)); return id; }
    public void remove(Long id) { folders.removeIf(folder -> folder.id().equals(id)); }
    @Override public List<ProjectFolder> folders(Workspace workspace) { return List.copyOf(folders); }
}
