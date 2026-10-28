/* Workspace Git page: recent commits of the current branch with read-only per-file comparisons, in a panel and a log window. */
(App => {
    'use strict';
    const { $, state, node, repoUrl, repoById, repoName } = App;
    const { request, guard } = App;
    const PAGE = 30;
    const relative = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });
    function when(seconds) {
        const minutes = Math.round((seconds * 1000 - Date.now()) / 60000);
        if (Math.abs(minutes) < 60) return relative.format(minutes, 'minute');
        const hours = Math.round(minutes / 60);
        if (Math.abs(hours) < 48) return relative.format(hours, 'hour');
        const days = Math.round(hours / 24);
        return Math.abs(days) < 60 ? relative.format(days, 'day') : new Date(seconds * 1000).toLocaleDateString();
    }
    /**
     * One paged commit list bound to its own elements. {@code onFile(view, commit, file)} shows a file of a commit;
     * {@code isOpenFile(view, commit, file)} marks the file currently shown.
     */
    function commitList({ list, upstream, more, onFile, isOpenFile }) {
        const view = { open: false, repository: null, commits: [], more: false, upstream: false, expanded: null, files: new Map() };
        async function loadPage(reset, id = view.repository) {
            if (!id) return;
            if (reset) Object.assign(view, { repository: id, commits: [], more: false, expanded: null, files: new Map() });
            const page = await request(`${repoUrl(id)}/log?skip=${view.commits.length}&limit=${PAGE}`);
            if (view.repository !== id || !view.open) return;
            view.commits.push(...page.commits); view.more = page.more; view.upstream = page.upstream; render();
        }
        async function toggleCommit(sha) {
            view.expanded = view.expanded === sha ? null : sha;
            if (view.expanded && !view.files.has(sha)) { view.files.set(sha, 'loading'); render(); view.files.set(sha, await request(`${repoUrl(view.repository)}/commits/${sha}/files`)); }
            render();
        }
        function render() {
            const items = [];
            upstream.textContent = view.upstream ? 'Unpushed commits are marked.' : 'No upstream: push state is unknown.';
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
                        open.setAttribute('aria-pressed', String(isOpenFile(view, commit, file)));
                        open.append(node('span', 'git-file-status', file.status), node('span', '', file.oldPath ? `${file.oldPath} → ${file.path}` : file.path));
                        open.addEventListener('click', () => guard(() => onFile(view, commit, file)));
                        entry.append(open); fileList.append(entry);
                    });
                    item.append(fileList);
                }
                items.push(item);
            }
            if (!items.length) items.push(node('li', 'git-empty', view.repository ? 'No commits on this branch yet.' : 'Choose a repository.'));
            list.replaceChildren(...items);
            more.hidden = !view.more;
        }
        more.addEventListener('click', () => guard(() => loadPage(false)));
        return { view, loadPage, render };
    }

    // History panel: files open in the main comparison pane.
    const panel = commitList({
        list: $('git-history-list'), upstream: $('git-history-upstream'), more: $('git-history-more'),
        onFile: (view, commit, file) => chooseCommitFile(commit, file),
        isOpenFile: (view, commit, file) => state.file?.area === 'commit' && state.file.sha === commit.sha && state.file.path === file.path
    });
    /** Shows one file of a commit in the comparison pane; history comparisons have no staging controls. */
    async function chooseCommitFile(commit, file) {
        if (!(await App.leaveEditor())) return;
        const id = panel.view.repository; App.cancelComparison(); const generation = ++state.comparisonGeneration;
        state.file = { path: file.path, area: 'commit', sha: commit.sha }; state.diff = null; state.lines.clear();
        $('git-file-name').textContent = file.path; $('git-comparison-label').textContent = `Commit ${commit.shortSha} · ${commit.subject}`;
        App.renderRepositories([id]); document.body.classList.add('git-show-diff');
        const old = file.oldPath ? `&oldPath=${encodeURIComponent(file.oldPath)}` : '';
        const diff = await request(`${repoUrl(id)}/commits/${commit.sha}/diff?path=${encodeURIComponent(file.path)}${old}`);
        if (generation !== state.comparisonGeneration || state.repository !== id) return;
        state.diff = diff; await App.renderDiff(); App.renderToolbar(); panel.render();
    }
    async function toggleHistory() {
        panel.view.open = !panel.view.open;
        $('git-history').hidden = !panel.view.open; $('git-history-toggle').setAttribute('aria-expanded', String(panel.view.open));
        if (panel.view.open) await panel.loadPage(true, state.repository);
    }
    /** Called when the active repository changes or after a commit, so an open panel shows current history. */
    async function refreshHistory() { if (panel.view.open) await panel.loadPage(true, state.repository); }

    // Log window: files open in a read-only unified comparison inside the window.
    let logFile = null; let logGeneration = 0;
    const log = commitList({
        list: $('git-log-list'), upstream: $('git-log-upstream'), more: $('git-log-more'),
        onFile: (view, commit, file) => showLogFile(view.repository, commit, file),
        isOpenFile: (view, commit, file) => logFile?.sha === commit.sha && logFile.path === file.path
    });
    function logLine(line) {
        const row = node('div', `git-log-line${line.type === '+' ? ' is-added' : line.type === '-' ? ' is-deleted' : ''}`);
        row.append(node('span', 'git-log-number', line.oldNumber ?? ''), node('span', 'git-log-number', line.newNumber ?? ''),
            node('span', 'git-log-sign', line.type === ' ' ? '' : line.type), node('span', 'git-log-text', line.text));
        return row;
    }
    async function showLogFile(id, commit, file) {
        const generation = ++logGeneration; logFile = { sha: commit.sha, path: file.path }; log.render();
        $('git-log-file').textContent = `${file.oldPath ? `${file.oldPath} → ` : ''}${file.path} · ${commit.shortSha} ${commit.subject}`;
        $('git-log-diff').replaceChildren(node('p', 'git-folder-state', 'Loading comparison…'));
        const old = file.oldPath ? `&oldPath=${encodeURIComponent(file.oldPath)}` : '';
        const diff = await request(`${repoUrl(id)}/commits/${commit.sha}/diff?path=${encodeURIComponent(file.path)}${old}`);
        if (generation !== logGeneration) return;
        const rows = diff.kind === 'text' && diff.lines.length ? diff.lines.map(logLine) : [node('p', 'git-empty', diff.reason || 'No text differences to show.')];
        $('git-log-diff').replaceChildren(...rows);
    }
    async function openLog() {
        const repo = repoById(state.repository); if (!repo) return;
        logFile = null; logGeneration++; log.view.open = true;
        $('git-log-title').textContent = `Log · ${repoName(repo)}${state.statuses.get(repo.id)?.branch ? ` · ${state.statuses.get(repo.id).branch}` : ''}`;
        $('git-log-title').title = repo.absolutePath;
        $('git-log-file').textContent = 'Select a file of a commit to compare it with its parent.'; $('git-log-diff').replaceChildren();
        $('git-log-list').replaceChildren(node('li', 'git-folder-state', 'Loading commits…'));
        if (!$('git-log-dialog').open) $('git-log-dialog').showModal();
        await log.loadPage(true, repo.id);
    }
    function initHistory() { $('git-history-toggle').addEventListener('click', () => guard(toggleHistory)); }
    function initLog() {
        $('git-log-open').addEventListener('click', () => guard(openLog));
        $('git-log-close').addEventListener('click', () => $('git-log-dialog').close());
        $('git-log-dialog').addEventListener('close', () => { log.view.open = false; logGeneration++; });
        // Reaching the end of the list loads older commits.
        $('git-log-list').parentElement.addEventListener('scroll', event => {
            const box = event.currentTarget;
            if (log.view.more && box.scrollTop + box.clientHeight >= box.scrollHeight - 24 && !box.dataset.loading) {
                box.dataset.loading = 'true'; guard(() => log.loadPage(false)).finally(() => { delete box.dataset.loading; });
            }
        });
    }
    Object.assign(App, { initHistory, initLog, openLog, toggleHistory, refreshHistory, chooseCommitFile, historyView: panel.view, logView: log.view });
})(window.WorkspaceGitApp = window.WorkspaceGitApp || {});
