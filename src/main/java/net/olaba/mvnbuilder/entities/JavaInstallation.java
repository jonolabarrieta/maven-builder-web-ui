package net.olaba.mvnbuilder.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity representing a registered Java installation (JDK) with a custom name,
 * the path to JAVA_HOME, and whether it is the default global version.
 */
@Entity
@Getter
@Setter
@Builder(setterPrefix = "with")
@NoArgsConstructor
@AllArgsConstructor
public class JavaInstallation {
    /** Unique identifier for the Java installation. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Name of this Java version (e.g. JDK 17, Java 21). */
    @Column(nullable = false, unique = true)
    private String name;

    /** Path to the JAVA_HOME directory of this installation. */
    @Column(nullable = false, length = 2000)
    private String javaHome;

    /** Flag indicating if this is the default active Java version. */
    @Column(nullable = false)
    private boolean isDefault;
}
