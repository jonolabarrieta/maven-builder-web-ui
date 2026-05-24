package net.olaba.mvnbuilder.service;

import net.olaba.mvnbuilder.entities.MavenProject;
import net.olaba.mvnbuilder.repository.MavenProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service responsible for calculating the build order of projects within a
 * workspace
 * using a topological sort algorithm that respects cross-project dependencies.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TopologicalSortService {

    private final MavenProjectRepository mavenProjectRepository;
    private final WorkspaceService workspaceService;

    /**
     * Calculates and persists the execution order for all top-level projects in a
     * workspace.
     * 
     * @param workspaceId The workspace ID.
     */
    @Transactional
    public void calculateOrder(final Long workspaceId) {
        final List<MavenProject> allProjects = mavenProjectRepository
                .findByWorkspaceIdOrderByExecutionOrderAsc(workspaceId);

        // 1. Identify parent (root-level) projects
        final List<MavenProject> parentProjects = allProjects.stream()
                .filter(p -> isParentProject(p, allProjects))
                .collect(Collectors.toList());

        log.info("Found {} parent projects out of {} total", parentProjects.size(), allProjects.size());

        // 2. Map every project to its root parent for dependency analysis
        final Map<Long, Long> projectToRoot = new HashMap<>();

        for (final MavenProject parent : parentProjects) {
            final Long parentId = parent.getId();
            projectToRoot.put(parentId, parentId);

            for (final MavenProject p : allProjects) {
                if (workspaceService.isChildOf(p, parent)) {
                    projectToRoot.put(p.getId(), parentId);
                }
            }
        }

        // 3. Build dependency graph between parent projects
        final Map<Long, Set<Long>> parentDeps = new HashMap<>();
        for (final MavenProject parent : parentProjects) {
            parentDeps.put(parent.getId(), new HashSet<>());
        }

        for (final MavenProject project : allProjects) {
            final Long myRoot = projectToRoot.get(project.getId());
            if (myRoot == null) {
                continue;
            }

            for (final String depId : project.getInternalDependencies()) {
                final MavenProject depProject = resolveDependency(depId, project, allProjects, projectToRoot);
                if (depProject == null) {
                    continue;
                }

                final Long depRoot = projectToRoot.get(depProject.getId());
                if (depRoot == null || depRoot.equals(myRoot)) {
                    continue;
                }

                // Cross-family dependency: depRoot must be built before myRoot
                parentDeps.get(myRoot).add(depRoot);
                log.debug("Cross-family dep: {} (in {}) depends on {} -> {} must come before {}",
                        project.getArtifactId(), rootName(myRoot, parentProjects),
                        depId, rootName(depRoot, parentProjects), rootName(myRoot, parentProjects));
            }
        }

        // 4. Topological sort (Kahn's algorithm) on parent projects
        final Map<Long, Integer> inDegree = new HashMap<>();
        final Map<Long, List<Long>> adj = new HashMap<>();
        for (final MavenProject parent : parentProjects) {
            final Long pk = parent.getId();
            inDegree.put(pk, 0);
            adj.put(pk, new ArrayList<>());
        }

        for (final Map.Entry<Long, Set<Long>> entry : parentDeps.entrySet()) {
            final Long dependent = entry.getKey();
            for (final Long dependency : entry.getValue()) {
                if (adj.containsKey(dependency) && !adj.get(dependency).contains(dependent)) {
                    adj.get(dependency).add(dependent);
                    inDegree.put(dependent, inDegree.get(dependent) + 1);
                }
            }
        }

        // Precompute transitive dependent and dependency counts for mathematical prioritization
        final Map<Long, Integer> transitiveDependents = new HashMap<>();
        for (final MavenProject parent : parentProjects) {
            transitiveDependents.put(parent.getId(), calculateTransitiveDependentsCount(parent.getId(), adj));
        }

        final Map<Long, Integer> transitiveDependencies = new HashMap<>();
        for (final MavenProject parent : parentProjects) {
            transitiveDependencies.put(parent.getId(), calculateTransitiveDependenciesCount(parent.getId(), parentDeps));
        }

        final List<Long> ready = new LinkedList<>();
        for (final MavenProject parent : parentProjects) {
            final Long pk = parent.getId();
            if (inDegree.get(pk) == 0) {
                ready.add(pk);
            }
        }

        String lastFamily = null;
        String lastSubFamily = null;
        final List<Long> sortedParentIds = new ArrayList<>();
        while (!ready.isEmpty()) {
            final String currentLastFamily = lastFamily;
            final String currentLastSubFamily = lastSubFamily;
            // Pick node using family grouping and mathematical transitive dependents heuristics
            final Long best = ready.stream()
                    .min((k1, k2) -> {
                        final MavenProject p1 = parentProjects.stream().filter(p -> p.getId().equals(k1)).findFirst().get();
                        final MavenProject p2 = parentProjects.stream().filter(p -> p.getId().equals(k2)).findFirst().get();
                        
                        final String fam1 = getProjectFamily(p1);
                        final String fam2 = getProjectFamily(p2);
                        
                        // 1. Same family prioritization
                        if (currentLastFamily != null) {
                            final boolean match1 = fam1.equals(currentLastFamily);
                            final boolean match2 = fam2.equals(currentLastFamily);
                            if (match1 && !match2) {
                                return -1;
                            }
                            if (!match1 && match2) {
                                return 1;
                            }
                        }
                        
                        // 2. Same subFamily prioritization
                        if (currentLastSubFamily != null) {
                            final String subFam1 = getProjectParentFolder(p1);
                            final String subFam2 = getProjectParentFolder(p2);
                            final boolean subMatch1 = subFam1.equals(currentLastSubFamily);
                            final boolean subMatch2 = subFam2.equals(currentLastSubFamily);
                            if (subMatch1 && !subMatch2) {
                                return -1;
                            }
                            if (!subMatch1 && subMatch2) {
                                return 1;
                            }
                        }

                        // 3. Transitive dependencies count (ascending)
                        final int dep1 = transitiveDependencies.getOrDefault(k1, 0);
                        final int dep2 = transitiveDependencies.getOrDefault(k2, 0);
                        if (dep1 != dep2) {
                            return Integer.compare(dep1, dep2);
                        }

                        // 4. Transitive dependents (ascending with leaf deferral)
                        final int d1 = transitiveDependents.getOrDefault(k1, 0);
                        final int d2 = transitiveDependents.getOrDefault(k2, 0);
                        if (d1 != d2) {
                            if (d1 == 0) return 1;
                            if (d2 == 0) return -1;
                            return Integer.compare(d1, d2); // Ascending
                        }
                        
                        // Tie breaker 1: Shortest relative path length
                        final int len1 = p1.getRelativePath() != null ? p1.getRelativePath().length() : Integer.MAX_VALUE;
                        final int len2 = p2.getRelativePath() != null ? p2.getRelativePath().length() : Integer.MAX_VALUE;
                        if (len1 != len2) {
                            return Integer.compare(len1, len2);
                        }
                        
                        // Tie breaker 2: Alphabetical by artifactId
                        final String art1 = p1.getArtifactId() != null ? p1.getArtifactId() : "";
                        final String art2 = p2.getArtifactId() != null ? p2.getArtifactId() : "";
                        return art1.compareTo(art2);
                    })
                    .get();
            
            ready.remove(best);
            sortedParentIds.add(best);
            
            final MavenProject bestProject = parentProjects.stream().filter(p -> p.getId().equals(best)).findFirst().get();
            lastFamily = getProjectFamily(bestProject);
            lastSubFamily = getProjectParentFolder(bestProject);

            for (final Long neighbor : adj.get(best)) {
                inDegree.put(neighbor, inDegree.get(neighbor) - 1);
                if (inDegree.get(neighbor) == 0) {
                    ready.add(neighbor);
                }
            }
        }

        // Cycle handling
        if (sortedParentIds.size() != parentProjects.size()) {
            log.warn("Cycle detected in parent dependencies! Sorted {} out of {}",
                    sortedParentIds.size(), parentProjects.size());
            for (final MavenProject p : parentProjects) {
                if (!sortedParentIds.contains(p.getId())) {
                    sortedParentIds.add(p.getId());
                }
            }
        }

        // 5. Update execution order
        log.info("Calculated build order for {} parent projects:", sortedParentIds.size());
        int orderIndex = 0;
        final Map<Long, MavenProject> projectMap = allProjects.stream()
                .collect(Collectors.toMap(MavenProject::getId, p -> p));

        for (final Long parentId : sortedParentIds) {
            final MavenProject parent = projectMap.get(parentId);
            parent.setExecutionOrder(orderIndex++);
            mavenProjectRepository.save(parent);
            log.info("  Order {}: {} (deps: {})", parent.getExecutionOrder(), parent.getArtifactId(),
                    parentDeps.get(parentId).stream()
                            .map(k -> rootName(k, parentProjects))
                            .collect(Collectors.joining(", ")));
        }
    }

    /**
     * Identifies if a project has no physical ancestor in the given list.
     * 
     * @param project     The project to check.
     * @param allProjects The list of all workspace projects.
     * @return True if it's a root-level project.
     */
    private boolean isParentProject(final MavenProject project, final List<MavenProject> allProjects) {
        for (final MavenProject other : allProjects) {
            if (workspaceService.isChildOf(project, other)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Resolves a dependency key (groupId:artifactId) to a project in the workspace.
     * Prioritizes enabled projects, same family projects, and shorter paths.
     */
    private MavenProject resolveDependency(
            final String depKey,
            final MavenProject dependingProject,
            final List<MavenProject> allProjects,
            final Map<Long, Long> projectToRoot) {

        final String[] parts = depKey.split(":");
        if (parts.length != 2) {
            return null;
        }
        final String groupId = parts[0];
        final String artifactId = parts[1];

        // Find all candidates matching groupId:artifactId
        final List<MavenProject> candidates = allProjects.stream()
                .filter(p -> p.getGroupId().equals(groupId) && p.getArtifactId().equals(artifactId))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        // Multiple candidates:
        // 1. Filter candidates to enabled ones
        List<MavenProject> filtered = candidates.stream().filter(MavenProject::isEnabled).collect(Collectors.toList());
        if (filtered.isEmpty()) {
            filtered = candidates; // fallback if all candidates are disabled
        }

        // 2. Prefer candidates in the same family (root parent)
        final Long dependingRoot = projectToRoot.get(dependingProject.getId());
        if (dependingRoot != null) {
            final List<MavenProject> sameFamily = filtered.stream()
                    .filter(p -> dependingRoot.equals(projectToRoot.get(p.getId())))
                    .collect(Collectors.toList());
            if (!sameFamily.isEmpty()) {
                filtered = sameFamily;
            }
        }

        // 3. Fallback/sort by shortest relative path
        filtered.sort(Comparator.comparing(p -> p.getRelativePath() != null ? p.getRelativePath().length() : Integer.MAX_VALUE));

        return filtered.get(0);
    }

    /**
     * Resolves the artifactId for a given project key among the parent projects.
     * 
     * @param id      The parent ID.
     * @param parents The list of parent projects.
     * @return The artifactId or the string representation of ID if not found.
     */
    private String rootName(final Long id, final List<MavenProject> parents) {
        return parents.stream()
                .filter(p -> p.getId().equals(id))
                .map(MavenProject::getArtifactId)
                .findFirst().orElse(id.toString());
    }

    /**
     * Extracts organization/family from a project's groupId.
     */
    private String getProjectFamily(final MavenProject project) {
        if (project == null || project.getGroupId() == null) {
            return "";
        }
        final String groupId = project.getGroupId();
        final String[] segments = groupId.split("\\.");
        if (segments.length == 0) {
            return "";
        }
        if (segments.length > 1) {
            final String first = segments[0].toLowerCase();
            if (first.equals("com") || first.equals("org") || first.equals("net") || 
                first.equals("edu") || first.equals("gov") || first.equals("mil") || 
                first.equals("io") || first.equals("co")) {
                return segments[0] + "." + segments[1];
            }
        }
        return segments[0];
    }

    /**
     * Extracts the name of the parent folder containing the project.
     */
    private String getProjectParentFolder(final MavenProject project) {
        if (project == null || project.getAbsolutePath() == null) {
            return "";
        }
        try {
            final java.nio.file.Path path = java.nio.file.Paths.get(project.getAbsolutePath());
            final java.nio.file.Path parent = path.getParent();
            return parent != null ? parent.getFileName().toString() : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Calculates the number of transitive dependents for a given parent project.
     * This represents the size of the transitive closure of dependents.
     */
    private int calculateTransitiveDependentsCount(final Long rootId, final Map<Long, List<Long>> adj) {
        final Set<Long> visited = new HashSet<>();
        final Queue<Long> queue = new LinkedList<>();
        queue.add(rootId);
        visited.add(rootId);

        while (!queue.isEmpty()) {
            final Long current = queue.poll();
            final List<Long> neighbors = adj.get(current);
            if (neighbors != null) {
                for (final Long neighbor : neighbors) {
                    if (!visited.contains(neighbor)) {
                        visited.add(neighbor);
                        queue.add(neighbor);
                    }
                }
            }
        }
        return visited.size() - 1; // Exclude the starting node itself
    }

    /**
     * Calculates the number of transitive dependencies for a given parent project.
     * This represents the size of the transitive closure of dependencies.
     */
    private int calculateTransitiveDependenciesCount(final Long rootId, final Map<Long, Set<Long>> parentDeps) {
        final Set<Long> visited = new HashSet<>();
        final Queue<Long> queue = new LinkedList<>();
        queue.add(rootId);
        visited.add(rootId);

        while (!queue.isEmpty()) {
            final Long current = queue.poll();
            final Set<Long> deps = parentDeps.get(current);
            if (deps != null) {
                for (final Long dep : deps) {
                    if (!visited.contains(dep)) {
                        visited.add(dep);
                        queue.add(dep);
                    }
                }
            }
        }
        return visited.size() - 1; // Exclude the starting node itself
    }
}
