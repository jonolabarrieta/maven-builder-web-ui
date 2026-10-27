/* Workspace Git page: synchronization overview of commits to push and pull across repositories and branches. */
(App => {
    'use strict';
    const { $, base, state, node, announce } = App;
    const { request, guard } = App;
    const relative = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });
    let summary = null;
    function age(timestamp) {
        if (!timestamp) return 'Never fetched';
        const minutes = Math.round((timestamp - Date.now()) / 60000);
        if (Math.abs(minutes) < 60) return relative.format(minutes, 'minute');
        const hours = Math.round(minutes / 60);
        return Math.abs(hours) < 48 ? relative.format(hours, 'hour') : relative.format(Math.round(hours / 24), 'day');
    }
    const stale = repository => !repository.lastFetch || Date.now() - repository.lastFetch > state.staleFetchHours * 3600000;
    const current = repository => repository.branches.find(branch => branch.current);
    const FILTERS = {
        all: () => true,
        ahead: (repository, branch) => branch.ahead > 0,
        behind: (repository, branch) => branch.behind > 0,
        diverged: (repository, branch) => branch.ahead > 0 && branch.behind > 0,
        'no-upstream': (repository, branch) => !branch.upstream,
        changes: (repository, branch) => repository.changes > 0 && branch.current
    };
    function count(value, label) {
        const cell = node('td', 'git-sync-count');
        if (value === null || value === undefined) cell.textContent = '—';
        else { cell.textContent = String(value); if (value > 0) cell.classList.add(label); }
        return cell;
    }
    function render() {
        const filter = FILTERS[$('git-sync-filter').value] || FILTERS.all; const rows = [];
        for (const repository of summary?.repositories || []) {
            if (repository.error) { const row = node('tr', 'git-sync-error'); const cell = node('td', '', `${repository.path}: ${repository.error}`); cell.colSpan = 6; row.append(cell); rows.push(row); continue; }
            const branches = repository.branches.filter(branch => filter(repository, branch));
            branches.forEach((branch, index) => {
                const row = node('tr'); row.dataset.repository = repository.id; row.dataset.branch = branch.name;
                const name = node('td', 'git-sync-repository');
                if (index === 0) {
                    name.append(node('strong', '', repository.path));
                    const fetched = node('span', stale(repository) ? 'git-sync-fetch is-stale' : 'git-sync-fetch', `Fetched ${age(repository.lastFetch)}`);
                    if (!repository.lastFetch) fetched.textContent = 'No fetch recorded';
                    if (stale(repository)) fetched.title = 'Counts may be outdated. Fetch to update them.';
                    name.append(fetched);
                }
                const label = node('td', 'git-sync-branch'); label.append(node('span', '', branch.name));
                if (branch.current) label.append(node('span', 'git-sync-badge', 'current'));
                if (branch.ahead > 0 && branch.behind > 0) label.append(node('span', 'git-sync-badge is-diverged', 'diverged'));
                const upstream = node('td', 'git-sync-upstream', branch.gone ? `${branch.upstream} (gone)` : branch.upstream || 'No upstream');
                if (branch.ahead > 0 && branch.behind > 0) upstream.title = 'Fast-forward pull will not apply; pull with rebase or merge.';
                const changes = node('td', 'git-sync-count', branch.current && repository.changes ? String(repository.changes) : '');
                row.append(name, label, upstream, count(branch.ahead, 'is-ahead'), count(branch.behind, 'is-behind'), changes);
                rows.push(row);
            });
        }
        if (!rows.length) { const row = node('tr'); const cell = node('td', 'git-empty', summary ? 'No branches match this filter.' : 'Loading…'); cell.colSpan = 6; row.append(cell); rows.push(row); }
        $('git-sync-rows').replaceChildren(...rows);
        const targets = bulkTargets();
        $('git-sync-pull').textContent = `Pull behind (${targets.pull.length})`; $('git-sync-pull').disabled = !targets.pull.length;
        $('git-sync-push').textContent = `Push ahead (${targets.push.length})`; $('git-sync-push').disabled = !targets.push.length;
        $('git-sync-fetch').disabled = !(summary?.repositories || []).some(repository => !repository.error);
    }
    /** Bulk actions only target each repository's current branch. */
    function bulkTargets() {
        const repositories = (summary?.repositories || []).filter(repository => !repository.error);
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
        if (!$('git-sync-dialog').open) $('git-sync-dialog').showModal();
        await loadSummary();
    }
    async function bulk(action) {
        const ids = bulkTargets()[action];
        if (!ids.length) return;
        const buttons = ['git-sync-fetch', 'git-sync-pull', 'git-sync-push'].map(id => $(id)); buttons.forEach(button => { button.disabled = true; });
        try { await App.runAction(action, ids, null); }
        finally { await loadSummary(); announce(`${action} finished for ${ids.length} ${ids.length === 1 ? 'repository' : 'repositories'}; the overview is refreshed.`); }
    }
    function initSync() {
        $('git-sync-open').addEventListener('click', () => guard(openSync));
        $('git-sync-filter').addEventListener('change', render);
        $('git-sync-refresh').addEventListener('click', () => guard(loadSummary));
        $('git-sync-fetch').addEventListener('click', () => guard(() => bulk('fetch')));
        $('git-sync-pull').addEventListener('click', () => guard(() => bulk('pull')));
        $('git-sync-push').addEventListener('click', () => guard(() => bulk('push')));
        $('git-sync-close').addEventListener('click', () => $('git-sync-dialog').close());
    }
    Object.assign(App, { initSync, openSync, syncTargets: bulkTargets });
})(window.WorkspaceGitApp = window.WorkspaceGitApp || {});
