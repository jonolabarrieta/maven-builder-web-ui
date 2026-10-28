package net.olaba.mvnbuilder.service;

import net.olaba.mvnbuilder.entities.*;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Saves RUN entries in a real H2 file and reopens it. */
class RunEntryPersistenceTest {
    @TempDir Path directory;

    SessionFactory factory(String url) {
        return new Configuration().addAnnotatedClass(Workspace.class).addAnnotatedClass(MavenProject.class)
            .addAnnotatedClass(JavaInstallation.class).addAnnotatedClass(RunEntry.class)
            .setProperty("hibernate.connection.url", url).setProperty("hibernate.connection.username", "sa")
            .setProperty("hibernate.connection.password", "").setProperty("hibernate.hbm2ddl.auto", "update")
            .setProperty("hibernate.show_sql", "false").buildSessionFactory();
    }

    @Test
    void entryWithOrderedEnvironmentSurvivesReopen() {
        String url = "jdbc:h2:file:" + directory.resolve("run") + ";DB_CLOSE_ON_EXIT=FALSE";
        long entryId;
        try (var factory = factory(url); var session = factory.openSession()) {
            var tx = session.beginTransaction();
            Workspace workspace = Workspace.builder().withName("Services").build();
            session.persist(workspace);
            RunEntry entry = new RunEntry();
            entry.setWorkspace(workspace); entry.setName("api"); entry.setCommand("mvn spring-boot:run");
            entry.setWorkingDirectory("/srv/api"); entry.setProjectId(99L); entry.setUseWorkspaceJava(true);
            entry.getEnv().add(new RunEnvVar("SERVER_PORT", "8081"));
            entry.getEnv().add(new RunEnvVar("A_FLAG", ""));
            session.persist(entry);
            tx.commit();
            entryId = entry.getId();
        }
        try (var factory = factory(url); var session = factory.openSession()) {
            RunEntry entry = session.get(RunEntry.class, entryId);
            assertEquals("api", entry.getName());
            assertEquals(99L, entry.getProjectId());
            assertTrue(entry.isUseWorkspaceJava());
            assertEquals(2, entry.getEnv().size());
            assertEquals("SERVER_PORT", entry.getEnv().get(0).getName());
            assertEquals("8081", entry.getEnv().get(0).getValue());
            assertEquals("A_FLAG", entry.getEnv().get(1).getName());

        }
    }
}
