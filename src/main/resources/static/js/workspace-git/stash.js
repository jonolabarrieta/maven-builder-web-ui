/* Workspace Git page: stash list of the active repository with apply, pop and confirmed drop. */
(App => {
    'use strict';
    const { $, state, node, repoUrl } = App;
    const { request, guard } = App;
    const relative = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });
    const view = { open: false, repository: null, entries: [] };
    function when(seconds) {
        const hours = Math.round((seconds * 1000 - Date.now()) / 3600000);
        return Math.abs(hours) < 48 ? relative.format(hours, 'hour') : relative.format(Math.round(hours / 24), 'day');
    }
    async function load() {
        const id = state.repository; if (!id || !view.open) return;
        const entries = await request(`${repoUrl(id)}/stashes`);
        if (state.repository !== id) return;
        Object.assign(view, { repository: id, entries }); render();
    }
    /** Every action names the stash reference and the commit the user saw, so a changed list is rejected. */
    async function run(action, entry, confirmation = null) {
        try { await App.runAction(action, [view.repository], null, false, confirmation, null, { stash: entry.ref, stashSha: entry.sha }); }
        finally { await load(); }
    }
    function act(action, entry) {
        if (action !== 'stash-drop') return guard(() => run(action, entry));
        ConfirmationModal.show({
            title: 'Drop stash?', variant: 'danger', confirmLabel: 'Drop stash',
            message: `Drop ${entry.ref} “${entry.message}”? Its changes are removed from the stash list and cannot be restored from it. This cannot be undone.`,
            onConfirm: () => guard(() => run('stash-drop', entry, 'confirmed'))
        });
    }
    function render() {
        const busy = state.busy.has(view.repository);
        const items = view.entries.map(entry => {
            const item = node('li', 'git-stash-entry');
            const label = node('div', 'git-stash-label');
            label.append(node('code', '', entry.ref), node('span', 'git-history-subject', entry.message),
                node('span', 'git-history-meta', `${entry.branch ? `on ${entry.branch} · ` : ''}${when(entry.time)}`));
            const actions = node('div', 'git-stash-actions');
            [['stash-apply', 'Apply'], ['stash-pop', 'Pop'], ['stash-drop', 'Drop']].forEach(([action, text]) => {
                const button = node('button', action === 'stash-drop' ? 'git-danger' : '', text); button.type = 'button'; button.disabled = busy;
                button.setAttribute('aria-label', `${text} ${entry.ref}: ${entry.message}`);
                button.addEventListener('click', () => act(action, entry)); actions.append(button);
            });
            item.append(label, actions); return item;
        });
        if (!items.length) items.push(node('li', 'git-empty', 'No stashes in this repository.'));
        $('git-stash-list').replaceChildren(...items);
    }
    async function toggleStashes() {
        view.open = !view.open;
        $('git-stashes').hidden = !view.open; $('git-stash-toggle').setAttribute('aria-expanded', String(view.open));
        if (view.open) await load();
    }
    async function refreshStashes() { if (view.open) await load(); }
    function initStashes() { $('git-stash-toggle').addEventListener('click', () => guard(toggleStashes)); }
    Object.assign(App, { initStashes, refreshStashes, stashView: view });
})(window.WorkspaceGitApp = window.WorkspaceGitApp || {});
