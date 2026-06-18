package net.olaba.mvnbuilder.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Service for interacting with Git repositories on the file system.
 */
@Service
@Slf4j
public class GitService {

    /**
     * Retrieves the name of the current Git branch for a given directory.
     * 
     * @param projectDir The directory to check.
     * @return The current branch name, or "n/a" if an error occurs.
     */
    public String getCurrentBranch(final File projectDir) {
        try {
            final ProcessBuilder pb = new ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD");
            pb.directory(projectDir);
            final Process process = pb.start();
            
            try (final BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                final String branch = reader.readLine();
                process.waitFor(5, TimeUnit.SECONDS);
                if (process.exitValue() == 0) {
                    return branch != null ? branch.trim() : "unknown";
                }
                log.warn("Git command exited with code {} for directory {}", process.exitValue(), projectDir);
            }
        } catch (final Exception e) {
            log.error("Failed to get git branch for directory {}: {}", projectDir, e.getMessage());
        }
        return "n/a";
    }

    /**
     * Lists all local branches for a given directory.
     * 
     * @param projectDir The directory to check.
     * @return A list of branch names.
     */
    public List<String> listBranches(final File projectDir) {
        final List<String> branches = new ArrayList<>();
        try {
            final ProcessBuilder pb = new ProcessBuilder("git", "branch", "--format=%(refname:short)");
            pb.directory(projectDir);
            final Process process = pb.start();
            
            try (final BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    branches.add(line.trim());
                }
                process.waitFor(5, TimeUnit.SECONDS);
            }
        } catch (final Exception e) {
            log.error("Failed to list git branches for directory {}: {}", projectDir, e.getMessage());
        }
        return branches;
    }

    /**
     * Lists all remote branches for a given directory.
     * 
     * @param projectDir The directory to check.
     * @return A list of remote branch names.
     */
    public List<String> listRemoteBranches(final File projectDir) {
        final List<String> branches = new ArrayList<>();
        try {
            final ProcessBuilder pb = new ProcessBuilder("git", "branch", "-r", "--format=%(refname:short)");
            pb.directory(projectDir);
            final Process process = pb.start();
            
            try (final BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    final String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.contains("->")) {
                        branches.add(trimmed);
                    }
                }
                process.waitFor(5, TimeUnit.SECONDS);
            }
        } catch (final Exception e) {
            log.error("Failed to list remote git branches for directory {}: {}", projectDir, e.getMessage());
        }
        return branches;
    }

    /**
     * Lists all tags for a given directory, sorted by version descending.
     * 
     * @param projectDir The directory to check.
     * @return A list of tag names.
     */
    public List<String> listTags(final File projectDir) {
        final List<String> tags = new ArrayList<>();
        try {
            final ProcessBuilder pb = new ProcessBuilder("git", "tag", "--sort=-v:refname", "--format=%(refname:short)");
            pb.directory(projectDir);
            final Process process = pb.start();
            
            try (final BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    final String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        tags.add(trimmed);
                    }
                }
                process.waitFor(5, TimeUnit.SECONDS);
            }
        } catch (final Exception e) {
            log.error("Failed to list git tags for directory {}: {}", projectDir, e.getMessage());
        }
        return tags;
    }

    /**
     * Checks out an existing branch.
     * 
     * @param projectDir The repository directory.
     * @param branchName The branch to checkout.
     * @return True if successful.
     */
    public boolean checkoutBranch(final File projectDir, final String branchName) {
        return executeGitCommand(projectDir, "checkout", branchName);
    }

    /**
     * Creates and checks out a new branch.
     * 
     * @param projectDir The repository directory.
     * @param branchName The branch to create.
     * @return True if successful.
     */
    public boolean createBranch(final File projectDir, final String branchName) {
        return executeGitCommand(projectDir, "checkout", "-b", branchName);
    }

    /**
     * Executes git fetch synchronously.
     * 
     * @param projectDir The repository directory.
     * @return True if successful.
     */
    public boolean fetch(final File projectDir) {
        return executeGitCommand(projectDir, "fetch");
    }

    /**
     * Executes a generic Git command.
     * 
     * @param projectDir The repository directory.
     * @param args       The Git command arguments.
     * @return True if the command finished with exit code 0.
     */
    private boolean executeGitCommand(final File projectDir, final String... args) {
        try {
            final List<String> command = new ArrayList<>();
            command.add("git");
            for (final String arg : args) {
                command.add(arg);
            }
            
            log.info("Executing git command: {} in {}", command, projectDir);
            final ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(projectDir);
            pb.environment().put("GIT_TERMINAL_PROMPT", "0");
            pb.redirectErrorStream(true);
            final Process process = pb.start();
            
            final StringBuilder output = new StringBuilder();
            final CompletableFuture<Void> readFuture = CompletableFuture.runAsync(() -> {
                try (final BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                } catch (final Exception e) {
                    log.error("Error reading git output: {}", e.getMessage());
                }
            });
            
            final boolean finished = process.waitFor(15, TimeUnit.SECONDS);
            if (!finished) {
                log.error("Git command timed out: {}", command);
                process.destroyForcibly();
                throw new RuntimeException("Git command timed out: " + String.join(" ", command));
            }
            
            // Wait a bit for the output reader to finish
            try {
                readFuture.get(2, TimeUnit.SECONDS);
            } catch (final Exception e) {
                log.warn("Timeout waiting for output reader to finish");
            }
            
            final int exitCode = process.exitValue();
            if (exitCode != 0) {
                final String errMsg = output.toString().trim();
                log.error("Git command failed: {}. Exit code: {}. Output: {}", 
                        command, exitCode, errMsg);
                throw new RuntimeException(errMsg.isEmpty() ? "Git command failed with exit code " + exitCode : errMsg);
            }
            
            log.info("Git command successful: {}", command);
            return true;
        } catch (final Exception e) {
            log.error("Exception during git command: {}", e.getMessage(), e);
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException("Failed to execute git: " + e.getMessage(), e);
        }
    }
}
