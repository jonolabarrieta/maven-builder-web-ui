package net.olaba.mvnbuilder.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.olaba.mvnbuilder.entities.*;
import net.olaba.mvnbuilder.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

/** Portable version 2 configuration. File identities never refer to database identities. */
@Service @RequiredArgsConstructor @Transactional
public class WorkspaceExchangeService {
    public static final String HEADER = "MvnBuilderWorkspace: 2\n";
    private final ObjectMapper json;
    private final WorkspaceService workspaces;
    private final WorkspaceGroupService groups;
    private final MavenProjectRepository projects;
    private final WorkspaceProjectGroupRepository groupRepository;
    private final WorkspaceRepository workspaceRepository;
    private final RunEntryRepository runEntries;

    public record Project(String key, String path, boolean nonMaven, boolean enabled) { }
    public record Block(String kind, String name, String parent, List<String> members, Map<String, Boolean> overrides) { }
    public record Definition(String key, String name, String basePath, List<String> exclusions, boolean parent,
                             List<Project> projects, List<Block> blocks,
                             @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY) List<Run> runs) { }
    public record Env(String name, String value) { }
    /** A RUN entry; {@code project} is a file-local project key, qualified by {@code parent} for imported members. */
    public record Run(String name, String command, String workingDirectory, List<Env> env, boolean useWorkspaceJava,
                      String project, String parent) { }
    public record Bundle(int version, Definition workspace, List<Definition> parents) { }

    public String export(Long id) {
        Map<Long, String> parentKeys = new LinkedHashMap<>();
        for (var g : groups.list(id)) if (g.isImported()) parentKeys.putIfAbsent(g.getParentWorkspace().getId(), "parent" + parentKeys.size());
        List<Definition> parents = parentKeys.entrySet().stream().map(e -> definition(e.getKey(), e.getValue(), Map.of())).toList();
        try { return HEADER + json.writerWithDefaultPrettyPrinter().writeValueAsString(new Bundle(2, definition(id, "workspace", parentKeys), parents)) + "\n"; }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalArgumentException("Unable to export workspace.", e); }
    }
    private Map<Long, String> projectKeys(Long owner) {
        Map<Long, String> keys = new LinkedHashMap<>();
        for (var p : projects.findByWorkspaceIdOrderByExecutionOrderAsc(owner)) keys.put(p.getId(), "project" + keys.size());
        return keys;
    }
    private Definition definition(Long id, String key, Map<Long, String> parentKeys) {
        Workspace ws = groups.workspace(id); Map<Long, String> keys = projectKeys(id);
        List<Project> entries = projects.findByWorkspaceIdOrderByExecutionOrderAsc(id).stream()
            .map(p -> new Project(keys.get(p.getId()), path(p), !p.isMaven(), p.isEnabled())).toList();
        List<Block> blocks = new ArrayList<>();
        if (!ws.isParent()) for (var b : groups.composition(id).blocks()) {
            if (b.group() == null) blocks.add(new Block("PROJECT", null, null, List.of(keys.get(b.projects().getFirst().getId())), Map.of()));
            else if (!b.group().isImported()) blocks.add(new Block("LOCAL", b.group().getName(), null, b.projects().stream().map(p -> keys.get(p.getId())).toList(), Map.of()));
            else {
                var sourceKeys = projectKeys(b.group().getParentWorkspace().getId());
                Map<String, Boolean> overrides = new LinkedHashMap<>();
                b.group().getEnabledOverrides().forEach((pid, enabled) -> { if (sourceKeys.containsKey(pid)) overrides.put(sourceKeys.get(pid), enabled); });
                blocks.add(new Block("IMPORTED", b.group().getName(), parentKeys.get(b.group().getParentWorkspace().getId()), List.of(), overrides));
            }
        }
        return new Definition(key, ws.getName(), ws.getBasePath(), List.copyOf(ws.getExcludedPaths()), ws.isParent(), entries, blocks,
            ws.isParent() ? List.of() : runs(id, keys, parentKeys));
    }
    private List<Run> runs(Long id, Map<Long, String> ownKeys, Map<Long, String> parentKeys) {
        Map<Long, String[]> importedKeys = new HashMap<>();
        parentKeys.forEach((parentId, parentKey) -> projectKeys(parentId).forEach((projectId, projectKey) -> importedKeys.putIfAbsent(projectId, new String[] {parentKey, projectKey})));
        return runEntries.findByWorkspaceIdOrderByPositionAscIdAsc(id).stream().map(e -> {
            String project = null, parent = null;
            if (e.getProjectId() != null && ownKeys.containsKey(e.getProjectId())) project = ownKeys.get(e.getProjectId());
            else if (e.getProjectId() != null && importedKeys.containsKey(e.getProjectId())) { parent = importedKeys.get(e.getProjectId())[0]; project = importedKeys.get(e.getProjectId())[1]; }
            return new Run(e.getName(), e.getCommand(), e.getWorkingDirectory(), e.getEnv().stream().map(v -> new Env(v.getName(), v.getValue())).toList(),
                e.isUseWorkspaceJava(), project, parent);
        }).toList();
    }
    private String path(MavenProject p) {
        return p.getAbsolutePath() != null ? p.getAbsolutePath() : new java.io.File(p.getWorkspace().getBasePath(), p.getRelativePath()).getAbsolutePath();
    }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }
    private static boolean line(String value) { return value != null && !value.contains("\n") && !value.contains("\r"); }
    public Workspace read(String text) {
        check(text.startsWith(HEADER), "Unsupported workspace format version.");
        Bundle bundle;
        try { bundle = json.readValue(text.substring(HEADER.length()), Bundle.class); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid grouped workspace file. Check its JSON structure.", e); }
        check(bundle != null && bundle.version() == 2 && bundle.workspace() != null && bundle.parents() != null, "Invalid version 2 workspace bundle.");
        Map<String, Definition> definitions = new LinkedHashMap<>();
        for (Definition d : bundle.parents()) {
            check(d != null && d.parent() && line(d.key()) && !definitions.containsKey(d.key()), "Duplicate or invalid parent definition.");
            definitions.put(d.key(), d);
        }
        check(!definitions.containsKey(bundle.workspace().key()), "Duplicate file identity.");
        // Validate the entire graph before any persistence.
        for (Definition d : bundle.parents()) validate(d, definitions);
        validate(bundle.workspace(), definitions);
        Map<String, Workspace> restored = new HashMap<>(); Map<String, Map<String, Long>> ids = new HashMap<>();
        for (Definition d : bundle.parents()) restore(d, restored, ids);
        Workspace result = restore(bundle.workspace(), restored, ids);
        List<String> layout = new ArrayList<>();
        for (Block b : bundle.workspace().blocks()) {
            if (b.kind().equals("PROJECT")) {
                Long p = ids.get(bundle.workspace().key()).get(b.members().getFirst()); if (p != null) layout.add("p:" + p);
            } else {
                WorkspaceProjectGroup group = groups.create(result.getId(), b.name(), b.kind().equals("IMPORTED") ? restored.get(b.parent()).getId() : null);
                if (group.isImported()) b.overrides().forEach((key, enabled) -> { Long p = ids.get(b.parent()).get(key); if (p != null) group.getEnabledOverrides().put(p, enabled); });
                else group.setMemberIds(b.members().stream().map(ids.get(bundle.workspace().key())::get).filter(Objects::nonNull).collect(Collectors.toCollection(ArrayList::new)));
                groupRepository.save(group); layout.add("g:" + group.getId());
            }
        }
        result.setBlocks(layout); workspaceRepository.save(result);
        restoreRuns(bundle.workspace(), result, ids);
        for (Workspace parent : restored.values()) if (!parent.getId().equals(result.getId())) result.getImportWarnings().addAll(parent.getImportWarnings());
        return result;
    }
    private void restoreRuns(Definition d, Workspace owner, Map<String, Map<String, Long>> ids) {
        if (d.runs() == null) return;
        int position = 0;
        for (Run r : d.runs()) {
            RunEntry entry = new RunEntry();
            entry.setWorkspace(owner); entry.setPosition(position++); entry.setName(r.name().trim()); entry.setCommand(r.command());
            entry.setWorkingDirectory(r.workingDirectory()); entry.setUseWorkspaceJava(r.useWorkspaceJava());
            if (r.env() != null) r.env().forEach(v -> entry.getEnv().add(new RunEnvVar(v.name(), v.value() == null ? "" : v.value())));
            if (r.project() != null) {
                Long projectId = ids.getOrDefault(r.parent() == null ? d.key() : r.parent(), Map.of()).get(r.project());
                entry.setProjectId(projectId);
                if (projectId == null) owner.getImportWarnings().add("RUN entry \"" + r.name() + "\" was imported without its project link; it keeps its working directory.");
            }
            if (!new java.io.File(r.workingDirectory()).isDirectory())
                owner.getImportWarnings().add("RUN entry \"" + r.name() + "\" uses a working directory that does not exist: " + r.workingDirectory());
            runEntries.save(entry);
        }
    }
    private void validate(Definition d, Map<String, Definition> parents) {
        check(d != null && line(d.key()) && line(d.name()) && !d.name().isBlank() && d.name().length() <= 255 && (d.basePath() == null || line(d.basePath()))
            && d.projects() != null && d.blocks() != null && d.exclusions() != null, "Invalid workspace definition.");
        check(d.exclusions().stream().allMatch(WorkspaceExchangeService::line), "Invalid exclusion path.");
        if (d.runs() != null) {
            check(!d.parent() || d.runs().isEmpty(), "Parent definitions cannot contain RUN entries.");
            Set<String> names = new HashSet<>();
            for (Run r : d.runs()) {
                check(r != null && line(r.name()) && !r.name().isBlank() && r.name().trim().length() <= 255 && names.add(r.name().trim().toLowerCase(Locale.ROOT))
                    && r.command() != null && !r.command().isBlank() && r.command().length() <= 4000
                    && line(r.workingDirectory()) && !r.workingDirectory().isBlank() && r.workingDirectory().length() <= 2000
                    && (r.env() == null || r.env().stream().allMatch(v -> v != null && v.name() != null && v.name().matches("[A-Za-z_][A-Za-z0-9_.]*")))
                    && (r.parent() == null || parents.containsKey(r.parent())), "Invalid or duplicate RUN entry.");
            }
        }
        Set<String> keys = new HashSet<>(), paths = new HashSet<>();
        for (Project p : d.projects()) check(p != null && line(p.key()) && keys.add(p.key()) && line(p.path()) && new java.io.File(p.path()).isAbsolute() && paths.add(WorkspaceGroupService.canonical(p.path())), "Invalid or duplicate project identity/path.");
        Set<String> members = new HashSet<>(), imported = new HashSet<>();
        check(!d.parent() || d.blocks().isEmpty(), "Parent definitions cannot contain groups.");
        for (Block b : d.blocks()) {
            check(b != null && b.kind() != null && Set.of("PROJECT", "LOCAL", "IMPORTED").contains(b.kind()) && b.members() != null && b.overrides() != null, "Invalid block or nested group.");
            if (b.kind().equals("IMPORTED")) {
                check(parents.containsKey(b.parent()) && imported.add(b.parent()) && b.members().isEmpty(), "Missing or repeated parent reference.");
                Set<String> sourceKeys = parents.get(b.parent()).projects().stream().map(Project::key).collect(Collectors.toSet());
                check(sourceKeys.containsAll(b.overrides().keySet()) && b.overrides().values().stream().allMatch(Objects::nonNull), "Invalid enabled override.");
            } else {
                check(b.parent() == null && b.overrides().isEmpty() && (!b.kind().equals("PROJECT") || b.members().size() == 1), "Invalid project block.");
                for (String member : b.members()) check(keys.contains(member) && members.add(member), "Unknown or repeated group member.");
            }
            if (!b.kind().equals("PROJECT")) check(line(b.name()) && !b.name().isBlank() && b.name().length() <= 255, "Invalid group name.");
        }
        // Reject non-root grouping before persistence, using portable paths rather than database IDs.
        for (String key : members) {
            Project p = d.projects().stream().filter(v -> v.key().equals(key)).findFirst().orElseThrow();
            if (!p.nonMaven()) check(d.projects().stream().noneMatch(other -> other != p && !other.nonMaven()
                && java.nio.file.Path.of(WorkspaceGroupService.canonical(p.path())).startsWith(java.nio.file.Path.of(WorkspaceGroupService.canonical(other.path())))), "Only project roots can occupy blocks; modules follow their root.");
        }
    }
    private Workspace restore(Definition d, Map<String, Workspace> restored, Map<String, Map<String, Long>> ids) {
        StringBuilder legacy = new StringBuilder("Workspace: ").append(d.name()).append('\n');
        if (d.basePath() != null) legacy.append("BasePath: ").append(d.basePath()).append('\n');
        d.exclusions().forEach(e -> legacy.append("Exclude: ").append(e).append('\n'));
        for (Project p : d.projects()) legacy.append(p.nonMaven() ? "NonMavenProject: " : "").append(p.path()).append('\n');
        Workspace ws = workspaces.importWorkspace(legacy.toString()); ws.setKind(d.parent() ? WorkspaceKind.PARENT : WorkspaceKind.NORMAL);
        workspaceRepository.save(ws);
        var existing = projects.findByWorkspaceIdOrderByExecutionOrderAsc(ws.getId()).stream().collect(Collectors.toMap(p -> WorkspaceGroupService.canonical(p.getAbsolutePath()), p -> p));
        Map<String, Long> mapping = new HashMap<>();
        for (Project p : d.projects()) {
            MavenProject saved = existing.get(WorkspaceGroupService.canonical(p.path()));
            if (saved != null) { saved.setEnabled(p.enabled()); mapping.put(p.key(), saved.getId()); }
        }
        if (d.parent() && groups.parents().stream().anyMatch(p -> !p.getId().equals(ws.getId()) && p.getName().equals(ws.getName())))
            ws.getImportWarnings().add("Created a separate parent with an existing display name: " + ws.getName());
        restored.put(d.key(), ws); ids.put(d.key(), mapping); return ws;
    }
}
