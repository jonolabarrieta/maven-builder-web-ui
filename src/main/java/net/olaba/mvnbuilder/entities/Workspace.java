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
 * Entity representing a workspace containing multiple Maven projects.
 */
@Entity
@Getter
@Setter
@Builder(setterPrefix = "with")
@NoArgsConstructor
@AllArgsConstructor
public class Workspace {
    /** Unique identifier for the workspace. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Name of the workspace. */
    @Column(nullable = false)
    private String name;

    /** Base filesystem path where projects are located. */
    @Column(nullable = true, length = 2000)
    private String basePath;

    /** List of Maven projects associated with this workspace. */
    @OneToMany(mappedBy = "workspace", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MavenProject> projects = new ArrayList<>();

    /** List of directory paths excluded from the workspace scanning process. */
    @ElementCollection
    @CollectionTable(name = "workspace_excluded_paths", joinColumns = @JoinColumn(name = "workspace_id"))
    @Column(name = "excluded_path", length = 2000)
    @Builder.Default
    private List<String> excludedPaths = new ArrayList<>();
}
