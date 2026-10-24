"""Exercise the build-log follow toggle and wheel-close in headless Chrome.

The harness embeds the production workspace page script, stubs transport and a
small in-memory localStorage, and drives the follow toggle, the follow behavior,
and middle-click tab closing. Chrome must be installed and runnable locally.
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
        <button type="button" id="log-follow-toggle" aria-pressed="false">
          <span id="log-follow-indicator">Off</span></button>
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
          <tr data-id="1"><td><input class="project-checkbox" value="1" type="checkbox"></td></tr>
        </tbody></table>
        <pre id="test-results"></pre>
        <script>
          window.htmx = {};
          window.Sortable = class { constructor() {} };
          window.SockJS = class { constructor() {} };
          window.Stomp = {over: () => ({connect: () => {}, subscribe: () => {}})};
          window.requests = [];
          const store = {};
          Object.defineProperty(window, 'localStorage', { configurable: true, value: {
            getItem: key => (Object.prototype.hasOwnProperty.call(store, key) ? store[key] : null),
            setItem: (key, value) => { store[key] = String(value); },
            removeItem: key => { delete store[key]; },
            clear: () => { Object.keys(store).forEach(key => delete store[key]); }
          }});
          window.fetch = (url, options = {}) => new Promise((resolve, reject) => {
            window.requests.push({url, options, resolve, reject});
          });
          window.onerror = message => {
            document.getElementById('test-results').textContent = JSON.stringify({error: String(message)});
          };
        </script>
        <script>""" + global_spinner_script + """</script>
        <script>""" + page_script + """</script>
        <script>
          const checks = {};
          const flush = () => new Promise(resolve => setTimeout(resolve, 5));
          const toggle = () => document.getElementById('log-follow-toggle');
          const indicator = () => document.getElementById('log-follow-indicator');
          const tabGroupFor = label => document.getElementById('tab-' + label)?.closest('.project-log-tab');
          const middle = (type, target) => {
            const event = new MouseEvent(type, {button: 1, bubbles: true, cancelable: true});
            target.dispatchEvent(event);
            return event;
          };

          async function run() {
            await flush();

            // Default: off.
            checks.defaultOff = toggle().getAttribute('aria-pressed') === 'false'
              && indicator().textContent === 'Off' && followBuild === false;

            // The first project tab still opens (existing behavior preserved).
            switchTab('Global');
            appendLog('projA', 'first');
            checks.firstTabOpens = activeTab === 'projA';

            // Follow off: an existing project's output does not steal the active tab.
            appendLog('projB', 'b');
            switchTab('Global');
            appendLog('projA', 'again');
            checks.followOffDoesNotSwitch = activeTab === 'Global';

            // Global lines never switch the active tab.
            switchTab('projB');
            appendLog('Global', 'global-line');
            checks.globalDoesNotSwitch = activeTab === 'projB';

            // Enabling persists and survives a simulated reload.
            toggleLogFollow();
            checks.enabledAndPersisted = followBuild === true
              && toggle().getAttribute('aria-pressed') === 'true'
              && indicator().textContent === 'On'
              && localStorage.getItem('mvnbuilder.buildLogFollow') === 'true';
            setLogFollow(readFollowPreference());
            checks.rememberedAcrossReload = followBuild === true;

            // Follow on: an existing project's output switches to it.
            switchTab('Global');
            appendLog('projA', 'follow me');
            checks.followOnSwitches = activeTab === 'projA';

            // Global lines still do not steal with follow on.
            appendLog('Global', 'g2');
            checks.globalNoStealWithFollow = activeTab === 'projA';

            // Middle button suppresses autoscroll and closes the tab.
            const groupA = tabGroupFor('projA');
            checks.middleDownPreventsAutoscroll = middle('mousedown', groupA).defaultPrevented === true;
            middle('auxclick', groupA);
            checks.middleClickCloses = !document.getElementById('tab-projA')
              && !document.getElementById('log-container-projA');
            checks.fallsBackToGlobal = activeTab === 'Global';

            // The close (x) button still works.
            appendLog('projC', 'c');
            tabGroupFor('projC').querySelector('button[aria-label^="Close log"]').click();
            checks.closeButtonWorks = !document.getElementById('tab-projC');

            document.getElementById('test-results').textContent = JSON.stringify(checks);
          }
          document.addEventListener('DOMContentLoaded', () => run().catch(error => {
            document.getElementById('test-results').textContent = JSON.stringify({error: String(error)});
          }));
        </script></body></html>"""


def main() -> None:
    """Execute the harness and fail with the results of any broken interaction."""
    with tempfile.TemporaryDirectory(prefix="build-log-tabs-browser-") as directory:
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
            raise AssertionError(f"Build log tab browser regressions failed: {failed}")


if __name__ == "__main__":
    main()
