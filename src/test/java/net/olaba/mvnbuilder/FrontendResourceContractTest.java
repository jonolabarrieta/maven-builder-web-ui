package net.olaba.mvnbuilder;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontendResourceContractTest {

    private static final Path TEMPLATE_ROOT = Path.of("src", "main", "resources", "templates");
    private static final List<String> TOP_LEVEL_TEMPLATES = List.of(
        "index.html",
        "settings.html",
        "workspace-detail.html"
    );
    private static final Pattern NATIVE_DIALOG_CALL = Pattern.compile(
        "(?<![\\w.])(alert|confirm|prompt)\\s*\\(|\\b(window|globalThis)\\.(alert|confirm|prompt)\\s*\\("
    );

    /**
     * Ensures every complete page installs the shared loading and dialog infrastructure.
     *
     * @throws IOException when a template cannot be read
     */
    @Test
    void top_level_templates_load_shared_frontend_components() throws IOException {
        for (final String templateName : TOP_LEVEL_TEMPLATES) {
            final String template = Files.readString(TEMPLATE_ROOT.resolve(templateName), StandardCharsets.UTF_8);

            assertTrue(template.contains("src=\"/js/global-spinner.js\""),
                templateName + " must load the global spinner");
            assertTrue(template.contains("src=\"/js/confirmation-modal.js\""),
                templateName + " must load the application dialog component");
        }
    }

    /**
     * Prevents native browser dialogs from returning to any Thymeleaf template or fragment.
     *
     * @throws IOException when the template tree cannot be read
     */
    @Test
    void templates_do_not_invoke_native_browser_dialogs() throws IOException {
        try (Stream<Path> templatePaths = Files.walk(TEMPLATE_ROOT)) {
            final List<Path> htmlTemplates = templatePaths
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".html"))
                .toList();

            for (final Path templatePath : htmlTemplates) {
                final String template = Files.readString(templatePath, StandardCharsets.UTF_8);
                assertFalse(NATIVE_DIALOG_CALL.matcher(template).find(),
                    templatePath + " must use ConfirmationModal instead of a native browser dialog");
            }
        }
    }
}
