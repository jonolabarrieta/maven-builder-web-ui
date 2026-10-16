"""Check reference selection in Chrome using fragments rendered by real HTTP/Git tests.

First run WorkspaceGitReferenceTest with Maven, then run this script. The fixtures
in target/git-reference-browser contain the production Thymeleaf output. Only
navigation and transport are replaced; the production selector and confirmation
scripts handle the actual buttons, confirmation, loading and failure recovery.
"""

import html
import json
import re
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
FIXTURES = ROOT / "target/git-reference-browser"
CONFIRMATION = ROOT / "src/main/resources/static/js/confirmation-modal.js"


def build_harness(kind: str) -> str:
    fragment = (FIXTURES / f"{kind}.html").read_text(encoding="utf-8")
    # A successful reload is verified by independently rendered post-checkout
    # fixtures. Count navigation here so that it does not tear down this harness.
    fragment = fragment.replace("window.location.reload();", "window.recordedReloads++;")
    setup = """
        window.recordedReloads = 0;
        window.requests = [];
        window.fetch = (url, options) => new Promise((resolve, reject) => {
            requests.push({url, options, resolve, reject});
        });
    """
    checks = r"""
        async function run() {
            const checks = {};
            const flush = () => new Promise(resolve => setTimeout(resolve, 0));
            const choice = ref => document.querySelector('[data-ref="' + ref + '"]');
            const confirmButton = () => document.getElementById('confirmation-modal-primary');
            const message = () => document.getElementById('confirmation-modal-message');
            const latest = () => requests.at(-1);
            const expectPayload = (type, ref) => latest().url === '/projects/2/checkout'
                && latest().options.method === 'POST'
                && latest().options.globalSpinner === false
                && latest().options.body.get('branch') === ref
                && latest().options.body.get('referenceType') === type;

            const label = document.querySelector('#branch-modal-content-inner p[title]');
            if (document.body.dataset.kind === 'tag') {
                checks.currentTagLabel = label.textContent === 'v2.0.0 · detached HEAD';
                checks.allExactTagsActive = !!choice('refs/tags/v1.0.0').querySelector('svg')
                    && !!choice('refs/tags/v2.0.0').querySelector('svg');
                checks.localNotActiveForTag = !choice('refs/heads/main').querySelector('svg');
            } else if (document.body.dataset.kind === 'remote') {
                checks.currentTrackingBranchLabel = label.textContent === 'feature/demo';
                checks.localAndUpstreamActive = !!choice('refs/heads/feature/demo').querySelector('svg')
                    && !!choice('refs/remotes/origin/feature/demo').querySelector('svg');
            }

            switchBranchTab('remote');
            checks.tabSwitch = !document.getElementById('branch-panel-remote').classList.contains('hidden')
                && document.getElementById('branch-panel-local').classList.contains('hidden');
            choice('refs/remotes/origin/feature/demo').click();
            checks.remoteConfirmation = message().textContent.includes('local branch "feature/demo"')
                && message().textContent.includes('tracking "origin/feature/demo"')
                && message().textContent.includes('does not pull or reset');
            checks.noRequestBeforeConfirmation = requests.length === 0;
            document.getElementById('confirmation-modal-cancel').click();
            checks.cancelDoesNotCheckout = requests.length === 0
                && document.getElementById('confirmation-modal-overlay').style.display === 'none';

            choice('refs/remotes/origin/feature/demo').click();
            confirmButton().click();
            checks.remotePayload = expectPayload('REMOTE', 'refs/remotes/origin/feature/demo');
            checks.loadingUntilResponse = !document.getElementById('branch-loading').classList.contains('hidden')
                && document.getElementById('branch-list-container').classList.contains('hidden')
                && recordedReloads === 0;
            latest().resolve({ok: true});
            await flush();
            checks.successReloads = recordedReloads === 1;

            resetBranchLoading();
            switchBranchTab('tags');
            choice('refs/tags/v1.0.0').click();
            checks.tagConfirmationExplainsDetached = message().textContent.includes('tag "v1.0.0"')
                && message().textContent.includes('HEAD detached');
            confirmButton().click();
            checks.tagPayload = expectPayload('TAG', 'refs/tags/v1.0.0');
            latest().resolve({ok: false, text: async () => 'Local changes would be overwritten'});
            await flush();
            checks.failureRecovers = document.getElementById('branch-loading').classList.contains('hidden')
                && !document.getElementById('branch-list-container').classList.contains('hidden')
                && message().textContent === 'Local changes would be overwritten'
                && recordedReloads === 1;
            confirmButton().click();

            switchBranchTab('local');
            choice('refs/heads/main').click();
            checks.localConfirmation = message().textContent.includes('local branch "main"')
                && !message().textContent.includes('detached');
            confirmButton().click();
            checks.localPayload = expectPayload('LOCAL', 'refs/heads/main');
            latest().reject(new Error('Connection unavailable'));
            await flush();
            checks.transportFailureRecovers = message().textContent === 'Connection unavailable'
                && document.getElementById('branch-loading').classList.contains('hidden');
            confirmButton().click();

            if (document.body.dataset.kind === 'escaped') {
                const escaped = Array.from(document.querySelectorAll('[data-ref-type="TAG"]'))
                    .find(button => button.dataset.refName.includes('<img>'));
                escaped.click();
                checks.escapedReferenceIsText = message().textContent.includes('v3\'"<img>')
                    && !message().querySelector('img') && !escaped.querySelector('img');
                confirmButton().click();
                checks.escapedIdentityPreserved = latest().options.body.get('branch') === 'refs/tags/v3\'"<img>';
                latest().resolve({ok: true});
                await flush();
            }
            document.getElementById('test-results').textContent = JSON.stringify(checks);
        }
        document.addEventListener('DOMContentLoaded', () => run().catch(error => {
            document.getElementById('test-results').textContent = JSON.stringify({error: String(error)});
        }));
    """
    return f"""<!doctype html><html><head><meta charset="UTF-8">
        <style>.hidden {{ display: none; }}</style></head><body data-kind="{kind}">
        <script>{setup}</script><script>{CONFIRMATION.read_text(encoding='utf-8')}</script>
        {fragment}<pre id="test-results"></pre><script>{checks}</script></body></html>"""


def main() -> None:
    results = {}
    with tempfile.TemporaryDirectory(prefix="git-reference-browser-") as directory:
        for kind in ("remote", "tag", "escaped"):
            page = Path(directory) / f"{kind}.html"
            page.write_text(build_harness(kind), encoding="utf-8")
            output = subprocess.run([
                "google-chrome", "--headless", "--no-sandbox", "--disable-dev-shm-usage",
                "--disable-gpu", "--disable-background-networking",
                f"--user-data-dir={directory}/profile-{kind}", "--virtual-time-budget=3500",
                "--dump-dom", page.as_uri(),
            ], capture_output=True, text=True, timeout=20, check=True)
            match = re.search(r'<pre id="test-results">(.*?)</pre>', output.stdout, re.S)
            if match is None:
                raise AssertionError(f"No browser result for {kind}: {output.stderr[-1000:]}")
            checks = json.loads(html.unescape(match.group(1)))
            results[kind] = checks
            if any(value is not True for value in checks.values()):
                raise AssertionError(json.dumps(results, indent=2))
    print(json.dumps(results, indent=2))


if __name__ == "__main__":
    main()
