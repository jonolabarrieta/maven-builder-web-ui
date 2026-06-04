package net.olaba.mvnbuilder.config;

import net.olaba.mvnbuilder.model.LogMessage;
import net.olaba.mvnbuilder.model.ProcessInfo;
import net.olaba.mvnbuilder.entities.BuildProfile;
import net.olaba.mvnbuilder.entities.FavoriteM2Folder;
import net.olaba.mvnbuilder.entities.MavenProject;
import net.olaba.mvnbuilder.entities.Workspace;
import net.olaba.mvnbuilder.entities.JavaInstallation;
import net.olaba.mvnbuilder.entities.SystemSetting;
import net.olaba.mvnbuilder.model.BuildFailure;
import net.olaba.mvnbuilder.model.M2ProjectInfo;
import net.olaba.mvnbuilder.model.ActionSummary;
import net.olaba.mvnbuilder.controller.WorkspaceController;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

import java.util.ArrayList;

/**
 * Configuration for GraalVM native image runtime hints.
 * Ensures that reflection-based operations (like Thymeleaf or SpEL) work in
 * native mode.
 */
@Configuration
@ImportRuntimeHints(NativeRuntimeHints.NativeHintsRegistrar.class)
public class NativeRuntimeHints {

    /**
     * Registrar for native image runtime hints.
     */
    public static class NativeHintsRegistrar implements RuntimeHintsRegistrar {

        /**
         * Registers reflection hints for various classes used throughout the
         * application.
         * 
         * @param hints       The RuntimeHints.
         * @param classLoader The ClassLoader.
         */
        @Override
        public void registerHints(final RuntimeHints hints, final ClassLoader classLoader) {
            // Register List and ArrayList methods for reflection (needed by SpEL/Thymeleaf)
            hints.reflection().registerType(java.util.List.class, MemberCategory.INVOKE_PUBLIC_METHODS);
            hints.reflection().registerType(ArrayList.class, MemberCategory.INVOKE_PUBLIC_METHODS);
            hints.reflection().registerType(String.class, MemberCategory.INVOKE_PUBLIC_METHODS);

            // Register Hibernate collection types if present (needed when SpEL accesses
            // lazy-loaded collections)
            try {
                hints.reflection().registerType(
                        Class.forName("org.hibernate.collection.spi.PersistentBag"),
                        MemberCategory.INVOKE_PUBLIC_METHODS);
            } catch (final ClassNotFoundException ignored) {
                // Not on classpath
            }

            // Register our entities for full reflection access
            hints.reflection().registerType(Workspace.class,
                    builder -> builder.withMembers(
                            MemberCategory.INVOKE_PUBLIC_METHODS,
                            MemberCategory.INVOKE_DECLARED_METHODS,
                            MemberCategory.PUBLIC_FIELDS,
                            MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS));

            hints.reflection().registerType(MavenProject.class,
                    builder -> builder.withMembers(
                            MemberCategory.INVOKE_PUBLIC_METHODS,
                            MemberCategory.INVOKE_DECLARED_METHODS,
                            MemberCategory.PUBLIC_FIELDS,
                            MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS));

            hints.reflection().registerType(BuildProfile.class,
                    builder -> builder.withMembers(
                            MemberCategory.INVOKE_PUBLIC_METHODS,
                            MemberCategory.INVOKE_DECLARED_METHODS,
                            MemberCategory.PUBLIC_FIELDS,
                            MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS));

            hints.reflection().registerType(FavoriteM2Folder.class,
                    builder -> builder.withMembers(
                            MemberCategory.INVOKE_PUBLIC_METHODS,
                            MemberCategory.INVOKE_DECLARED_METHODS,
                            MemberCategory.PUBLIC_FIELDS,
                            MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS));

            hints.reflection().registerType(JavaInstallation.class,
                    builder -> builder.withMembers(
                            MemberCategory.INVOKE_PUBLIC_METHODS,
                            MemberCategory.INVOKE_DECLARED_METHODS,
                            MemberCategory.PUBLIC_FIELDS,
                            MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS));

            hints.reflection().registerType(SystemSetting.class,
                    builder -> builder.withMembers(
                            MemberCategory.INVOKE_PUBLIC_METHODS,
                            MemberCategory.INVOKE_DECLARED_METHODS,
                            MemberCategory.PUBLIC_FIELDS,
                            MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS));

            // Register DTOs for WebSocket serialization and Thymeleaf access
            hints.reflection().registerType(LogMessage.class,
                    builder -> builder.withMembers(
                            MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                            MemberCategory.INVOKE_PUBLIC_METHODS,
                            MemberCategory.PUBLIC_FIELDS));

            hints.reflection().registerType(ProcessInfo.class,
                    builder -> builder.withMembers(
                            MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                            MemberCategory.INVOKE_PUBLIC_METHODS,
                            MemberCategory.PUBLIC_FIELDS));

            hints.reflection().registerType(BuildFailure.class,
                    builder -> builder.withMembers(
                            MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                            MemberCategory.INVOKE_PUBLIC_METHODS,
                            MemberCategory.PUBLIC_FIELDS));

            hints.reflection().registerType(M2ProjectInfo.class,
                    builder -> builder.withMembers(
                            MemberCategory.INVOKE_PUBLIC_METHODS,
                            MemberCategory.PUBLIC_FIELDS));

            hints.reflection().registerType(ActionSummary.class,
                    builder -> builder.withMembers(
                            MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                            MemberCategory.INVOKE_PUBLIC_METHODS,
                            MemberCategory.PUBLIC_FIELDS));

            hints.reflection().registerType(WorkspaceController.KeyPropertyInfo.class,
                    builder -> builder.withMembers(
                            MemberCategory.INVOKE_PUBLIC_METHODS,
                            MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS));

            hints.reflection().registerType(net.olaba.mvnbuilder.service.FileSystemService.FileItem.class,
                    builder -> builder.withMembers(
                            MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                            MemberCategory.INVOKE_PUBLIC_METHODS,
                            MemberCategory.PUBLIC_FIELDS));

            // Thymeleaf SpEL expression helpers
            hints.reflection().registerType(org.thymeleaf.expression.Lists.class, MemberCategory.INVOKE_PUBLIC_METHODS);
            hints.reflection().registerType(org.thymeleaf.expression.Strings.class, MemberCategory.INVOKE_PUBLIC_METHODS);

            // Register Maven model classes for parsing POMs
            hints.reflection().registerType(org.apache.maven.model.Model.class,
                    builder -> builder.withMembers(
                            MemberCategory.INVOKE_PUBLIC_METHODS,
                            MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS));
            hints.reflection().registerType(org.apache.maven.model.Parent.class,
                    builder -> builder.withMembers(
                            MemberCategory.INVOKE_PUBLIC_METHODS,
                            MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS));
            hints.reflection().registerType(org.apache.maven.model.Dependency.class,
                    builder -> builder.withMembers(
                            MemberCategory.INVOKE_PUBLIC_METHODS,
                            MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS));
        }
    }
}
