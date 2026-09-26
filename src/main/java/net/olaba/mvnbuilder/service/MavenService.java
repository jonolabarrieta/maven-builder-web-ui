package net.olaba.mvnbuilder.service;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.springframework.stereotype.Service;

import net.olaba.mvnbuilder.entities.MavenProject;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for parsing and analyzing Maven pom.xml files.
 */
@Service
public class MavenService {

    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");

    private record PomModel(File file, Model model) {}

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
        return parsePom(pomFile, workspaceBasePath, null);
    }

    public MavenProject parsePom(final File pomFile, final String workspaceBasePath, final String versionProfileId) {
        try (final FileReader reader = new FileReader(pomFile)) {
            final MavenXpp3Reader mavenReader = new MavenXpp3Reader();
            final Model model = mavenReader.read(reader);

            final String artifactId = resolveProperty(model.getArtifactId(), model, pomFile);
            String groupId = model.getGroupId();
            if (groupId == null && model.getParent() != null) {
                groupId = model.getParent().getGroupId();
            }
            groupId = resolveProperty(groupId, model, pomFile);

            final String version = resolveVersion(model, pomFile, versionProfileId);

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

    public String resolveVersion(final File pomFile, final String versionProfileId) {
        try (final FileReader reader = new FileReader(pomFile)) {
            return resolveVersion(new MavenXpp3Reader().read(reader), pomFile, versionProfileId);
        } catch (final Exception e) {
            throw new RuntimeException("Failed to parse POM: " + pomFile.getAbsolutePath(), e);
        }
    }

    private String resolveVersion(final Model model, final File pomFile, final String versionProfileId) {
        final List<PomModel> lineage = new ArrayList<>();
        lineage.add(new PomModel(pomFile, model));
        final Set<String> visited = new HashSet<>();
        visited.add(pomFile.getAbsolutePath());
        while (lineage.get(lineage.size() - 1).model().getParent() != null) {
            final PomModel parent = loadParent(lineage.get(lineage.size() - 1));
            if (parent == null || !visited.add(parent.file().getAbsolutePath())) {
                break;
            }
            lineage.add(parent);
        }
        Collections.reverse(lineage);

        final int last = lineage.size() - 1;
        final String selected = versionProfileId == null || versionProfileId.isBlank()
                ? null : versionProfileId.trim();
        final String profiled = selected == null ? null : resolveVersionAt(lineage, last, selected, new HashSet<>());
        if (profiled != null) {
            return profiled;
        }
        final String base = resolveVersionAt(lineage, last, null, new HashSet<>());
        if (base != null) {
            return base;
        }
        final Model current = lineage.get(last).model();
        return current.getVersion() != null ? current.getVersion()
                : current.getParent() != null ? current.getParent().getVersion() : null;
    }

    private PomModel loadParent(final PomModel child) {
        final org.apache.maven.model.Parent parent = child.model().getParent();
        final String relativePath = parent.getRelativePath();
        if (relativePath == null || !relativePath.isEmpty()) {
            File file = new File(child.file().getParentFile(), relativePath == null ? "../pom.xml" : relativePath);
            if (file.isDirectory()) {
                file = new File(file, "pom.xml");
            }
            final PomModel local = readMatchingParent(file, parent);
            if (local != null) {
                return local;
            }
        }
        final String path = parent.getGroupId().replace('.', '/') + "/" + parent.getArtifactId() + "/"
                + parent.getVersion() + "/" + parent.getArtifactId() + "-" + parent.getVersion() + ".pom";
        return readMatchingParent(new File(System.getProperty("user.home"), ".m2/repository/" + path), parent);
    }

    private PomModel readMatchingParent(final File file, final org.apache.maven.model.Parent parent) {
        if (!file.isFile()) {
            return null;
        }
        try (final FileReader reader = new FileReader(file)) {
            final Model model = new MavenXpp3Reader().read(reader);
            final String groupId = model.getGroupId() != null ? model.getGroupId()
                    : model.getParent() != null ? model.getParent().getGroupId() : null;
            if (parent.getGroupId().equals(groupId) && parent.getArtifactId().equals(model.getArtifactId())) {
                return new PomModel(file, model);
            }
        } catch (final Exception ignored) {
            // A parent not available locally cannot contribute profile properties.
        }
        return null;
    }

    private String resolveVersionAt(final List<PomModel> lineage, final int index, final String profileId,
            final Set<String> resolving) {
        final Model model = lineage.get(index).model();
        final String expression = model.getVersion() != null ? model.getVersion()
                : model.getParent() != null ? model.getParent().getVersion() : null;
        return resolveExpression(expression, lineage, index, profileId, effectiveProperties(lineage, index, profileId),
                resolving);
    }

    private Properties effectiveProperties(final List<PomModel> lineage, final int index, final String profileId) {
        final Properties properties = new Properties();
        for (int i = 0; i <= index; i++) {
            final Model model = lineage.get(i).model();
            properties.putAll(model.getProperties());
            if (profileId != null) {
                model.getProfiles().stream().filter(profile -> profileId.equals(profile.getId())).findFirst()
                        .ifPresent(profile -> properties.putAll(profile.getProperties()));
            }
        }
        return properties;
    }

    private String resolveExpression(final String expression, final List<PomModel> lineage, final int index,
            final String profileId, final Properties properties, final Set<String> resolving) {
        if (expression == null) {
            return null;
        }
        final Matcher matcher = PROPERTY.matcher(expression);
        final StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            final String name = matcher.group(1);
            final String key = index + ":" + name;
            if (!resolving.add(key)) {
                return null;
            }
            final Model model = lineage.get(index).model();
            final String value;
            if ("project.parent.version".equals(name) || "parent.version".equals(name)) {
                value = index > 0 ? resolveVersionAt(lineage, index - 1, profileId, resolving)
                        : model.getParent() == null ? null
                        : resolveExpression(model.getParent().getVersion(), lineage, index, profileId, properties, resolving);
            } else if ("project.version".equals(name) || "pom.version".equals(name)) {
                value = resolveVersionAt(lineage, index, profileId, resolving);
            } else if ("project.groupId".equals(name) || "pom.groupId".equals(name)) {
                value = model.getGroupId() != null ? model.getGroupId()
                        : model.getParent() == null ? null : model.getParent().getGroupId();
            } else {
                value = resolveExpression(properties.getProperty(name), lineage, index, profileId, properties, resolving);
            }
            resolving.remove(key);
            if (value == null) {
                return null;
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Resolves Maven properties (like ${project.version} or custom ones) if they are defined
     * in the model or parent POM files.
     * 
     * @param value The value to resolve.
     * @param model The Maven model.
     * @param pomFile The POM file associated with the model.
     * @return The resolved value or the original if not found.
     */
    private String resolveProperty(final String value, final Model model, final File pomFile) {
        if (value == null) {
            return null;
        }
        if (value.startsWith("${") && value.endsWith("}")) {
            final String propertyName = value.substring(2, value.length() - 1);

            // Standard Maven properties
            if ("project.parent.version".equals(propertyName) || "parent.version".equals(propertyName)) {
                if (model.getParent() != null) {
                    return resolveProperty(model.getParent().getVersion(), model, pomFile);
                }
            }
            if ("project.groupId".equals(propertyName) || "pom.groupId".equals(propertyName)) {
                String gId = model.getGroupId();
                if (gId == null && model.getParent() != null) {
                    gId = model.getParent().getGroupId();
                }
                return gId;
            }
            if ("project.version".equals(propertyName) || "pom.version".equals(propertyName)) {
                String v = model.getVersion();
                if (v == null && model.getParent() != null) {
                    v = model.getParent().getVersion();
                }
                return v;
            }

            // Custom properties
            String propertyValue = model.getProperties() != null ? model.getProperties().getProperty(propertyName) : null;
            if (propertyValue == null && model.getParent() != null) {
                // Try parent POM properties
                final java.util.Properties parentProps = getParentPomProperties(pomFile, model.getParent());
                propertyValue = parentProps.getProperty(propertyName);
            }
            if (propertyValue != null) {
                // Recursively resolve in case the property value itself is a property reference
                return resolveProperty(propertyValue, model, pomFile);
            }
        }
        return value;
    }

    /**
     * Helper to recursively load properties from parent POM files.
     */
    public java.util.Properties getParentPomProperties(final File pomFile, final org.apache.maven.model.Parent parent) {
        final java.util.Properties props = new java.util.Properties();
        if (parent == null) {
            return props;
        }

        final MavenXpp3Reader mavenReader = new MavenXpp3Reader();
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

        Model parentModel = null;
        if (parentFile.exists()) {
            try (final FileReader pr = new FileReader(parentFile)) {
                final Model pm = mavenReader.read(pr);
                String groupId = pm.getGroupId();
                if (groupId == null && pm.getParent() != null) {
                    groupId = pm.getParent().getGroupId();
                }
                if (parentGroupId.equals(groupId) && parentArtifactId.equals(pm.getArtifactId())) {
                    parentModel = pm;
                }
            } catch (final Exception e) {
                // Ignore and try ~/.m2
            }
        }

        if (parentModel == null) {
            // 2. Look in the local ~/.m2 repository
            final String m2Path = System.getProperty("user.home") + "/.m2/repository/"
                    + parentGroupId.replace('.', '/') + "/" + parentArtifactId + "/" + parentVersion + "/"
                    + parentArtifactId + "-" + parentVersion + ".pom";
            final File m2File = new File(m2Path);
            if (m2File.exists()) {
                try (final FileReader pr = new FileReader(m2File)) {
                    parentModel = mavenReader.read(pr);
                } catch (final Exception e) {
                    // Ignore
                }
            }
        }

        if (parentModel != null) {
            // Recursively get grandparent properties first, then put parent's to override them
            if (parentModel.getParent() != null) {
                final File currentPomFile = parentFile.exists() ? parentFile : new File(System.getProperty("user.home") + "/.m2/repository/"
                    + parentGroupId.replace('.', '/') + "/" + parentArtifactId + "/" + parentVersion + "/pom.xml");
                props.putAll(getParentPomProperties(currentPomFile, parentModel.getParent()));
            }
            if (parentModel.getProperties() != null) {
                props.putAll(parentModel.getProperties());
            }
        }

        return props;
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
            return getParentPomProperties(pomFile, model.getParent());
        } catch (final Exception e) {
            // Ignore
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
