package net.olaba.mvnbuilder.service;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.springframework.stereotype.Service;

import net.olaba.mvnbuilder.entities.MavenProject;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for parsing and analyzing Maven pom.xml files.
 */
@Service
public class MavenService {

    /**
     * Parses a pom.xml file into a MavenProject domain object.
     * 
     * @param pomFile           The pom.xml file to parse.
     * @param workspaceBasePath The base path of the workspace for calculating
     *                          relative paths.
     * @return A MavenProject object.
     * @throws RuntimeException if parsing fails.
     */
    public MavenProject parsePom(final File pomFile, final String workspaceBasePath) {
        try (final FileReader reader = new FileReader(pomFile)) {
            final MavenXpp3Reader mavenReader = new MavenXpp3Reader();
            final Model model = mavenReader.read(reader);

            final String artifactId = resolveProperty(model.getArtifactId(), model);
            String groupId = model.getGroupId();
            if (groupId == null && model.getParent() != null) {
                groupId = model.getParent().getGroupId();
            }
            groupId = resolveProperty(groupId, model);

            String version = model.getVersion();
            if (version == null && model.getParent() != null) {
                version = model.getParent().getVersion();
            }
            version = resolveProperty(version, model);

            final String absolutePath = pomFile.getParentFile().getAbsolutePath();
            String relativePath;
            try {
                relativePath = new File(workspaceBasePath).getAbsoluteFile().toPath()
                        .relativize(new File(absolutePath).toPath()).toString();
            } catch (final Exception e) {
                // If we can't relativize, just use the absolute path as relative fallback
                relativePath = absolutePath;
            }

            // Normalize separators to forward slash for cross-platform consistency
            relativePath = relativePath.replace('\\', '/');

            if (relativePath.isEmpty()) {
                relativePath = ".";
            }

            final List<String> modules = model.getModules();
            final List<String> internalDependencies = model.getDependencies().stream()
                    .map(d -> d.getGroupId() + ":" + d.getArtifactId())
                    .collect(Collectors.toList());

            String parentKey = null;
            if (model.getParent() != null) {
                parentKey = model.getParent().getGroupId() + ":" + model.getParent().getArtifactId();
            }

            return MavenProject.builder()
                    .withArtifactId(artifactId)
                    .withGroupId(groupId)
                    .withVersion(version)
                    .withRelativePath(relativePath)
                    .withAbsolutePath(absolutePath)
                    .withModules(new ArrayList<>(modules))
                    .withInternalDependencies(internalDependencies)
                    .withParentKey(parentKey)
                    .build();
        } catch (final Exception e) {
            throw new RuntimeException("Failed to parse POM: " + pomFile.getAbsolutePath(), e);
        }
    }

    /**
     * Resolves basic Maven properties (like ${project.version}) if they are defined
     * in the model.
     * 
     * @param value The value to resolve.
     * @param model The Maven model.
     * @return The resolved value or the original if not found.
     */
    private String resolveProperty(final String value, final Model model) {
        if (value != null && value.startsWith("${") && value.endsWith("}")) {
            final String propertyName = value.substring(2, value.length() - 1);
            final String propertyValue = model.getProperties().getProperty(propertyName);
            if (propertyValue != null) {
                return propertyValue;
            }
        }
        return value;
    }

    /**
     * Finds and parses the parent POM file of a project and extracts its properties.
     * 
     * @param project The project whose parent POM properties to fetch.
     * @return The Properties object of the parent POM, or empty Properties if none.
     */
    public java.util.Properties getParentPomProperties(final MavenProject project) {
        final File pomFile = new File(project.getAbsolutePath(), "pom.xml");
        if (!pomFile.exists()) {
            return new java.util.Properties();
        }

        try (final FileReader reader = new FileReader(pomFile)) {
            final MavenXpp3Reader mavenReader = new MavenXpp3Reader();
            final Model model = mavenReader.read(reader);

            if (model.getParent() == null) {
                return new java.util.Properties();
            }

            final org.apache.maven.model.Parent parent = model.getParent();
            final String parentGroupId = parent.getGroupId();
            final String parentArtifactId = parent.getArtifactId();
            final String parentVersion = parent.getVersion();
            final String parentRelativePath = parent.getRelativePath();

            // Try to find the parent POM file:
            // 1. Check relative path if specified (default: ../pom.xml)
            final String relPath = parentRelativePath != null ? parentRelativePath : "../pom.xml";
            File parentFile = new File(pomFile.getParentFile(), relPath);
            if (parentFile.isDirectory()) {
                parentFile = new File(parentFile, "pom.xml");
            }
            if (parentFile.exists()) {
                try (final FileReader pr = new FileReader(parentFile)) {
                    final Model parentModel = mavenReader.read(pr);
                    String groupId = parentModel.getGroupId();
                    if (groupId == null && parentModel.getParent() != null) {
                        groupId = parentModel.getParent().getGroupId();
                    }
                    if (parentGroupId.equals(groupId) && parentArtifactId.equals(parentModel.getArtifactId())) {
                        return parentModel.getProperties();
                    }
                } catch (final Exception e) {
                    // Ignore and try ~/.m2
                }
            }

            // 2. Look in the local ~/.m2 repository
            final String m2Path = System.getProperty("user.home") + "/.m2/repository/"
                    + parentGroupId.replace('.', '/') + "/" + parentArtifactId + "/" + parentVersion + "/"
                    + parentArtifactId + "-" + parentVersion + ".pom";
            final File m2File = new File(m2Path);
            if (m2File.exists()) {
                try (final FileReader pr = new FileReader(m2File)) {
                    final Model parentModel = mavenReader.read(pr);
                    return parentModel.getProperties();
                }
            }
        } catch (final Exception e) {
            // Log or ignore
        }

        return new java.util.Properties();
    }

    /**
     * Finds and parses the project's own POM file and extracts its properties.
     * 
     * @param project The project whose POM properties to fetch.
     * @return The Properties object of the POM, or empty Properties if none.
     */
    public java.util.Properties getProjectProperties(final MavenProject project) {
        final File pomFile = new File(project.getAbsolutePath(), "pom.xml");
        if (!pomFile.exists()) {
            return new java.util.Properties();
        }

        try (final FileReader reader = new FileReader(pomFile)) {
            final MavenXpp3Reader mavenReader = new MavenXpp3Reader();
            final Model model = mavenReader.read(reader);
            return model.getProperties() != null ? model.getProperties() : new java.util.Properties();
        } catch (final Exception e) {
            // Ignore
        }

        return new java.util.Properties();
    }
}
