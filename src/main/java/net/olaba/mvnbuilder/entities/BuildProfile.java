package net.olaba.mvnbuilder.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity representing a Maven build profile with specific command arguments.
 */
@Entity
@Getter
@Setter
@Builder(setterPrefix = "with")
@NoArgsConstructor
@AllArgsConstructor
public class BuildProfile {
    /** Unique identifier for the build profile. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Descriptive name of the build profile. */
    @Column(nullable = false, unique = true)
    private String name;

    /** The Maven command arguments associated with this profile. */
    @Column(nullable = false)
    private String command;

    /** Flag indicating if this is the default profile for builds. */
    @Column(nullable = false)
    private boolean isDefault;
}
