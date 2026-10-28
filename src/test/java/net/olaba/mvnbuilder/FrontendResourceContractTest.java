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

    /**
     * Keeps the RUN console, launch dialog and build guard wired into the workspace page.
     *
     * @throws IOException when a resource cannot be read
     */
    @Test
    void workspace_page_wires_run_entries_and_build_guard() throws IOException {
        final String page = Files.readString(TEMPLATE_ROOT.resolve("workspace-detail.html"), StandardCharsets.UTF_8);
        final Path js = Path.of("src", "main", "resources", "static", "js");
        final String run = Files.readString(js.resolve("run-processes.js"), StandardCharsets.UTF_8);
        final String groups = Files.readString(js.resolve("workspace-groups.js"), StandardCharsets.UTF_8);

        assertTrue(page.contains("src=\"/js/run-processes.js\""), "workspace page must load the RUN module");
        assertTrue(page.contains("data-run-open "), "workspace page must offer the Run action");
        assertTrue(page.contains("data-run-open-config"), "workspace page must offer the RUN configuration");
        assertTrue(page.contains("id=\"run-launch-modal\"") && page.contains("id=\"run-config-modal\""), "RUN dialogs must exist");
        assertTrue(page.contains("window.RunTabs?.connected(stompClient)"), "RUN tabs must subscribe when the socket connects");
        assertTrue(page.contains("!window.RunTabs.beforeClose(label)"), "closing a running RUN tab must be refused");
        assertTrue(page.contains("!window.RunTabs?.isRunTab(label)"), "clearing logs must keep RUN tabs");
        assertTrue(page.contains("window.RunGuard.fetch"), "selection builds must answer the build guard");
        assertTrue(groups.contains("window.RunGuard.fetch"), "group builds must answer the build guard");

        assertTrue(run.contains("'htmx:responseError'") && run.contains("xhr.status !== 409"), "htmx builds must answer the build guard");
        assertTrue(run.contains("runGuard="), "guarded requests must be re-sent with the answer");
        assertTrue(run.contains("/run/state"), "RUN tabs must be restored after a reload");
        assertTrue(run.contains("message.seq <= tab.lastSeq"), "restored and live lines must not be duplicated");
        assertFalse(run.contains("followBuild"), "RUN output must not drive the follow toggle");
    }
}
