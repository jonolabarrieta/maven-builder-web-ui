"""Exercise the bulk Git stash dialog wiring in headless Chrome.

The harness embeds the production workspace page script and the production
confirmation dialog, stubs only transport, and drives the bulk "stash" action
through the real UI code. Chrome must be installed and runnable locally.
"""

import html
import json
import re
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
TEMPLATE = ROOT / "src/main/resources/templates/workspace-detail.html"
GLOBAL_SPINNER = ROOT / "src/main/resources/static/js/global-spinner.js"
CONFIRMATION = ROOT / "src/main/resources/static/js/confirmation-modal.js"


def build_harness() -> str:
    """Embed the production page script and dialog without duplicating them."""
    template = TEMPLATE.read_text(encoding="utf-8")
    match = re.search(r'<script th:inline="javascript">(.*?)</script>', template, re.S)
    if match is None:
        raise AssertionError("workspace page script not found")
    page_script = match.group(1).replace(
        "const workspaceId = [[${workspace.id}]];", "const workspaceId = 1;", 1
    )
    global_spinner_script = GLOBAL_SPINNER.read_text(encoding="utf-8")
    confirmation_script = CONFIRMATION.read_text(encoding="utf-8")
    return """<!doctype html><html><body>
        <form id="workspace-refresh-form" hx-post="/workspaces/1/refresh" data-no-spinner="true">
          <button id="workspace-refresh-button"></button></form>
        <div id="workspace-refresh-overlay" class="hidden" aria-hidden="true">
          <span id="workspace-refresh-phase"></span></div>
        <div id="workspace-refresh-status"></div>
        <div id="workspace-git-status" data-url="/workspaces/1/git-status-table"></div>
        <span id="selected-status"></span><input id="select-all" type="checkbox">
        <div id="bulk-actions" class="hidden"></div><details id="bulk-more-menu"></details>
        <div id="action-summary-container"></div>
        <div id="log-tabs"><button id="tab-Global" aria-pressed="true"></button></div>
        <div id="log-containers"><div id="log-container-Global"></div></div>
        <table><tbody id="project-table-body">
          <tr data-id="1"><td><input class="project-checkbox" value="1" type="checkbox"></td>
            <td><span id="git-branch-1">cached-1</span></td></tr>
          <tr data-id="2"><td><input class="project-checkbox" value="2" type="checkbox"></td>
            <td><span id="git-branch-2">cached-2</span></td></tr>
        </tbody></table>
        <pre id="test-results"></pre>
        <script>
          window.sortableCount = 0;
          window.htmx = {};
          window.Sortable = class { constructor() { window.sortableCount++; } };
          window.SockJS = class { constructor() {} };
          window.Stomp = {over: () => ({connect: () => {}, subscribe: () => {}})};
          window.requests = [];
          window.bulkRequests = [];
          window.fetch = (url, options = {}) => {
            if (url.includes('/bulk-action')) {
              window.bulkRequests.push({url, options});
              return Promise.resolve({ok: true});
            }
            return new Promise((resolve, reject) => {
              window.requests.push({url, options, resolve, reject});
            });
          };
          window.onerror = message => {
            document.getElementById('test-results').textContent = JSON.stringify({error: String(message)});
          };
        </script>
        <script>""" + global_spinner_script + """</script>
        <script>""" + confirmation_script + """</script>
        <script>""" + page_script + """</script>
        <script>
          const checks = {};
          const flush = () => new Promise(resolve => setTimeout(resolve, 0));
          const overlayHidden = () => document.getElementById('confirmation-modal-overlay').style.display === 'none';
          const checkbox = () => document.getElementById('confirmation-modal-checkbox-input');
          const checkboxSection = () => document.getElementById('confirmation-modal-checkbox-section');
          const primary = () => document.getElementById('confirmation-modal-primary');
          const cancel = () => document.getElementById('confirmation-modal-cancel');
          const lastBulk = () => bulkRequests.at(-1);

          async function run() {
            await flush();

            // Dialogs that do not configure an option render no checkbox.
            ConfirmationModal.info({title: 'Note', message: 'No option here'});
            checks.noCheckboxWhenOmitted = checkboxSection().style.display === 'none';
            primary().click();
            await flush();

            selectedProjectIds.add('2');

            // Opening stash shows the untracked checkbox unchecked and sends nothing yet.
            executeBulkAction('stash');
            checks.stashDialogOpens = overlayHidden() === false
              && checkboxSection().style.display === 'block'
              && checkbox().checked === false
              && document.getElementById('confirmation-modal-checkbox-label').textContent === 'Include untracked files';
            checks.noRequestBeforeConfirm = bulkRequests.length === 0;

            cancel().click();
            await flush();
            checks.cancelSendsNothing = bulkRequests.length === 0 && overlayHidden();

            // Confirming with the option unchecked posts includeUntracked=false.
            executeBulkAction('stash');
            checkbox().checked = false;
            primary().click();
            await flush();
            checks.excludedPayload = lastBulk().url === '/workspaces/1/bulk-action'
              && lastBulk().options.body.get('action') === 'stash'
              && lastBulk().options.body.get('includeUntracked') === 'false'
              && lastBulk().options.body.get('projectIds') === '2';

            // Confirming with the option checked posts includeUntracked=true.
            executeBulkAction('stash');
            checkbox().checked = true;
            primary().click();
            await flush();
            checks.includedPayload = lastBulk().options.body.get('includeUntracked') === 'true';

            // With no selection the guard dialog opens and nothing is posted.
            selectedProjectIds.clear();
            const before = bulkRequests.length;
            executeBulkAction('stash');
            checks.noSelectionGuard = bulkRequests.length === before
              && document.getElementById('confirmation-modal-title').textContent === 'No projects selected';
            primary().click();

            document.getElementById('test-results').textContent = JSON.stringify(checks);
          
          }
          document.addEventListener('DOMContentLoaded', () => run().catch(error => {
            document.getElementById('test-results').textContent = JSON.stringify({error: String(error)});
          }));
        </script></body></html>"""


def main() -> None:
    """Execute the harness and fail with the results of any broken interaction."""
    with tempfile.TemporaryDirectory(prefix="git-stash-browser-") as directory:
        page = Path(directory) / "test.html"
        page.write_text(build_harness(), encoding="utf-8")
        command = [
            "google-chrome", "--headless", "--no-sandbox", "--disable-dev-shm-usage",
            "--disable-gpu", "--disable-background-networking",
            f"--user-data-dir={directory}/profile", "--virtual-time-budget=3500",
            "--dump-dom", page.as_uri(),
        ]
        output = subprocess.run(command, capture_output=True, text=True, timeout=20, check=True)
        match = re.search(r'<pre id="test-results">(.*?)</pre>', output.stdout, re.S)
        if match is None:
            raise AssertionError(f"Browser produced no test result: {output.stderr[-1000:]}")
        results = json.loads(html.unescape(match.group(1)))
        failed = [name for name, passed in results.items() if passed is not True]
        print(json.dumps(results, indent=2))
        if failed:
            raise AssertionError(f"Git stash browser regressions failed: {failed}")


if __name__ == "__main__":
    main()
