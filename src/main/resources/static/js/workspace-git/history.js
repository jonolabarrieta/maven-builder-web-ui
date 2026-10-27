/* Workspace Git page: recent commits of the current branch with read-only per-file comparisons. */
(App => {
    'use strict';
    const { $, state, node, repoUrl } = App;
    const { request, guard } = App;
    const PAGE = 30;
    const relative = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });
    const view = { open: false, repository: null, commits: [], more: false, upstream: false, expanded: null, files: new Map() };
    function when(seconds) {
        const minutes = Math.round((seconds * 1000 - Date.now()) / 60000);
        if (Math.abs(minutes) < 60) return relative.format(minutes, 'minute');
        const hours = Math.round(minutes / 60);
        if (Math.abs(hours) < 48) return relative.format(hours, 'hour');
        const days = Math.round(hours / 24);
        return Math.abs(days) < 60 ? relative.format(days, 'day') : new Date(seconds * 1000).toLocaleDateString();
    }
    async function loadPage(reset) {
        const id = state.repository; if (!id) return;
        if (reset) Object.assign(view, { repository: id, commits: [], more: false, expanded: null, files: new Map() });
        const page = await request(`${repoUrl(id)}/log?skip=${view.commits.length}&limit=${PAGE}`);
        if (state.repository !== id || !view.open) return;
        view.commits.push(...page.commits); view.more = page.more; view.upstream = page.upstream; render();
    }
    async function toggleCommit(sha) {
        view.expanded = view.expanded === sha ? null : sha;
        if (view.expanded && !view.files.has(sha)) { view.files.set(sha, 'loading'); render(); view.files.set(sha, await request(`${repoUrl(view.repository)}/commits/${sha}/files`)); }
        render();
    }
    /** Shows one file of a commit in the comparison pane; history comparisons have no staging controls. */
    async function chooseCommitFile(commit, file) {
        if (!(await App.leaveEditor())) return;
        const id = view.repository; App.cancelComparison(); const generation = ++state.comparisonGeneration;
        state.file = { path: file.path, area: 'commit', sha: commit.sha }; state.diff = null; state.lines.clear();
        $('git-file-name').textContent = file.path; $('git-comparison-label').textContent = `Commit ${commit.shortSha} · ${commit.subject}`;
        App.renderRepositories([id]); document.body.classList.add('git-show-diff');
        const old = file.oldPath ? `&oldPath=${encodeURIComponent(file.oldPath)}` : '';
        const diff = await request(`${repoUrl(id)}/commits/${commit.sha}/diff?path=${encodeURIComponent(file.path)}${old}`);
        if (generation !== state.comparisonGeneration || state.repository !== id) return;
        state.diff = diff; await App.renderDiff(); App.renderToolbar(); render();
    }
    function render() {
        const list = $('git-history-list'); const items = [];
        $('git-history-upstream').textContent = view.upstream ? 'Unpushed commits are marked.' : 'No upstream: push state is unknown.';
        for (const commit of view.commits) {
            const item = node('li', 'git-history-commit');
            const button = node('button'); button.type = 'button'; button.setAttribute('aria-expanded', String(view.expanded === commit.sha));
            button.append(node('code', '', commit.shortSha), node('span', 'git-history-subject', commit.subject), node('span', 'git-history-meta', `${commit.author} · ${when(commit.time)}`));
            if (view.upstream && !commit.pushed) button.append(node('span', 'git-history-badge', 'not pushed'));
            button.addEventListener('click', () => guard(() => toggleCommit(commit.sha)));
            item.append(button);
            const files = view.expanded === commit.sha ? view.files.get(commit.sha) : null;
            if (files === 'loading') item.append(node('p', 'git-folder-state', 'Loading files…'));
            else if (files) {
                const fileList = node('ul', 'git-history-files');
                files.forEach(file => {
                    const entry = node('li'); const open = node('button'); open.type = 'button';
                    open.setAttribute('aria-pressed', String(state.file?.area === 'commit' && state.file.sha === commit.sha && state.file.path === file.path));
                    open.append(node('span', 'git-file-status', file.status), node('span', '', file.oldPath ? `${file.oldPath} → ${file.path}` : file.path));
                    open.addEventListener('click', () => guard(() => chooseCommitFile(commit, file)));
                    entry.append(open); fileList.append(entry);
                });
                item.append(fileList);
            }
            items.push(item);
        }
        if (!items.length) items.push(node('li', 'git-empty', view.repository ? 'No commits on this branch yet.' : 'Choose a repository.'));
        list.replaceChildren(...items);
        $('git-history-more').hidden = !view.more;
    }
    async function toggleHistory() {
        view.open = !view.open;
        $('git-history').hidden = !view.open; $('git-history-toggle').setAttribute('aria-expanded', String(view.open));
        if (view.open) await loadPage(true);
    }
    /** Called when the active repository changes or after a commit, so an open panel shows current history. */
    async function refreshHistory() { if (view.open) await loadPage(true); }
    function initHistory() {
        $('git-history-toggle').addEventListener('click', () => guard(toggleHistory));
        $('git-history-more').addEventListener('click', () => guard(() => loadPage(false)));
    }
    Object.assign(App, { initHistory, toggleHistory, refreshHistory, chooseCommitFile, historyView: view });
})(window.WorkspaceGitApp = window.WorkspaceGitApp || {});
