package net.olaba.mvnbuilder.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One environment variable of a RUN entry. */
@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class RunEnvVar {
    @Column(name = "env_name", nullable = false, length = 255) private String name;
    @Column(name = "env_value", length = 4000) private String value;
}
