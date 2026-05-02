package net.olaba.mvnbuilder.service;

import lombok.Getter;
import lombok.Setter;
import net.olaba.mvnbuilder.model.M2ProjectInfo;

import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service for managing the local Maven repository (~/.m2/repository).
 */
@Service
public class MavenRepositoryService {

    private final String m2RepoPath = System.getProperty("user.home") + "/.m2/repository";



    /**
     * Retrieves information about installed versions of a project in the M2 repository.
     * 
     * @param projectId  The project ID.
     * @param groupId    The group ID.
     * @param artifactId The artifact ID.
     * @return M2ProjectInfo object.
     */
    public M2ProjectInfo getProjectInfo(final Long projectId, final String groupId, final String artifactId) {
        final String path = groupId.replace(".", "/") + "/" + artifactId;
        final File projectDir = new File(m2RepoPath, path);
        
        final M2ProjectInfo info = new M2ProjectInfo();
        info.setProjectId(projectId);
        info.setGroupId(groupId);
        info.setArtifactId(artifactId);
        info.setAbsolutePath(projectDir.getAbsolutePath());
        
        if (projectDir.exists() && projectDir.isDirectory()) {
            final List<String> versions = new ArrayList<>();
            final File[] files = projectDir.listFiles();
            if (files != null) {
                for (final File file : files) {
                    if (file.isDirectory()) {
                        versions.add(file.getName());
                    }
                }
            }
            Collections.sort(versions, Collections.reverseOrder());
            info.setVersions(versions);
        } else {
            info.setVersions(Collections.emptyList());
        }
        
        return info;
    }

    /**
     * Deletes a specific version of a project from the M2 repository.
     * 
     * @param groupId    The group ID.
     * @param artifactId The artifact ID.
     * @param version    The version to delete.
     * @return True if deleted successfully.
     */
    public boolean deleteVersion(final String groupId, final String artifactId, final String version) {
        if (groupId == null || artifactId == null || version == null) {
            return false;
        }
        final String path = groupId.replace(".", "/") + "/" + artifactId + "/" + version;
        final File versionDir = new File(m2RepoPath, path);
        if (versionDir.exists()) {
            final boolean deleted = FileSystemUtils.deleteRecursively(versionDir);
            if (deleted) {
                cleanEmptyParentDirs(versionDir.getParentFile());
            }
            return deleted;
        }
        return false;
    }

    /**
     * Deletes all versions of a project from the M2 repository.
     * 
     * @param groupId    The group ID.
     * @param artifactId The artifact ID.
     * @return True if deleted successfully.
     */
    public boolean deleteProject(final String groupId, final String artifactId) {
        if (groupId == null || artifactId == null) {
            return false;
        }
        final String path = groupId.replace(".", "/") + "/" + artifactId;
        final File projectDir = new File(m2RepoPath, path);
        if (projectDir.exists()) {
            final boolean deleted = FileSystemUtils.deleteRecursively(projectDir);
            if (deleted) {
                cleanEmptyParentDirs(projectDir.getParentFile());
            }
            return deleted;
        }
        return false;
    }

    /**
     * Deletes all projects under a given group ID.
     * 
     * @param groupId The group ID to delete.
     * @return True if deleted successfully.
     */
    public boolean deleteByGroupId(final String groupId) {
        if (groupId == null || groupId.trim().isEmpty()) {
            return false;
        }
        final String path = groupId.replace(".", "/");
        final File groupDir = new File(m2RepoPath, path);
        if (groupDir.exists()) {
            final boolean deleted = FileSystemUtils.deleteRecursively(groupDir);
            if (deleted) {
                cleanEmptyParentDirs(groupDir.getParentFile());
            }
            return deleted;
        }
        return false;
    }

    /**
     * Retrieves the top-level folders in the M2 repository (usually group ID prefixes).
     * 
     * @return A sorted list of folder names.
     */
    public List<String> getM2TopLevelFolders() {
        final File repo = new File(m2RepoPath);
        if (!repo.exists() || !repo.isDirectory()) {
            return List.of();
        }
        
        final File[] files = repo.listFiles(File::isDirectory);
        if (files == null) {
            return List.of();
        }
        
        return java.util.Arrays.stream(files)
                .map(File::getName)
                .filter(name -> !name.startsWith(".")) // Ignore hidden folders like .cache
                .sorted()
                .toList();
    }

    /**
     * Recursively deletes empty parent directories up to the M2 repository root.
     * 
     * @param dir The directory to start cleaning from.
     */
    private void cleanEmptyParentDirs(final File dir) {
        final File repoBase = new File(m2RepoPath);
        File currentDir = dir;
        while (currentDir != null && !currentDir.equals(repoBase) && currentDir.exists() && currentDir.isDirectory()) {
            final String[] children = currentDir.list();
            if (children == null || children.length == 0) {
                currentDir.delete();
                currentDir = currentDir.getParentFile();
            } else {
                break;
            }
        }
    }
}
