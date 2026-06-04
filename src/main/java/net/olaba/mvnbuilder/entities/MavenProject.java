package net.olaba.mvnbuilder.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a Maven project within a workspace.
 * Stores information about project structure, dependencies, and execution
 * state.
 */
@Entity
@Getter
@Setter
@Builder(setterPrefix = "with")
@NoArgsConstructor
@AllArgsConstructor
public class MavenProject {
    /** Unique identifier for the Maven project. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Maven artifact ID. */
    private String artifactId;

    /** Maven group ID. */
    private String groupId;

    /** Project version. */
    private String version;

    /** Path relative to the workspace base path. */
    @Column(length = 2000)
    private String relativePath;

    /** Absolute file system path of the project. */
    @Column(length = 2000)
    private String absolutePath;

    /** Current Git branch of the project. */
    private String gitBranch;

    /** Defined order of execution in the build process. */
    private Integer executionOrder;

    /** Key of the parent project in "groupId:artifactId" format. */
    private String parentKey; // stored as "groupId:artifactId"

    /** Flag indicating if the project is enabled for build actions. */
    @Builder.Default
    private boolean enabled = true;

    /** List of child module names defined in the project. */
    @ElementCollection
    @CollectionTable(name = "project_modules", joinColumns = @JoinColumn(name = "project_id"))
    @Column(name = "module_name")
    @Builder.Default
    private List<String> modules = new ArrayList<>();

    /** List of internal project dependencies in "groupId:artifactId" format. */
    @ElementCollection
    @CollectionTable(name = "project_dependencies", joinColumns = @JoinColumn(name = "project_id"))
    @Column(name = "dependency_id") // stored as "groupId:artifactId"
    @Builder.Default
    private List<String> internalDependencies = new ArrayList<>();

    /** The workspace this project belongs to. */
    @ManyToOne
    @JoinColumn(name = "workspace_id")
    private Workspace workspace;

    /**
     * Checks if this project has any child subprojects in the workspace.
     * Calculated dynamically using directory structures.
     *
     * @return true if there are other projects nested inside this project's path.
     */
    public boolean hasChildren() {
        if (workspace == null || workspace.getProjects() == null) {
            return false;
        }
        if (this.relativePath == null) {
            return false;
        }
        final String parentPath = this.relativePath.replace('\\', '/');
        for (final MavenProject child : workspace.getProjects()) {
            if (child == this) {
                continue;
            }
            if (child.getId() != null && this.getId() != null && child.getId().equals(this.getId())) {
                continue;
            }
            if (child.getRelativePath() == null) {
                continue;
            }
            final String childPath = child.getRelativePath().replace('\\', '/');
            if (parentPath.equals(".")) {
                return true;
            }
            if (childPath.startsWith(parentPath + "/")) {
                return true;
            }
        }
        return false;
    }
}

