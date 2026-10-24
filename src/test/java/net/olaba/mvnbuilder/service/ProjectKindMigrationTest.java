package net.olaba.mvnbuilder.service;

import net.olaba.mvnbuilder.entities.ProjectKind;
import net.olaba.mvnbuilder.repository.MavenProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=update", "spring.sql.init.mode=always", "spring.sql.init.schema-locations=classpath:legacy-project-schema.sql"})
class ProjectKindMigrationTest {
    @Autowired MavenProjectRepository projects;
    @Test void existingSchemaBackfillsMavenKindWithoutChangingIdentity() {
        var legacy=projects.findById(42L).orElseThrow();
        assertEquals(ProjectKind.MAVEN,legacy.getProjectKind()); assertEquals("legacy-project",legacy.getArtifactId());
        assertTrue(legacy.isMaven());
    }
}
