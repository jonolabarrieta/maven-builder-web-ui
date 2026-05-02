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
        final Map<String, String> projectToRoot = new HashMap<>();

        for (final MavenProject parent : parentProjects) {
            final String parentKey = key(parent);
            projectToRoot.put(parentKey, parentKey);

            for (final MavenProject p : allProjects) {
                if (workspaceService.isChildOf(p, parent)) {
                    projectToRoot.put(key(p), parentKey);
                }
            }
        }

        // 3. Build dependency graph between parent projects
        final Map<String, Set<String>> parentDeps = new HashMap<>();
        for (final MavenProject parent : parentProjects) {
            parentDeps.put(key(parent), new HashSet<>());
        }

        final Set<String> allProjectKeys = allProjects.stream().map(this::key).collect(Collectors.toSet());

        for (final MavenProject project : allProjects) {
            final String myRoot = projectToRoot.get(key(project));
            if (myRoot == null) {
                continue;
            }

            for (final String depId : project.getInternalDependencies()) {
                if (!allProjectKeys.contains(depId)) {
                    continue;
                }

                final String depRoot = projectToRoot.get(depId);
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
        final Map<String, Integer> inDegree = new HashMap<>();
        final Map<String, List<String>> adj = new HashMap<>();
        for (final MavenProject parent : parentProjects) {
            final String pk = key(parent);
            inDegree.put(pk, 0);
            adj.put(pk, new ArrayList<>());
        }

        for (final Map.Entry<String, Set<String>> entry : parentDeps.entrySet()) {
            final String dependent = entry.getKey();
            for (final String dependency : entry.getValue()) {
                if (adj.containsKey(dependency) && !adj.get(dependency).contains(dependent)) {
                    adj.get(dependency).add(dependent);
                    inDegree.put(dependent, inDegree.get(dependent) + 1);
                }
            }
        }

        final List<String> ready = new LinkedList<>();
        for (final MavenProject parent : parentProjects) {
            final String pk = key(parent);
            if (inDegree.get(pk) == 0) {
                ready.add(pk);
            }
        }

        final List<String> sortedParentKeys = new ArrayList<>();
        while (!ready.isEmpty()) {
            // Pick node with shortest path for stable ordering
            final String best = ready.stream()
                    .min(Comparator.comparing(k -> parentProjects.stream()
                            .filter(p -> key(p).equals(k))
                            .findFirst().get().getRelativePath().length()))
                    .get();
            ready.remove(best);
            sortedParentKeys.add(best);

            for (final String neighbor : adj.get(best)) {
                inDegree.put(neighbor, inDegree.get(neighbor) - 1);
                if (inDegree.get(neighbor) == 0) {
                    ready.add(neighbor);
                }
            }
        }

        // Cycle handling
        if (sortedParentKeys.size() != parentProjects.size()) {
            log.warn("Cycle detected in parent dependencies! Sorted {} out of {}",
                    sortedParentKeys.size(), parentProjects.size());
            for (final MavenProject p : parentProjects) {
                if (!sortedParentKeys.contains(key(p))) {
                    sortedParentKeys.add(key(p));
                }
            }
        }

        // 5. Update execution order
        log.info("Calculated build order for {} parent projects:", sortedParentKeys.size());
        int orderIndex = 0;
        final Map<String, MavenProject> projectMap = allProjects.stream()
                .collect(Collectors.toMap(this::key, p -> p));

        for (final String parentKey : sortedParentKeys) {
            final MavenProject parent = projectMap.get(parentKey);
            parent.setExecutionOrder(orderIndex++);
            mavenProjectRepository.save(parent);
            log.info("  Order {}: {} (deps: {})", parent.getExecutionOrder(), parent.getArtifactId(),
                    parentDeps.get(parentKey).stream()
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
     * Generates a unique key for a project based on groupId and artifactId.
     * 
     * @param p The project.
     * @return The unique key string.
     */
    private String key(final MavenProject p) {
        return p.getGroupId() + ":" + p.getArtifactId();
    }

    /**
     * Resolves the artifactId for a given project key among the parent projects.
     * 
     * @param key     The project key.
     * @param parents The list of parent projects.
     * @return The artifactId or the key if not found.
     */
    private String rootName(final String key, final List<MavenProject> parents) {
        return parents.stream()
                .filter(p -> key(p).equals(key))
                .map(MavenProject::getArtifactId)
                .findFirst().orElse(key);
    }
}
