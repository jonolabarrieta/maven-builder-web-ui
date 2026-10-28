/* Workspace Git page: synchronization overview of commits to push and pull across repositories and branches. */
(App => {
    'use strict';
    const { $, base, state, node, announce } = App;
    const { request, guard } = App;
    const relative = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });
    const SIZE = 'mvnbuilder.git.syncSize';
    let summary = null;
    /** Repositories whose other branches are shown; the current branch is always in the header row. */
    const expanded = new Set();
    function age(timestamp) {
        if (!timestamp) return 'Never fetched';
        const minutes = Math.round((timestamp - Date.now()) / 60000);
        if (Math.abs(minutes) < 60) return relative.format(minutes, 'minute');
        const hours = Math.round(minutes / 60);
        return Math.abs(hours) < 48 ? relative.format(hours, 'hour') : relative.format(Math.round(hours / 24), 'day');
    }
    const stale = repository => !repository.lastFetch || Date.now() - repository.lastFetch > state.staleFetchHours * 3600000;
    const current = repository => repository.branches.find(branch => branch.current);
    const name = repository => repository.displayName || repository.name;
    const STATUS = {
        all: () => true,
        ahead: (repository, branch) => branch.ahead > 0,
        behind: (repository, branch) => branch.behind > 0,
        diverged: (repository, branch) => branch.ahead > 0 && branch.behind > 0,
        'no-upstream': (repository, branch) => !branch.upstream,
        changes: (repository, branch) => repository.changes > 0 && branch.current
    };
    /** Status, repository-name and branch-name filters combine; a repository is shown when any of its branches match. */
    function filtered() {
        const status = STATUS[$('git-sync-filter').value] || STATUS.all;
        const repositoryTerm = $('git-sync-repository-filter').value.trim().toLowerCase();
        const branchTerm = $('git-sync-branch-filter').value.trim().toLowerCase();
        const shown = [];
        for (const repository of summary?.repositories || []) {
            if (repositoryTerm && !name(repository).toLowerCase().includes(repositoryTerm)) continue;
            if (repository.error) { if (!branchTerm && $('git-sync-filter').value === 'all') shown.push({ repository, branches: [] }); continue; }
            const branches = repository.branches.filter(branch => status(repository, branch) && (!branchTerm || branch.name.toLowerCase().includes(branchTerm)));
            if (branches.length) shown.push({ repository, branches });
        }
        return shown;
    }
    function count(value, label) {
        const cell = node('td', 'git-sync-count');
        if (value === null || value === undefined) cell.textContent = '—';
        else { cell.textContent = String(value); if (value > 0) cell.classList.add(label); }
        return cell;
    }
    function upstreamCell(branch) {
        const cell = node('td', 'git-sync-upstream', !branch ? '—' : branch.gone ? `${branch.upstream} (gone)` : branch.upstream || 'No upstream');
        if (branch?.ahead > 0 && branch.behind > 0) cell.title = 'Fast-forward pull will not apply; pull with rebase or merge.';
        return cell;
    }
    function badges(cell, branch) {
        if (branch.ahead > 0 && branch.behind > 0) cell.append(node('span', 'git-sync-badge is-diverged', 'diverged'));
        if (!branch.upstream) cell.append(node('span', 'git-sync-badge', 'no upstream'));
    }
    const filtering = () => $('git-sync-filter').value !== 'all' || Boolean($('git-sync-branch-filter').value.trim());
    function headerRow(repository, matching) {
        const row = node('tr', 'git-sync-repository-row'); row.dataset.repository = repository.id;
        const head = current(repository);
        const cell = node('td', 'git-sync-repository');
        const others = repository.branches.filter(branch => !branch.current);
        const toggle = App.disclosure(`Show other branches of ${name(repository)}`); toggle.classList.add('git-sync-toggle');
        toggle.hidden = !others.length;
        // Under a status or branch filter, repositories whose other branches match open by themselves.
        const open = expanded.has(repository.id) || (filtering() && matching.some(branch => !branch.current));
        toggle.setAttribute('aria-expanded', String(open));
        toggle.setAttribute('aria-label', `${open ? 'Hide' : 'Show'} ${others.length} other ${others.length === 1 ? 'branch' : 'branches'} of ${name(repository)}`);
        toggle.addEventListener('click', () => { expanded.has(repository.id) ? expanded.delete(repository.id) : expanded.add(repository.id); render(); });
        const title = node('strong', '', name(repository)); title.title = repository.absolutePath;
        const label = repository.branch === 'detached HEAD' ? 'detached' : repository.branch || head?.name || 'unknown';
        const badge = node('span', `git-branch-badge${repository.branch === 'detached HEAD' ? ' is-detached' : ''}`, label); badge.title = `Current branch: ${label}`;
        cell.append(toggle, title, badge);
        if (head) badges(cell, head);
        if (others.length) cell.append(node('span', 'git-sync-others', `+${others.length} ${others.length === 1 ? 'branch' : 'branches'}`));
        const fetched = node('td', stale(repository) ? 'git-sync-fetch is-stale' : 'git-sync-fetch', repository.lastFetch ? age(repository.lastFetch) : 'No fetch recorded');
        if (stale(repository)) fetched.title = 'Counts may be outdated. Fetch to update them.';
        row.append(cell, upstreamCell(head), count(head?.ahead, 'is-ahead'), count(head?.behind, 'is-behind'),
            node('td', 'git-sync-count', repository.changes ? String(repository.changes) : ''), fetched);
        return { row, open };
    }
    function branchRow(repository, branch) {
        const row = node('tr', 'git-sync-branch-row'); row.dataset.repository = repository.id; row.dataset.branch = branch.name;
        const cell = node('td', 'git-sync-branch'); cell.append(node('span', '', branch.name)); badges(cell, branch);
        row.append(cell, upstreamCell(branch), count(branch.ahead, 'is-ahead'), count(branch.behind, 'is-behind'), node('td'), node('td'));
        return row;
    }
    function render() {
        const rows = []; const shown = filtered();
        for (const { repository, branches } of shown) {
            if (repository.error) { const row = node('tr', 'git-sync-error'); const cell = node('td', '', `${name(repository)}: ${repository.error}`); cell.colSpan = 6; cell.title = repository.absolutePath; row.append(cell); rows.push(row); continue; }
            const header = headerRow(repository, branches); rows.push(header.row);
            if (!header.open) continue;
            // Under a status or branch filter only the matching other branches are listed.
            (filtering() ? branches : repository.branches).filter(branch => !branch.current).forEach(branch => rows.push(branchRow(repository, branch)));
        }
        if (!rows.length) { const row = node('tr'); const cell = node('td', 'git-empty', summary ? 'No repositories match these filters.' : 'Loading…'); cell.colSpan = 6; row.append(cell); rows.push(row); }
        $('git-sync-rows').replaceChildren(...rows);
        const targets = bulkTargets();
        $('git-sync-fetch').textContent = `Fetch all (${targets.fetch.length})`; $('git-sync-fetch').disabled = !targets.fetch.length;
        $('git-sync-pull').textContent = `Pull behind (${targets.pull.length})`; $('git-sync-pull').disabled = !targets.pull.length;
        $('git-sync-push').textContent = `Push ahead (${targets.push.length})`; $('git-sync-push').disabled = !targets.push.length;
    }
    /** Bulk actions only target the current branch of each repository the filters show. */
    function bulkTargets() {
        const repositories = filtered().map(entry => entry.repository).filter(repository => !repository.error);
        return {
            fetch: repositories.map(repository => repository.id),
            pull: repositories.filter(repository => current(repository)?.behind > 0).map(repository => repository.id),
            push: repositories.filter(repository => current(repository)?.ahead > 0).map(repository => repository.id)
        };
    }
    async function loadSummary() {
        $('git-sync-dialog').setAttribute('aria-busy', 'true');
        try { summary = await request(`${base}/sync`); render(); }
        finally { $('git-sync-dialog').setAttribute('aria-busy', 'false'); }
    }
    async function openSync() {
        summary = null; render();
        if (!$('git-sync-dialog').open) { $('git-sync-dialog').showModal(); opened = measure(); }
        await loadSummary();
    }
    async function bulk(action) {
        const ids = bulkTargets()[action];
        if (!ids.length) return;
        const buttons = ['git-sync-fetch', 'git-sync-pull', 'git-sync-push'].map(id => $(id)); buttons.forEach(button => { button.disabled = true; });
        try { await App.runAction(action, ids, null); }
        finally { await loadSummary(); announce(`${action} finished for ${ids.length} ${ids.length === 1 ? 'repository' : 'repositories'}; the overview is refreshed.`); }
    }
    /** The dialog is resizable by its corner; a size the user chose is kept for later visits. */
    let opened = null;
    const measure = () => ({ width: Math.round($('git-sync-dialog').offsetWidth), height: Math.round($('git-sync-dialog').offsetHeight) });
    function saveSize() {
        const dialog = $('git-sync-dialog'); if (!dialog.open || !opened) return;
        const size = measure();
        if (!size.width || !size.height || (size.width === opened.width && size.height === opened.height)) return;
        opened = size;
        try { localStorage.setItem(SIZE, JSON.stringify(size)); } catch (_) { /* Storage unavailable. */ }
    }
    function rememberSize() {
        const dialog = $('git-sync-dialog');
        try {
            const saved = JSON.parse(localStorage.getItem(SIZE) || 'null');
            if (saved?.width && saved?.height) { dialog.style.width = `${saved.width}px`; dialog.style.height = `${saved.height}px`; }
        } catch (_) { /* Storage unavailable or invalid. */ }
        // A resize ends with the pointer released; the observer also covers other ways the size can change.
        dialog.addEventListener('pointerup', saveSize);
        if (typeof ResizeObserver === 'undefined') return;
        let timer;
        new ResizeObserver(() => { clearTimeout(timer); timer = setTimeout(saveSize, 200); }).observe(dialog);
    }
    function initSync() {
        let filterTimer;
        const refilter = () => { clearTimeout(filterTimer); filterTimer = setTimeout(render, 120); };
        $('git-sync-open').addEventListener('click', () => guard(openSync));
        $('git-sync-filter').addEventListener('change', render);
        $('git-sync-repository-filter').addEventListener('input', refilter);
        $('git-sync-branch-filter').addEventListener('input', refilter);
        $('git-sync-refresh').addEventListener('click', () => guard(loadSummary));
        $('git-sync-fetch').addEventListener('click', () => guard(() => bulk('fetch')));
        $('git-sync-pull').addEventListener('click', () => guard(() => bulk('pull')));
        $('git-sync-push').addEventListener('click', () => guard(() => bulk('push')));
        $('git-sync-close').addEventListener('click', () => $('git-sync-dialog').close());
        rememberSize();
    }
    Object.assign(App, { initSync, openSync, syncTargets: bulkTargets, syncSizeKey: SIZE });
})(window.WorkspaceGitApp = window.WorkspaceGitApp || {});
