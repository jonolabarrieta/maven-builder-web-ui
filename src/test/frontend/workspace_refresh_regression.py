"""Run workspace selection and refresh lifecycle checks in headless Chrome.

Usage: python3 src/test/frontend/workspace_refresh_regression.py
The harness evaluates the current Thymeleaf page script against a small DOM and
controlled delayed responses. Chrome must be installed and runnable locally.
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


def build_harness() -> str:
    """Embed the production page script without duplicating its implementation."""
    template = TEMPLATE.read_text(encoding="utf-8")
    match = re.search(r'<script th:inline="javascript">(.*?)</script>', template, re.S)
    if match is None:
        raise AssertionError("workspace page script not found")
    page_script = match.group(1).replace(
        "const workspaceId = [[${workspace.id}]];", "const workspaceId = 1;", 1
    )
    # Recovery is tested independently; these fixtures isolate the older page concerns.
    page_script = "window.BuildRecovery = {init(){}, reset(){}, show(){}};\n" + page_script
    global_spinner_script = GLOBAL_SPINNER.read_text(encoding="utf-8")
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
          window.ConfirmationModal = {info: () => {}, error: () => { window.orderError = true; }};
          window.requests = [];
          window.bulkIds = [];
          window.savedOrders = [];
          window.fetch = (url, options = {}) => {
            if (url.includes('/update-order')) {
              window.savedOrders.push(JSON.parse(options.body));
              return new Promise(resolve => { window.pendingOrder = resolve; });
            }
            if (url.includes('/bulk-action')) {
              window.bulkIds.push(options.body.getAll('projectIds'));
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
        <script>""" + page_script + """</script>
        <script>
          const checks = {};
          const flush = () => new Promise(resolve => setTimeout(resolve, 0));
          const form = document.getElementById('workspace-refresh-form');
          const htmxEvent = (name, extra = {}) => document.dispatchEvent(new CustomEvent(name, {
            cancelable: true, detail: {elt: form, requestConfig: {elt: form}, ...extra}
          }));
          const complete = (request, data) => request.resolve({ok: true, json: async () => data});
          const clickProject = id => {
            const checkbox = document.querySelector(`#project-table-body input[value="${id}"]`);
            checkbox.checked = true;
            checkbox.dispatchEvent(new Event('change', {bubbles: true}));
          };
          const replaceRows = () => {
            const oldBody = document.getElementById('project-table-body');
            oldBody.outerHTML = `<tbody id="project-table-body">
              <tr data-id="2"><td><input class="project-checkbox" value="2" type="checkbox"></td>
                <td><span id="git-branch-2">cached-new-2</span></td></tr>
              <tr data-id="3"><td><input class="project-checkbox" value="3" type="checkbox"></td>
                <td><span id="git-branch-3">cached-new-3</span></td></tr></tbody>`;
            htmxEvent('htmx:afterSwap', {target: document.getElementById('project-table-body')});
          };
          async function run() {
            const firstGit = requests[0];
            clickProject('1');
            complete(firstGit, [{projectId: 1, branch: '<img src=x onerror=alert(1)>'}]);
            await flush();
            checks.selectionDuringGit = selectedProjectIds.has('1')
              && document.querySelector('input[value="1"]').checked;
            checks.branchEscaped = document.getElementById('git-branch-1').textContent
              === '<img src=x onerror=alert(1)>' && !document.querySelector('#git-branch-1 img');
            const referenceRows = document.getElementById('project-table-body');
            const selectedCheckbox = document.querySelector('input[value="1"]');
            requestGitStatus(false);
            complete(requests.at(-1), [
              {projectId: 1, branch: 'v1.0.0 · detached HEAD'},
              {projectId: 2, branch: 'feature/demo'}
            ]);
            await flush();
            checks.typedReferenceLabelsSurviveBackgroundUpdate =
              document.getElementById('git-branch-1').textContent === 'v1.0.0 · detached HEAD'
              && document.getElementById('git-branch-2').textContent === 'feature/demo'
              && document.getElementById('project-table-body') === referenceRows
              && document.querySelector('input[value="1"]') === selectedCheckbox
              && selectedCheckbox.checked && selectedProjectIds.has('1');
            clickProject('2');

            requestGitStatus(false);
            const staleGit = requests.at(-1);
            htmxEvent('htmx:beforeRequest');
            checks.refreshStarted = !document.getElementById('workspace-refresh-overlay').classList.contains('hidden')
              && document.getElementById('workspace-refresh-button').disabled;
            checks.noDuplicateGlobalSpinner = document.getElementById('global-http-spinner')
              .getAttribute('aria-hidden') === 'true';
            await new Promise(resolve => setTimeout(resolve, 2100));
            checks.longRescanVisible = !document.getElementById('workspace-refresh-overlay').classList.contains('hidden');
            replaceRows();
            const refreshGit = requests.at(-1);
            checks.reconciled = !selectedProjectIds.has('1') && selectedProjectIds.has('2')
              && document.querySelector('input[value="2"]').checked
              && !document.querySelector('input[value="3"]').checked
              && document.getElementById('selected-status').textContent === '1 project selected';
            checks.gitPhaseVisible = !document.getElementById('workspace-refresh-overlay').classList.contains('hidden')
              && document.getElementById('workspace-refresh-phase').textContent.includes('Git');
            complete(staleGit, [{projectId: 2, branch: 'stale'}]);
            await flush();
            checks.staleIgnored = document.getElementById('git-branch-2').textContent === 'cached-new-2';
            complete(refreshGit, [{projectId: 2, branch: 'fresh'}, {projectId: 3, branch: null}]);
            await flush();
            checks.completed = document.getElementById('workspace-refresh-overlay').classList.contains('hidden')
              && !document.getElementById('workspace-refresh-button').disabled
              && document.getElementById('git-branch-2').textContent === 'fresh'
              && document.getElementById('git-branch-3').textContent === 'Unknown';
            executeBulkAction('git-fetch');
            await flush();
            checks.bulkSelected = JSON.stringify(bulkIds.at(-1)) === JSON.stringify(['2']);
            const all = document.getElementById('select-all');
            all.checked = true;
            toggleAllCheckboxes(all);
            executeBulkAction('git-fetch');
            await flush();
            checks.selectAllBulk = JSON.stringify(bulkIds.at(-1)) === JSON.stringify(['2', '3'])
              && document.getElementById('selected-status').textContent === '2 projects selected';
            checks.sortInitializedOncePerBody = sortableCount === 2;

            htmxEvent('htmx:beforeRequest');
            htmxEvent('htmx:responseError', {xhr: {responseText: 'failure'}});
            checks.rescanFailure = document.getElementById('workspace-refresh-overlay').classList.contains('hidden')
              && !document.getElementById('workspace-refresh-button').disabled
              && document.getElementById('workspace-refresh-status').textContent.includes('retry')
              && selectedProjectIds.has('2');

            htmxEvent('htmx:beforeRequest');
            replaceRows();
            const failingGit = requests.at(-1);
            failingGit.reject(new Error('git failed'));
            await flush();
            checks.gitFailure = document.getElementById('workspace-refresh-overlay').classList.contains('hidden')
              && document.getElementById('workspace-refresh-status').textContent.includes('could not be updated')
              && document.getElementById('git-branch-2').textContent === 'cached-new-2';

            htmxEvent('htmx:beforeRequest');
            replaceRows();
            complete(requests.at(-1), [{projectId: 2, branch: 'retry-ok'}]);
            await flush();
            checks.retry = document.getElementById('workspace-refresh-overlay').classList.contains('hidden')
              && document.getElementById('git-branch-2').textContent === 'retry-ok';
            window.htmx = undefined;
            form.dispatchEvent(new Event('submit', {bubbles: true, cancelable: true}));
            await flush();
            checks.normalFormFallbackSpinner = document.getElementById('global-http-spinner')
              .getAttribute('aria-hidden') === 'false';

            const projectName = "platform's <b>shared</b> & tools";
            for (const summary of [
              {action: 'build', success: true, succeededProjects: [projectName]},
              {action: 'build', success: false, failedProject: projectName, succeededProjects: [projectName]},
              {action: 'git-fetch', success: true, changedProjects: [projectName], noChangesProjects: [projectName]},
              {action: 'git-fetch', success: false, failedProjects: [projectName]}
            ]) {
              showActionSummary(summary);
            }
            const feedback = document.getElementById('action-summary-container');
            checks.summaryNamesAreText = !feedback.querySelector('b')
              && feedback.textContent.split(projectName).length === 7;
            checks.summaryDismissNamed = feedback.querySelectorAll('button[aria-label="Dismiss action summary"]').length === 4;
            checks.summaryIdsAreUnique = new Set(Array.from(feedback.children, toast => toast.id)).size === 4;

            appendLog(projectName, 'Build output');
            const tab = document.getElementById(`tab-${projectName}`);
            checks.logNamesAreText = tab.textContent.includes(projectName) && !tab.querySelector('b')
              && document.getElementById('log-container-Global').textContent.includes(projectName)
              && !document.querySelector('#log-container-Global b');
            const closeLog = tab.parentElement.querySelector('button[aria-label]');
            closeLog.focus();
            closeLog.click();
            checks.closeLogRestoresFocus = !document.getElementById(`tab-${projectName}`)
              && !document.querySelector('.project-log-tab')
              && document.activeElement.id === 'tab-Global'
              && document.activeElement.getAttribute('aria-pressed') === 'true';
            appendLog('second-project', 'Output');
            clearLogs();
            checks.clearLogsRemovesControls = !document.querySelector('.project-log-tab')
              && Object.keys(projectTabs).join() === 'Global';

            const orderBody = document.getElementById('project-table-body');
            Array.from(orderBody.children).forEach((row, index) => {
              row.insertAdjacentHTML('beforeend', `<td><span class="order-number">${index + 1}</span>
                <button data-move="-1"></button><button data-move="1"></button></td>`);
            });
            initializeProjectSorting();
            const firstRow = orderBody.children[0];
            const moveDown = firstRow.querySelector('[data-move="1"]');
            checks.orderBoundaries = firstRow.querySelector('[data-move="-1"]').disabled
              && orderBody.children[1].querySelector('[data-move="1"]').disabled;
            const movement = moveProject(moveDown, 1);
            await moveProject(moveDown, -1);
            checks.orderPendingGuard = moveDown.disabled && savedOrders.length === 1
              && orderBody.getAttribute('aria-busy') === 'true';
            pendingOrder({ok: true});
            await movement;
            checks.orderMoveSaved = savedOrders[0].join() === '3,2'
              && Array.from(orderBody.children, row => row.dataset.id).join() === '3,2'
              && firstRow.querySelector('.order-number').textContent === '2';
            const failure = moveProject(firstRow.querySelector('[data-move="-1"]'), -1);
            pendingOrder({ok: false});
            await failure;
            checks.orderFailureRestores = window.orderError
              && Array.from(orderBody.children, row => row.dataset.id).join() === '3,2'
              && firstRow.querySelector('.order-number').textContent === '2'
              && orderBody.getAttribute('aria-busy') === 'false';
            const keyEvent = {key: 'ArrowUp', preventDefault: () => { window.orderKeyPrevented = true; }};
            reorderProjectByKey(keyEvent, firstRow.querySelector('[data-move="-1"]'));
            pendingOrder({ok: true});
            await flush();
            checks.orderKeyboard = window.orderKeyPrevented && savedOrders.at(-1).join() === '2,3'
              && orderBody.children[0] === firstRow;
            document.getElementById('test-results').textContent = JSON.stringify(checks);
          }
          document.addEventListener('DOMContentLoaded', () => run().catch(error => {
            document.getElementById('test-results').textContent = JSON.stringify({error: String(error)});
          }));
        </script></body></html>"""


def main() -> None:
    """Execute the harness and fail with the results of any broken interaction."""
    with tempfile.TemporaryDirectory(prefix="workspace-refresh-browser-") as directory:
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
            raise AssertionError(f"Workspace browser regressions failed: {failed}")


if __name__ == "__main__":
    main()
