package net.olaba.mvnbuilder.service;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for navigating the local file system.
 */
@Service
public class FileSystemService {

    /**
     * Represents an item (file or directory) in the file system.
     */
    @Getter
    @Setter
    @Builder(setterPrefix = "with")
    public static class FileItem {
        private String name;
        private String path;
        private boolean isDirectory;
        private boolean isMavenProject;
    }

    /**
     * Lists the contents of a directory.
     * 
     * @param path The absolute path to list.
     * @return A list of FileItem objects.
     */
    public List<FileItem> listDirectory(final String path) {
        File dir = new File(path);
        if (!dir.exists() || !dir.isDirectory()) {
            // If path doesn't exist, try user home as fallback
            dir = new File(System.getProperty("user.home"));
        }

        final File[] files = dir.listFiles();
        List<FileItem> items = new ArrayList<>();
        
        if (files != null) {
            items = Arrays.stream(files)
                    .filter(f -> !f.getName().startsWith("."))
                    .map(f -> FileItem.builder()
                            .withName(f.getName())
                            .withPath(f.getAbsolutePath())
                            .withIsDirectory(f.isDirectory())
                            .withIsMavenProject(isMavenProject(f))
                            .build())
                    .sorted(Comparator.comparing(FileItem::isDirectory).reversed()
                            .thenComparing(FileItem::getName))
                    .collect(Collectors.toList());
        }
        
        return items;
    }

    /**
     * Checks if a directory contains a Maven project (pom.xml).
     * 
     * @param dir The directory to check.
     * @return True if it's a Maven project.
     */
    private boolean isMavenProject(final File dir) {
        if (!dir.isDirectory()) {
            return false;
        }
        return new File(dir, "pom.xml").exists();
    }
}
