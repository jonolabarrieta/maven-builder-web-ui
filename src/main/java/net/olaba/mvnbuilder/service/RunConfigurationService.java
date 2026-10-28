package net.olaba.mvnbuilder.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.olaba.mvnbuilder.entities.MavenProject;
import net.olaba.mvnbuilder.entities.RunEntry;
import net.olaba.mvnbuilder.entities.RunEnvVar;
import net.olaba.mvnbuilder.entities.Workspace;
import net.olaba.mvnbuilder.repository.RunEntryRepository;
import net.olaba.mvnbuilder.repository.WorkspaceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.util.*;

/** Persists and validates the RUN entries of a workspace. */
@Service
@RequiredArgsConstructor
@Transactional
public class RunConfigurationService {
    private final RunEntryRepository entries;
    private final WorkspaceRepository workspaces;
    private final WorkspaceGroupService groups;
    private final SystemSettingService settings;

    /** Submitted entry fields. Environment names and values are parallel lists. */
    public record Form(String name, Long projectId, String workingDirectory, String command,
                       List<String> envNames, List<String> envValues, boolean useWorkspaceJava) { }

    /** A selectable project for an entry. */
    public record ProjectOption(Long id, String name, String directory, boolean imported) { }

    /** Everything needed to start an entry, detached from the persistence context. */
    public record LaunchSpec(Long workspaceId, Long entryId, String name, String workingDirectory, String command,
                             Map<String, String> env, String javaHome) { }

    /** An entry with its resolved project link. */
    public record EntryView(RunEntry entry, String projectName, boolean unlinked) { }

    /** Field-level validation failure. */
    @Getter
    public static class ValidationException extends RuntimeException {
        private final Map<String, String> errors;
        public ValidationException(Map<String, String> errors) {
            super(String.join(" ", errors.values()));
            this.errors = Map.copyOf(errors);
        }
    }

    @Transactional(readOnly = true)
    public List<RunEntry> list(Long workspaceId) {
        return entries.findByWorkspaceIdOrderByPositionAscIdAsc(workspaceId);
    }

    @Transactional(readOnly = true)
    public List<EntryView> views(Long workspaceId) {
        Map<Long, ProjectOption> options = optionsById(workspaceId);
        return list(workspaceId).stream().map(e -> {
            ProjectOption option = e.getProjectId() == null ? null : options.get(e.getProjectId());
            return new EntryView(e, option == null ? null : option.name(), e.getProjectId() != null && option == null);
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<ProjectOption> projectOptions(Long workspaceId) {
        Set<Long> seen = new HashSet<>();
        return groups.composition(workspaceId).all().stream().filter(p -> seen.add(p.getId()))
            .map(p -> new ProjectOption(p.getId(), p.getDisplayName(), directory(p), p.isImported())).toList();
    }

    /** @return the entry when it belongs to the workspace */
    @Transactional(readOnly = true)
    public RunEntry entry(Long workspaceId, Long entryId) {
        return entries.findById(entryId).filter(e -> e.getWorkspace().getId().equals(workspaceId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "RUN entry does not belong to this workspace."));
    }

    /** @return the saved configuration of an entry, ready to launch */
    @Transactional(readOnly = true)
    public LaunchSpec launchSpec(Long workspaceId, Long entryId) {
        RunEntry entry = entry(workspaceId, entryId);
        Map<String, String> env = new LinkedHashMap<>();
        entry.getEnv().forEach(v -> env.put(v.getName(), v.getValue() == null ? "" : v.getValue()));
        String javaHome = null;
        if (entry.isUseWorkspaceJava()) {
            Workspace workspace = entry.getWorkspace();
            javaHome = workspace.getJavaInstallation() != null ? workspace.getJavaInstallation().getJavaHome()
                : settings.getDefaultJavaInstallation().map(net.olaba.mvnbuilder.entities.JavaInstallation::getJavaHome).orElse(null);
        }
        return new LaunchSpec(workspaceId, entry.getId(), entry.getName(), entry.getWorkingDirectory(), entry.getCommand(), env, javaHome);
    }

    public RunEntry create(Long workspaceId, Form form) {
        Workspace workspace = editableWorkspace(workspaceId);
        RunEntry entry = new RunEntry();
        entry.setWorkspace(workspace);
        entry.setPosition(list(workspaceId).stream().mapToInt(RunEntry::getPosition).max().orElse(-1) + 1);
        apply(workspaceId, entry, form);
        return entries.save(entry);
    }

    public RunEntry update(Long workspaceId, Long entryId, Form form) {
        editableWorkspace(workspaceId);
        RunEntry entry = entry(workspaceId, entryId);
        apply(workspaceId, entry, form);
        return entries.save(entry);
    }

    public RunEntry duplicate(Long workspaceId, Long entryId) {
        editableWorkspace(workspaceId);
        RunEntry source = entry(workspaceId, entryId);
        Set<String> names = new HashSet<>(list(workspaceId).stream().map(RunEntry::getName).toList());
        String name = source.getName() + " (copy)";
        for (int i = 2; names.contains(name); i++) name = source.getName() + " (copy " + i + ")";
        RunEntry copy = new RunEntry();
        copy.setWorkspace(source.getWorkspace());
        copy.setName(name);
        copy.setProjectId(source.getProjectId());
        copy.setWorkingDirectory(source.getWorkingDirectory());
        copy.setCommand(source.getCommand());
        copy.setUseWorkspaceJava(source.isUseWorkspaceJava());
        source.getEnv().forEach(v -> copy.getEnv().add(new RunEnvVar(v.getName(), v.getValue())));
        List<RunEntry> ordered = new ArrayList<>(list(workspaceId));
        ordered.add(ordered.indexOf(source) + 1, copy);
        entries.save(copy);
        renumber(ordered);
        return copy;
    }

    public void delete(Long workspaceId, Long entryId) {
        entries.delete(entry(workspaceId, entryId));
    }

    public void reorder(Long workspaceId, List<Long> ids) {
        List<RunEntry> current = list(workspaceId);
        Map<Long, RunEntry> byId = new LinkedHashMap<>();
        current.forEach(e -> byId.put(e.getId(), e));
        if (ids == null || ids.size() != current.size() || !byId.keySet().containsAll(ids) || new HashSet<>(ids).size() != ids.size())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The order must list every RUN entry of this workspace once.");
        renumber(ids.stream().map(byId::get).toList());
    }

    /** Stores the last launch selection. */
    public void markSelected(Long workspaceId, Collection<Long> ids) {
        for (RunEntry e : list(workspaceId)) e.setLastSelected(ids.contains(e.getId()));
    }

    private void renumber(List<RunEntry> ordered) {
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).setPosition(i);
        entries.saveAll(ordered);
    }

    private Workspace editableWorkspace(Long workspaceId) {
        Workspace workspace = workspaces.findById(workspaceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace does not exist."));
        if (workspace.isParent()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parent workspaces cannot have RUN entries.");
        return workspace;
    }

    private void apply(Long workspaceId, RunEntry entry, Form form) {
        Map<String, String> errors = new LinkedHashMap<>();
        String name = trim(form.name());
        if (name == null) errors.put("name", "Enter a name.");
        else if (name.length() > 255 || name.contains("\n")) errors.put("name", "Use a name of up to 255 characters on one line.");
        else if (list(workspaceId).stream().anyMatch(e -> !e.getId().equals(entry.getId()) && e.getName().equalsIgnoreCase(name)))
            errors.put("name", "Another RUN entry already uses this name.");
        String command = trim(form.command());
        if (command == null) errors.put("command", "Enter a command.");
        else if (command.length() > 4000) errors.put("command", "The command is too long.");
        String directory = trim(form.workingDirectory());
        if (form.projectId() != null) {
            ProjectOption option = optionsById(workspaceId).get(form.projectId());
            if (option == null && !Objects.equals(form.projectId(), entry.getProjectId())) errors.put("projectId", "Choose a project of this workspace.");
            else if (directory == null && option != null) directory = option.directory();
        }
        if (directory == null) errors.put("workingDirectory", "Choose a project or enter a working directory.");
        else if (directory.length() > 2000) errors.put("workingDirectory", "The directory path is too long.");
        List<RunEnvVar> env = new ArrayList<>();
        List<String> names = form.envNames() == null ? List.of() : form.envNames();
        List<String> values = form.envValues() == null ? List.of() : form.envValues();
        for (int i = 0; i < names.size(); i++) {
            String key = trim(names.get(i));
            String value = i < values.size() && values.get(i) != null ? values.get(i) : "";
            if (key == null) { if (!value.isEmpty()) errors.put("env", "Every environment value needs a name."); continue; }
            if (!key.matches("[A-Za-z_][A-Za-z0-9_.]*")) errors.put("env", "Invalid environment variable name: " + key);
            env.add(new RunEnvVar(key, value));
        }
        if (!errors.isEmpty()) throw new ValidationException(errors);
        entry.setName(name);
        entry.setCommand(command);
        entry.setProjectId(form.projectId());
        entry.setWorkingDirectory(directory);
        entry.setUseWorkspaceJava(form.useWorkspaceJava());
        entry.getEnv().clear();
        entry.getEnv().addAll(env);
    }

    private Map<Long, ProjectOption> optionsById(Long workspaceId) {
        Map<Long, ProjectOption> options = new LinkedHashMap<>();
        for (ProjectOption o : projectOptions(workspaceId)) options.putIfAbsent(o.id(), o);
        return options;
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** @return the absolute directory of a project registration */
    public static String directory(MavenProject p) {
        if (p.getAbsolutePath() != null) return p.getAbsolutePath();
        String base = p.getWorkspace() == null ? null : p.getWorkspace().getBasePath();
        return base == null ? p.getRelativePath() : new File(base, p.getRelativePath() == null ? "" : p.getRelativePath()).getAbsolutePath();
    }
}
