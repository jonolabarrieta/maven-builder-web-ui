"""Exercise the persisted notification history panel in headless Chrome.

The harness embeds the production workspace page script and the production
confirmation dialog, stubs only transport, and drives the bell, badge, history
list, mark-read, live update, and clear-all flows. Chrome must be installed.
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
    # Recovery is tested independently; these fixtures isolate the older page concerns.
    page_script = "window.BuildRecovery = {init(){}, reset(){}, show(){}};\n" + page_script
    global_spinner_script = GLOBAL_SPINNER.read_text(encoding="utf-8")
    confirmation_script = CONFIRMATION.read_text(encoding="utf-8")
    return """<!doctype html><html><body>
        <button type="button" id="notification-bell" onclick="toggleNotificationPanel()">
          <span id="notification-badge">0</span></button>
        <div id="notification-panel" class="hidden" aria-hidden="true" role="dialog">
          <button id="notification-close" onclick="closeNotificationPanel()"></button>
          <button id="notification-clear" onclick="clearNotifications()"></button>
          <div id="notification-list"></div>
          <p id="notification-empty" style="display:none"></p>
        </div>
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
        </tbody></table>
        <pre id="test-results"></pre>
        <script>
          window.sortableCount = 0;
          window.htmx = {};
          window.Sortable = class { constructor() { window.sortableCount++; } };
          window.SockJS = class { constructor() {} };
          window.Stomp = {over: () => ({connect: () => {}, subscribe: () => {}})};
          window.requests = [];
          window.notificationRequests = [];
          window.unreadCount = 2;
          const now = new Date().toISOString();
          window.storedNotifications = [
            {id: 1, createdAt: now, category: 'ACTION', action: 'build', success: true,
             title: 'Build result', message: 'Build completed.', workspaceId: 1, workspaceName: 'WS',
             failedProjects: [], succeededProjects: ['mod-a'], changedProjects: [], noChangesProjects: [], read: false},
            {id: 2, createdAt: now, category: 'LOCAL', action: null, success: false,
             title: 'Action failed', message: '<img src=x onerror=alert(1)>', workspaceId: 1, workspaceName: 'WS',
             failedProjects: ['<b>bad</b>'], succeededProjects: [], changedProjects: [], noChangesProjects: [], read: false}
          ];
          const respond = data => Promise.resolve({ok: true, json: async () => data});
          window.fetch = (url, options = {}) => {
            const method = (options.method || 'GET').toUpperCase();
            if (url.includes('/notifications')) {
              window.notificationRequests.push({url, method});
              if (url.includes('/notifications/unread-count')) return respond({count: window.unreadCount});
              if (url.includes('/notifications/read')) return respond('OK');
              if (url.includes('/notifications/local')) return respond({});
              if (method === 'DELETE') { window.storedNotifications = []; return respond('OK'); }
              return respond(window.storedNotifications);
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
          const flush = () => new Promise(resolve => setTimeout(resolve, 5));
          const badge = () => document.getElementById('notification-badge');
          const list = () => document.getElementById('notification-list');
          const empty = () => document.getElementById('notification-empty');
          const panel = () => document.getElementById('notification-panel');
          const countRequests = (fragment, method) => notificationRequests.filter(
            request => request.url.includes(fragment) && (!method || request.method === method)).length;

          async function run() {
            await flush();

            // The badge reflects the unread count loaded on start.
            checks.badgeFromStoredHistory = badge().textContent === '2'
              && badge().style.display === 'inline-block';

            // Opening the panel lists stored history and clears the badge.
            document.getElementById('notification-bell').click();
            await flush();
            checks.panelOpens = panel().classList.contains('hidden') === false
              && panel().getAttribute('aria-hidden') === 'false';
            checks.historyRendered = list().children.length === 2
              && list().textContent.includes('Build result')
              && list().textContent.includes('Action failed');
            checks.markedRead = countRequests('/notifications/read', 'POST') === 1
              && badge().style.display === 'none';
            checks.untrustedTextEscaped = list().querySelector('img') === null
              && list().querySelector('b') === null
              && list().textContent.includes('<img src=x onerror=alert(1)>')
              && list().textContent.includes('<b>bad</b>');

            // A live notification while the panel is open refreshes the list.
            const beforeLive = countRequests('/notifications', 'GET');
            window.storedNotifications = window.storedNotifications.concat([{
              id: 3, createdAt: new Date().toISOString(), category: 'ACTION', action: 'git-push',
              success: true, title: 'Git Push result', message: 'Git Push finished.', workspaceId: 1,
              workspaceName: 'WS', failedProjects: [], succeededProjects: [], changedProjects: [], noChangesProjects: [], read: false
            }]);
            handleLiveNotification();
            await flush();
            checks.liveRefreshWhileOpen = list().children.length === 3
              && countRequests('/notifications', 'GET') === beforeLive + 1;

            // Closed, a live notification only refreshes the badge.
            closeNotificationPanel();
            window.unreadCount = 5;
            handleLiveNotification();
            await flush();
            checks.liveBadgeWhileClosed = badge().textContent === '5'
              && badge().style.display === 'inline-block';

            // Local messages post to the server.
            showLocalActionMessage('Could not start the action', true);
            await flush();
            checks.localMessagePosted = countRequests('/notifications/local', 'POST') === 1
              && document.getElementById('action-summary-container').textContent.includes('Could not start the action');

            // Clear all empties the list and shows the empty state.
            document.getElementById('notification-bell').click();
            await flush();
            document.getElementById('notification-clear').click();
            await flush();
            checks.clearAll = list().children.length === 0
              && empty().style.display === 'block'
              && empty().textContent.includes('No notifications')
              && badge().style.display === 'none';

            document.getElementById('test-results').textContent = JSON.stringify(checks);
          }
          document.addEventListener('DOMContentLoaded', () => run().catch(error => {
            document.getElementById('test-results').textContent = JSON.stringify({error: String(error)});
          }));
        </script></body></html>"""


def main() -> None:
    """Execute the harness and fail with the results of any broken interaction."""
    with tempfile.TemporaryDirectory(prefix="notification-history-browser-") as directory:
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
            raise AssertionError(f"Notification history browser regressions failed: {failed}")


if __name__ == "__main__":
    main()
