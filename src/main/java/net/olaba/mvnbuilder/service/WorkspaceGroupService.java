package net.olaba.mvnbuilder.service;

import net.olaba.mvnbuilder.entities.*;
import net.olaba.mvnbuilder.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/** Resolves consumer membership separately from persistent project ownership. */
@Service
@RequiredArgsConstructor
@Transactional
public class WorkspaceGroupService {
    private final WorkspaceRepository workspaces;
    private final MavenProjectRepository projects;
    private final WorkspaceProjectGroupRepository groups;
    private final WorkspaceService workspaceService;

    public record Block(String key, WorkspaceProjectGroup group, List<MavenProject> projects) { }
    public record Composition(List<Block> blocks, List<MavenProject> roots, List<MavenProject> all) { }

    public Workspace workspace(Long id) {
        return workspaces.findById(id).orElseThrow(() -> error("Workspace does not exist."));
    }
    private static ResponseStatusException error(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
    private static String name(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 255 || value.contains("\n") || value.contains("\r")) throw error("Enter a name of 1 to 255 characters.");
        return value.trim();
    }
    public List<Workspace> parents() { return workspaces.findAll().stream().filter(Workspace::isParent).toList(); }
    public List<Workspace> consumers(Long parentId) {
        return groups.findByParentWorkspaceId(parentId).stream().map(WorkspaceProjectGroup::getWorkspace).toList();
    }
    public Workspace createParent(String label, String basePath, boolean skipScan) {
        Workspace value = workspaceService.createWorkspace(name(label), basePath, skipScan);
        value.setKind(WorkspaceKind.PARENT);
        return workspaces.save(value);
    }
    public WorkspaceProjectGroup group(Long workspaceId, Long groupId) {
        WorkspaceProjectGroup group = groups.findById(groupId).orElseThrow(() -> error("Group no longer exists."));
        if (!group.getWorkspace().getId().equals(workspaceId)) throw error("Group does not belong to this workspace.");
        return group;
    }
    public List<WorkspaceProjectGroup> list(Long id) { return groups.findByWorkspaceIdOrderByIdAsc(id); }
    public List<WorkspaceProjectGroup> importedGroups(Long id) {
        return list(id).stream().filter(WorkspaceProjectGroup::isImported).toList();
    }
    /** Parent workspaces that can still be added to this child workspace. */
    public List<Workspace> availableParents(Long id) {
        consumer(id);
        Set<Long> linked = importedGroups(id).stream().map(g -> g.getParentWorkspace().getId()).collect(Collectors.toSet());
        return parents().stream().filter(parent -> !linked.contains(parent.getId())).toList();
    }
    /** Local project roots available for grouping. */
    @Transactional(readOnly = true)
    public List<MavenProject> localRoots(Long id) {
        return composition(id).roots().stream().filter(p -> !p.isImported()).toList();
    }
    /** Build All order is the flat project order; visual groups never move targets. */
    @Transactional(readOnly = true)
    public List<MavenProject> buildOrder(Long id) {
        Comparator<MavenProject> byOrder = Comparator
            .comparingInt((MavenProject p) -> p.getExecutionOrder() == null ? Integer.MAX_VALUE : p.getExecutionOrder())
            .thenComparing(MavenProject::isImported)
            .thenComparing(p -> p.getWorkspace().getId(), Comparator.nullsLast(Long::compareTo))
            .thenComparing(p -> p.getId(), Comparator.nullsLast(Long::compareTo));
        return composition(id).roots().stream().sorted(byOrder).toList();
    }
    private Workspace consumer(Long id) {
        Workspace value = workspace(id);
        if (value.isParent()) throw error("Parent workspaces cannot contain groups or imports.");
        return value;
    }
    public WorkspaceProjectGroup create(Long id, String label, Long parentId) {
        if (parentId == null) return addGroup(id, label, null, List.of());
        Workspace parent = workspace(parentId);
        if (id.equals(parentId) || !parent.isParent()) throw error("Choose a parent workspace.");
        if (list(id).stream().anyMatch(g -> g.isImported() && parentId.equals(g.getParentWorkspace().getId())))
            throw error("This parent is already added to this workspace.");
        return addGroup(id, label == null || label.isBlank() ? parent.getName() : label, parent, List.of());
    }
    /** Creates a visual group from one or more existing workspace project roots. */
    public WorkspaceProjectGroup createWithProjects(Long id, String label, List<Long> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) throw error("Select at least one project for the group.");
        return addGroup(id, label, null, projectIds);
    }
    private WorkspaceProjectGroup addGroup(Long id, String label, Workspace parent, List<Long> requestedIds) {
        Workspace owner = consumer(id);
        Composition value = composition(id);
        Map<Long, MavenProject> rootsById = value.roots().stream().filter(p -> !p.isImported())
            .collect(Collectors.toMap(MavenProject::getId, p -> p));
        List<Long> memberIds = new ArrayList<>();
        for (Long projectId : requestedIds) {
            if (projectId == null || memberIds.contains(projectId)) throw error("Each project can be added once.");
            MavenProject root = rootsById.get(projectId);
            if (root == null) throw error("Only project roots from this workspace can be grouped.");
            if (root.getContextGroupId() != null) throw error("A project can belong to only one group.");
            memberIds.add(projectId);
        }
        // The group header replaces the first selected project visually. Internally the
        // project execution order is never changed by grouping.
        List<String> layout = new ArrayList<>(value.blocks().stream().map(Block::key).toList());
        int insertion = Integer.MAX_VALUE;
        for (int i = 0; i < layout.size(); i++) {
            String key = layout.get(i);
            if (!key.startsWith("p:")) continue;
            Long projectId = Long.valueOf(key.substring(2));
            if (!memberIds.contains(projectId)) continue;
            insertion = Math.min(insertion, i);
            layout.remove(i--);
        }
        WorkspaceProjectGroup group = new WorkspaceProjectGroup();
        group.setWorkspace(owner); group.setName(name(label)); group.setParentWorkspace(parent);
        group.setMemberIds(memberIds);
        group = groups.save(group);
        if (insertion == Integer.MAX_VALUE) insertion = layout.size();
        layout.add(Math.min(insertion, layout.size()), "g:" + group.getId());
        owner.setBlocks(layout); workspaces.save(owner); return group;
    }
    /** Adds roots to an existing local group without changing their execution order. */
    public void addMembers(Long id, Long groupId, List<Long> projectIds) {
        WorkspaceProjectGroup group = group(id, groupId);
        if (group.isImported()) throw error("Edit imported project membership in the parent workspace.");
        if (projectIds == null || projectIds.isEmpty()) throw error("Select at least one project to add.");
        Map<Long, MavenProject> rootsById = composition(id).roots().stream().filter(p -> !p.isImported())
            .collect(Collectors.toMap(MavenProject::getId, p -> p));
        Set<Long> current = new HashSet<>(group.getMemberIds());
        for (Long projectId : projectIds) {
            if (projectId == null) continue;
            if (current.contains(projectId)) continue;
            MavenProject root = rootsById.get(projectId);
            if (root == null) throw error("Only project roots from this workspace can be grouped.");
            if (root.getContextGroupId() != null) throw error("A project can belong to only one group.");
            group.getMemberIds().add(projectId); current.add(projectId);
        }
    }
    public void rename(Long id, Long groupId, String label) { group(id, groupId).setName(name(label)); }
    public void remove(Long id, Long groupId) { remove(id, groupId, false); }
    /**
     * Removes a group. Local projects can either stay ungrouped or be removed from the
     * workspace; imported parents always stop contributing their projects to the child.
     */
    public void remove(Long id, Long groupId, boolean deleteProjects) {
        WorkspaceProjectGroup group = group(id, groupId);
        if (!group.isImported() && deleteProjects) {
            for (Long projectId : new ArrayList<>(group.getMemberIds())) {
                MavenProject project = projects.findById(projectId).orElse(null);
                if (project != null && id.equals(project.getWorkspace().getId())) workspaceService.deleteProject(projectId);
            }
            group = group(id, groupId);
        }
        List<String> layout = new ArrayList<>();
        for (Block block : composition(id).blocks()) {
            if (block.key().equals("g:" + groupId)) {
                if (!group.isImported()) block.projects().forEach(p -> layout.add("p:" + p.getId()));
            } else layout.add(block.key());
        }
        Workspace owner = workspace(id); owner.setBlocks(layout); workspaces.save(owner); groups.delete(group);
    }
    public void move(Long id, Long projectId, Long targetGroupId) {
        Workspace owner = consumer(id);
        Composition value = composition(id);
        MavenProject root = value.roots().stream().filter(p -> p.getId().equals(projectId) && !p.isImported()).findFirst()
            .orElseThrow(() -> error("Move an owned project root; its modules follow it."));
        WorkspaceProjectGroup target = targetGroupId == null ? null : group(id, targetGroupId);
        if (target != null && target.isImported()) throw error("Edit imported project membership in the parent workspace.");
        List<String> layout = new ArrayList<>(value.blocks().stream().map(Block::key).toList());
        for (WorkspaceProjectGroup g : list(id)) g.getMemberIds().remove(projectId);
        layout.remove("p:" + root.getId());
        if (target == null) layout.add("p:" + root.getId()); else target.getMemberIds().add(projectId);
        owner.setBlocks(layout); workspaces.save(owner);
    }
    public void reorderBlocks(Long id, List<String> keys) {
        Workspace owner = consumer(id);
        List<String> current = composition(id).blocks().stream().map(Block::key).toList();
        if (keys.size() != current.size() || new HashSet<>(keys).size() != keys.size() || !new HashSet<>(keys).equals(new HashSet<>(current)))
            throw error("The workspace changed. Refresh before reordering.");
        owner.setBlocks(new ArrayList<>(keys)); workspaces.save(owner);
    }
    public void reorderMembers(Long id, Long groupId, List<Long> ids) {
        WorkspaceProjectGroup group = group(id, groupId);
        if (group.isImported()) throw error("Reorder imported projects in their parent workspace.");
        Set<Long> current = composition(id).blocks().stream().filter(b -> b.key().equals("g:" + groupId))
            .flatMap(b -> b.projects().stream()).map(MavenProject::getId).collect(Collectors.toSet());
        if (ids.size() != current.size() || new HashSet<>(ids).size() != ids.size() || !current.equals(new HashSet<>(ids)))
            throw error("The group changed. Refresh before reordering.");
        group.setMemberIds(new ArrayList<>(ids));
    }
    @Transactional(readOnly = true)
    public Composition composition(Long id) {
        Workspace owner = workspace(id);
        List<WorkspaceProjectGroup> allGroups = list(id);
        Set<Long> sourceIds = new HashSet<>(); sourceIds.add(id);
        allGroups.stream().filter(WorkspaceProjectGroup::isImported).forEach(g -> sourceIds.add(g.getParentWorkspace().getId()));
        Map<Long, List<MavenProject>> byOwner = projects.findByWorkspaceIdInOrderByExecutionOrderAsc(sourceIds).stream()
            .collect(Collectors.groupingBy(p -> p.getWorkspace().getId()));
        List<MavenProject> own = byOwner.getOrDefault(id, List.of());
        List<MavenProject> ownRoots = roots(own);
        Map<Long, MavenProject> rootById = ownRoots.stream().collect(Collectors.toMap(MavenProject::getId, p -> p));
        Map<String, Block> pending = new LinkedHashMap<>();
        Set<Long> grouped = new HashSet<>();
        for (WorkspaceProjectGroup g : allGroups) {
            List<MavenProject> members;
            if (g.isImported()) members = roots(byOwner.getOrDefault(g.getParentWorkspace().getId(), List.of()));
            else {
                members = g.getMemberIds().stream().filter(rootById::containsKey).filter(grouped::add).map(rootById::get).toList();
            }
            pending.put("g:" + g.getId(), new Block("g:" + g.getId(), g, members.stream().map(p -> projectView(p, id, g)).toList()));
        }
        // Legacy roots preserve their executionOrder without requiring a destructive data migration.
        Map<String, Block> blocks = new LinkedHashMap<>();
        for (MavenProject p : ownRoots) if (!grouped.contains(p.getId())) {
            String key = "p:" + p.getId(); blocks.put(key, new Block(key, null, List.of(projectView(p, id, null))));
        }
        blocks.putAll(pending);
        List<Block> ordered = new ArrayList<>();
        for (String key : owner.getBlocks()) { Block b = blocks.remove(key); if (b != null) ordered.add(b); }
        ordered.addAll(blocks.values());
        // Imported parents keep their own execution order in their source workspace.
        // The child needs its own visible/build order, so imported roots are numbered
        // after the child's local roots while preserving their relative parent order.
        int nextImportedOrder = ordered.stream().flatMap(b -> b.projects().stream())
            .filter(p -> !p.isImported()).map(MavenProject::getExecutionOrder)
            .filter(Objects::nonNull).max(Integer::compare).orElse(-1) + 1;
        for (Block block : ordered) {
            if (block.group() == null || !block.group().isImported()) continue;
            for (MavenProject member : block.projects()) member.setExecutionOrder(nextImportedOrder++);
        }
        List<MavenProject> flat = ordered.stream().flatMap(b -> b.projects().stream()).toList();
        Map<Long, Map<String, MavenProject>> rootPaths = new HashMap<>();
        for (MavenProject root : flat) if (root.isMaven() && root.getRelativePath() != null)
            rootPaths.computeIfAbsent(root.getWorkspace().getId(), unused -> new HashMap<>()).put(normalized(root), root);
        Map<Long, List<MavenProject>> descendants = new HashMap<>();
        for (var source : byOwner.entrySet()) for (MavenProject child : source.getValue()) {
            if (!child.isMaven() || child.getRelativePath() == null) continue;
            Map<String, MavenProject> paths = rootPaths.getOrDefault(source.getKey(), Map.of());
            String path = normalized(child); MavenProject root = paths.get(path);
            if (root != null && root.getId().equals(child.getId())) continue;
            while (root == null && path.contains("/")) { path = path.substring(0, path.lastIndexOf('/')); root = paths.get(path); }
            if (root == null) root = paths.get(".");
            if (root != null) descendants.computeIfAbsent(root.getId(), unused -> new ArrayList<>()).add(child);
        }
        List<MavenProject> all = new ArrayList<>();
        for (Block block : ordered) for (MavenProject root : block.projects()) {
            all.add(root);
            for (MavenProject child : descendants.getOrDefault(root.getId(), List.of())) all.add(projectView(child, id, block.group()));
        }
        return new Composition(List.copyOf(ordered), flat, List.copyOf(all));
    }
    public static boolean descendant(MavenProject child, MavenProject parent) {
        if (!child.isMaven() || !parent.isMaven() || Objects.equals(child.getId(), parent.getId())
            || !Objects.equals(child.getWorkspace().getId(), parent.getWorkspace().getId())) return false;
        String root = parent.getRelativePath(), path = child.getRelativePath();
        if (root == null || path == null) return false;
        root = root.replace('\\', '/'); path = path.replace('\\', '/');
        return root.equals(".") || path.startsWith(root + "/");
    }
    private static String normalized(MavenProject project) { return project.getRelativePath().replace('\\', '/'); }
    public static List<MavenProject> roots(List<MavenProject> all) {
        Map<Long, Set<String>> paths = new HashMap<>();
        for (MavenProject p : all) if (p.isMaven() && p.getRelativePath() != null)
            paths.computeIfAbsent(p.getWorkspace().getId(), unused -> new HashSet<>()).add(normalized(p));
        return all.stream().filter(p -> {
            if (!p.isMaven() || p.getRelativePath() == null) return true;
            Set<String> owned = paths.get(p.getWorkspace().getId()); String path = normalized(p);
            if (!path.equals(".") && owned.contains(".")) return false;
            for (int slash=path.indexOf('/');slash>=0;slash=path.indexOf('/',slash+1))
                if (owned.contains(path.substring(0,slash))) return false;
            return true;
        }).toList();
    }
    public void synchronizeUngroupedOrder(Long id) {
        if (!list(id).isEmpty()) return;
        Workspace owner = workspace(id);
        owner.setBlocks(owner.isParent() ? new ArrayList<>() : roots(projects.findByWorkspaceIdOrderByExecutionOrderAsc(id)).stream()
            .map(p -> "p:"+p.getId()).collect(Collectors.toCollection(ArrayList::new)));
        workspaces.save(owner);
    }
    public static MavenProject projectView(MavenProject p, Long consumerId, WorkspaceProjectGroup group) {
        MavenProject view = MavenProject.builder().withId(p.getId()).withWorkspace(p.getWorkspace())
            .withProjectKind(p.getProjectKind()).withArtifactId(p.getArtifactId()).withGroupId(p.getGroupId())
            .withVersion(p.getVersion()).withRelativePath(p.getRelativePath()).withAbsolutePath(p.getAbsolutePath())
            .withGitBranch(p.getGitBranch()).withExecutionOrder(p.getExecutionOrder()).withParentKey(p.getParentKey())
            .withEnabled(group != null && group.isImported() ? group.getEnabledOverrides().getOrDefault(p.getId(), p.isEnabled()) : p.isEnabled())
            .withModules(new ArrayList<>(p.getModules())).withInternalDependencies(new ArrayList<>(p.getInternalDependencies())).build();
        view.setConsumerWorkspaceId(consumerId); view.setContextGroupId(group == null ? null : group.getId());
        view.setImported(group != null && group.isImported());
        return view;
    }
    @Transactional(readOnly = true)
    public List<MavenProject> scope(Long id, Long groupId, List<Long> selected, boolean modules) {
        if (groupId != null) group(id, groupId);
        Composition c = composition(id);
        List<MavenProject> candidates = (modules ? c.all() : c.roots()).stream()
            .filter(p -> groupId == null || Objects.equals(groupId, p.getContextGroupId())).toList();
        if (selected == null) return candidates;
        Set<Long> allowed = candidates.stream().map(MavenProject::getId).collect(Collectors.toSet());
        if (!allowed.containsAll(selected)) throw error("Every project must belong to the requested workspace and group. Refresh the project list.");
        return candidates.stream().filter(p -> selected.contains(p.getId())).toList();
    }
    public MavenProject project(Long id, Long groupId, Long projectId) {
        return scope(id, groupId, List.of(projectId), true).getFirst();
    }
    public boolean toggle(Long id, Long groupId, Long projectId) {
        MavenProject p = project(id, groupId, projectId);
        setEnabled(id, groupId, List.of(projectId), !p.isEnabled()); return !p.isEnabled();
    }
    public void setEnabled(Long id, Long groupId, List<Long> selected, boolean enabled) {
        for (MavenProject p : scope(id, groupId, selected, true)) {
            if (p.isImported()) group(id, p.getContextGroupId()).getEnabledOverrides().put(p.getId(), enabled);
            else projects.findById(p.getId()).orElseThrow().setEnabled(enabled);
        }
    }
    public void refresh(Long id, Long groupId) {
        WorkspaceProjectGroup g = group(id, groupId);
        if (!g.isImported()) workspaceService.refreshWorkspace(id);
        List<MavenProject> targets = scope(id, groupId, null, true);
        // Git metadata is source-owned; consumer refresh never changes source membership/order.
        for (MavenProject p : targets) if (p.isAvailable()) {
            MavenProject source = projects.findById(p.getId()).orElseThrow();
            source.setGitBranch(workspaceService.currentBranch(new File(p.getAbsolutePath())));
        }
    }
    public void projectRemoved(Long sourceId, List<Long> ids) {
        List<WorkspaceProjectGroup> affected = new ArrayList<>(list(sourceId));
        affected.addAll(groups.findByParentWorkspaceId(sourceId));
        for (WorkspaceProjectGroup g : affected) {
            g.getMemberIds().removeAll(ids);
            ids.forEach(g.getEnabledOverrides()::remove);
        }
    }
    public void beforeDelete(Long id) {
        List<Workspace> users = consumers(id);
        if (!users.isEmpty()) throw error("Remove this parent from its consumers first: " + users.stream().map(Workspace::getName).collect(Collectors.joining(", ")));
        groups.deleteAll(list(id)); groups.flush();
    }
    public void copyLayout(Long originalId, Workspace copy, Map<Long, Long> projectIds) {
        if (copy.isParent()) return;
        Map<String, String> keys = new HashMap<>();
        for (WorkspaceProjectGroup original : list(originalId)) {
            WorkspaceProjectGroup g = new WorkspaceProjectGroup(); g.setWorkspace(copy); g.setName(original.getName());
            g.setParentWorkspace(original.getParentWorkspace());
            g.setMemberIds(original.getMemberIds().stream().map(projectIds::get).filter(Objects::nonNull).collect(Collectors.toCollection(ArrayList::new)));
            g.getEnabledOverrides().putAll(original.getEnabledOverrides()); groups.save(g);
            keys.put("g:" + original.getId(), "g:" + g.getId());
        }
        projectIds.forEach((from, to) -> keys.put("p:" + from, "p:" + to));
        copy.setBlocks(composition(originalId).blocks().stream().map(Block::key).map(keys::get).filter(Objects::nonNull).collect(Collectors.toCollection(ArrayList::new)));
        workspaces.save(copy);
    }
    /** Stable topological ordering of blocks and local members, never splitting a group. */
    public void calculateOrder(Long id) {
        Composition c = composition(id);
        Map<Long, String> rootBlocks = new HashMap<>();
        Map<Long, Long> roots = new HashMap<>();
        for (Block b : c.blocks()) for (MavenProject root : b.projects()) {
            rootBlocks.put(root.getId(), b.key()); roots.put(root.getId(), root.getId());
            for (MavenProject child : c.all()) if (descendant(child, root)) roots.put(child.getId(), root.getId());
        }
        Map<String, Set<String>> blockDeps = new LinkedHashMap<>();
        c.blocks().forEach(b -> blockDeps.put(b.key(), new HashSet<>()));
        Map<Long, Set<Long>> projectDeps = new HashMap<>();
        c.roots().forEach(p -> projectDeps.put(p.getId(), new HashSet<>()));
        for (MavenProject p : c.all()) for (String dependency : p.getInternalDependencies()) {
            MavenProject target = c.all().stream().filter(q -> (q.getGroupId() + ":" + q.getArtifactId()).equals(dependency)).findFirst().orElse(null);
            if (target == null) continue;
            Long from = roots.get(p.getId()), to = roots.get(target.getId());
            if (from == null || to == null || from.equals(to)) continue;
            if (rootBlocks.get(from).equals(rootBlocks.get(to))) projectDeps.get(from).add(to);
            else blockDeps.get(rootBlocks.get(from)).add(rootBlocks.get(to));
        }
        List<String> blockOrder = topo(c.blocks().stream().map(Block::key).toList(), blockDeps);
        Map<Long, List<Long>> memberOrders = new HashMap<>();
        for (Block b : c.blocks()) if (b.group() != null) {
            List<Long> original = b.projects().stream().map(MavenProject::getId).toList();
            List<Long> sorted = topo(original, projectDeps);
            if (b.group().isImported() && !original.equals(sorted)) throw error("Imported parent order conflicts with dependencies. Reorder the parent first.");
            memberOrders.put(b.group().getId(), sorted);
        }
        // All graphs are validated before any order is changed.
        reorderBlocks(id, blockOrder);
        for (Block b : c.blocks()) if (b.group() != null && !b.group().isImported()) reorderMembers(id, b.group().getId(), memberOrders.get(b.group().getId()));
    }
    private static <T> List<T> topo(List<T> original, Map<T, Set<T>> dependencies) {
        List<T> result = new ArrayList<>();
        while (result.size() < original.size()) {
            T next = original.stream().filter(k -> !result.contains(k))
                .filter(k -> result.containsAll(dependencies.getOrDefault(k, Set.of()))).findFirst().orElse(null);
            if (next == null) throw error("Dependencies require splitting groups or contain a cycle. The previous order has been kept.");
            result.add(next);
        }
        return result;
    }

    public static String canonical(String path) {
        try { return new File(path).getCanonicalPath(); }
        catch (java.io.IOException e) { return new File(path).getAbsolutePath(); }
    }
}
