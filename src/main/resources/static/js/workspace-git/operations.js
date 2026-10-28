/* Workspace Git page: operations. Scripts share window.WorkspaceGitApp and load in template order. */
(App => {
    'use strict';
    const { $, workspaceId, workspaceName, base, state, announce, node, repoUrl, repoById, repoName } = App;
    const { guard, request, withSnapshots } = App;
    // Operation progress is pushed over STOMP. Messages can arrive before the HTTP response that names the
    // operation, so recent unknown ones are kept briefly; polling covers an unavailable or silent channel.
    const listeners = new Map(); const early = new Map();
    function connect() {
        if (state.channel !== 'idle') return;
        if (typeof SockJS === 'undefined' || typeof Stomp === 'undefined') { state.channel = 'failed'; return; }
        state.channel = 'connecting';
        try {
            const client = Stomp.over(new SockJS('/ws-logs')); client.debug = null;
            client.connect({}, () => {
                state.channel = 'open';
                client.subscribe(`/topic/workspace-git/${workspaceId}`, message => {
                    const operation = JSON.parse(message.body); const listener = listeners.get(operation.id);
                    if (listener) return listener(operation);
                    early.set(operation.id, operation); if (early.size > 50) early.delete(early.keys().next().value);
                });
            }, () => { state.channel = 'failed'; });
        } catch (_) { state.channel = 'failed'; }
    }
    /** A server that no longer knows the operation cannot say whether it finished. */
    const expired = operation => ({ ...operation, state: 'done', outcomes: operation.outcomes.map(outcome => ['queued', 'running'].includes(outcome.state)
        ? { ...outcome, state: 'uncertain', message: 'The server no longer tracks this operation; it may have completed. Review the refreshed repository state.' } : outcome) });
    const HISTORY_LIMIT = 200;
    const LABELS = { fetch: 'Fetch', pull: 'Pull', push: 'Push', 'pull-rebase': 'Pull with rebase', publish: 'Publish branch', stash: 'Stash',
        'unstage-all': 'Unstage all', discard: 'Discard changes', 'delete-untracked': 'Delete untracked file' };
    /** History entries keep the targets' names and paths from when they started, even if a repository later disappears. */
    function remember(id, { action, label, targets = [] }) {
        state.operationMeta.set(id, { action, label: label || LABELS[action] || action, startedAt: new Date(),
            targets: targets.map(target => { const repo = repoById(target); return { id: target, name: repoName(repo), path: repo?.absolutePath || null }; }) });
        while (state.operationMeta.size > HISTORY_LIMIT) { const oldest = state.operationMeta.keys().next().value; state.operationMeta.delete(oldest); state.operations.delete(oldest); }
    }
    /** {@code meta} records the operation in the session history: { action, label, targets: repository ids }. */
    function watch(operation, meta = null) {
        if (meta) remember(operation.id, meta);
        state.operations.set(operation.id, operation); renderOperations();
        if (operation.state !== 'running') return Promise.resolve(operation);
        connect();
        return new Promise(resolve => {
            let heard = Date.now(), failures = 0, finished = false, timer;
            const finish = result => { finished = true; clearInterval(timer); listeners.delete(operation.id); resolve(result); };
            const update = next => {
                if (finished) return;
                heard = Date.now(); state.operations.set(next.id, next); renderOperations();
                if (next.state !== 'running') finish(next);
            };
            listeners.set(operation.id, update);
            if (early.has(operation.id)) { update(early.get(operation.id)); early.delete(operation.id); }
            timer = setInterval(async () => {
                if (finished || (state.channel === 'open' && Date.now() - heard < 2000)) return;
                try { update(await request(`${base}/operations/${operation.id}`)); failures = 0; }
                catch (error) {
                    const current = state.operations.get(operation.id) || operation;
                    if (error.status === 404 || ++failures >= 10) { const result = expired(current); state.operations.set(result.id, result); renderOperations(); finish(result); }
                }
            }, 1000);
        });
    }
    const time = date => date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    function overall(operation) {
        if (operation.state === 'running') return 'running';
        const unsuccessful = operation.outcomes.filter(outcome => !['success', 'committed'].includes(outcome.state)).length;
        if (!unsuccessful) return operation.outcomes.some(outcome => outcome.state === 'committed') ? 'partial' : 'success';
        return unsuccessful === operation.outcomes.length ? 'failed' : 'partial';
    }
    const pathsOf = meta => meta.targets.map(target => target.path).filter(Boolean);
    function downloadEntry(id) {
        const meta = state.operationMeta.get(id); const paths = pathsOf(meta);
        if (paths.length) App.download(`${meta.action}-repositories`, paths);
    }
    /** All entries, oldest first, each headed by its action, time and overall state. */
    function downloadAll() {
        const lines = [];
        for (const [id, meta] of state.operationMeta) {
            const operation = state.operations.get(id); const paths = pathsOf(meta);
            if (!paths.length) continue;
            lines.push(`# ${meta.label} · ${meta.startedAt.toLocaleString()} · ${operation ? overall(operation) : 'unknown'}`, ...paths, '');
        }
        if (lines.length) App.download('operation-history', lines);
    }
    function renderOperations() {
        const items = [];
        for (const [id, meta] of [...state.operationMeta].reverse()) {
            const operation = state.operations.get(id); if (!operation) continue;
            const item = node('li', 'git-operation-entry'); item.dataset.state = overall(operation);
            const heading = node('div', 'git-operation-entry-heading');
            heading.append(node('strong', '', meta.label), node('span', 'git-operation-time', time(meta.startedAt)), node('span', 'git-operation-state', overall(operation)));
            const download = node('button', 'git-operation-download', 'Paths'); download.type = 'button'; download.disabled = !pathsOf(meta).length;
            download.title = 'Download the absolute paths of the repositories of this operation';
            download.setAttribute('aria-label', `Download repository paths of ${meta.label} at ${time(meta.startedAt)}`);
            download.addEventListener('click', () => downloadEntry(id));
            heading.append(download);
            const outcomes = node('ul');
            operation.outcomes.forEach(outcome => {
                const target = meta.targets.find(entry => entry.id === outcome.repositoryId);
                const name = target?.name || repoName(repoById(outcome.repositoryId));
                const row = node('li', '', `${outcome.repositoryId === 'discovery' ? 'Repositories' : name} · ${outcome.state}: ${outcome.message}`);
                row.dataset.state = outcome.state; if (target?.path) row.title = target.path; outcomes.append(row);
            });
            item.append(heading, outcomes); items.push(item);
        }
        if (!items.length) items.push(node('li', 'git-empty', 'No operations yet.'));
        $('git-operation-results').replaceChildren(...items);
        $('git-operation-download-all').disabled = ![...state.operationMeta.values()].some(meta => pathsOf(meta).length);
        const running = [...state.operationMeta.keys()].filter(id => state.operations.get(id)?.state === 'running').length;
        $('git-operation-heading').textContent = running ? `Operation history · ${running} running` : `Operation history${state.operationMeta.size ? ` (${state.operationMeta.size})` : ''}`;
    }
    async function runAction(action, ids, path, includeUntracked = false, confirmation = null, capturedTargets = null, extra = {}) {
        const targets = capturedTargets || ids.map(id => ({ id, version: state.statuses.get(id)?.version }));
        if (ids.some(id => state.busy.has(id))) throw new Error('A Git operation is already running for one of these repositories. Wait for it to finish.');
        ids.forEach(id => state.busy.add(id)); App.renderToolbar(); $('git-operation-details').open = true;
        let result;
        try {
            result = await watch(await request(`${base}/actions`, { action, targets, path: path || null, includeUntracked, confirmation, ...extra }), { action, targets: ids });
            const failed = result.outcomes.filter(outcome => outcome.state !== 'success');
            announce(failed.length ? `${LABELS[action] || action} finished with ${failed.length} unsuccessful repositories. Review the operation history.` : `${LABELS[action] || action} completed for ${ids.length} ${ids.length === 1 ? 'repository' : 'repositories'}.`);
        } finally {
            ids.forEach(id => state.busy.delete(id)); await App.refreshStatuses(); App.renderToolbar();
            if (ids.includes(state.repository)) guard(App.refreshHistory);
        }
        if (action === 'pull') offerRebase(result);
    }
    /** A refused fast-forward pull on diverged branches can continue as an explicitly confirmed rebase pull. */
    function offerRebase(result) {
        const diverged = result.outcomes.filter(outcome => outcome.state === 'failed')
            .map(outcome => outcome.repositoryId).filter(id => state.statuses.get(id)?.ahead > 0 && state.statuses.get(id)?.behind > 0);
        if (!diverged.length) return;
        const names = diverged.map(id => repoName(repoById(id))).join(', ');
        ConfirmationModal.show({
            title: 'Pull with rebase?', variant: 'confirm', confirmLabel: 'Pull with rebase',
            message: `Local and remote commits have diverged in ${names}, so a fast-forward pull is not possible. Rebase the local commits onto the upstream? Unpushed local commits get new identities; conflicts stop the rebase for you to resolve.`,
            onConfirm: () => guard(() => runAction('pull-rebase', diverged, null, false, 'confirmed'))
        });
    }
    /** Switches the active repository to a chosen reference, or creates a branch from HEAD when a name is entered. */
    async function switchBranch() {
        const id = state.repository; if (!id || state.busy.has(id)) return;
        const references = await request(`${repoUrl(id)}/references`);
        const option = group => reference => ({ value: `${reference.type}\0${reference.ref}`, label: reference.name, group });
        const options = [...references.local.map(option('Local branches')), ...references.remote.map(option('Remote branches')), ...references.tags.map(option('Tags'))];
        const current = references.local.find(reference => reference.name === references.current);
        const answer = await App.choose({
            title: 'Switch branch', message: `Current: ${references.current || 'unknown'}. Uncommitted changes stay in the working tree when Git allows the switch.`,
            label: 'Branch or tag', options, value: current ? `${current.type}\0${current.ref}` : null,
            text: { label: 'Or create a new branch from HEAD', placeholder: 'feature/my-change' }, confirmLabel: 'Switch'
        });
        if (!answer) return;
        state.busy.add(id); App.renderToolbar();
        try {
            const result = answer.text
                ? await request(`${repoUrl(id)}/branches`, { name: answer.text })
                : await request(`${repoUrl(id)}/checkout`, { reference: answer.choice.split('\0')[1], type: answer.choice.split('\0')[0] });
            announce(`${repoName(repoById(id))} is now on ${result.current}.`);
        } finally {
            state.busy.delete(id); const repo = state.repositories.find(item => item.id === id);
            if (repo) await App.refreshStatus(repo); App.flushRender(); App.renderToolbar();
            if (id === state.repository) await Promise.all([App.reconcileFile(), App.refreshHistory(), App.refreshStashes()]);
        }
    }
    async function openInVsCode() {
        const id = state.repository; if (!id) return;
        const result = await request(`${repoUrl(id)}/open-editor`, {});
        announce(`Opening ${result.opened} in Visual Studio Code.`);
    }
    async function openFolder() {
        const id = state.repository; if (!id) return;
        const result = await request(`${repoUrl(id)}/open-explorer`, {});
        announce(`Opening ${result.opened} in the file explorer.`);
    }
    async function publish(id) {
        const remotes = await request(`${repoUrl(id)}/remotes`); const branch = state.statuses.get(id)?.branch;
        if (!remotes.length) return ConfirmationModal.info({ title: 'No remote configured', message: 'Add a remote with Git (for example git remote add origin <url>), then publish the branch.' });
        const answer = await App.choose({
            title: 'Publish branch', message: `Push ${branch} to a remote branch with the same name and track it as the upstream.`, label: 'Remote',
            options: remotes.map(remote => ({ value: remote, label: remote })), value: remotes.includes('origin') ? 'origin' : remotes[0], confirmLabel: 'Publish'
        });
        if (answer) await runAction('publish', [id], null, false, null, null, { remote: answer.choice });
    }
    function actionDialog(action, ids, path = null) {
        if (!ids.length) return ConfirmationModal.info({ title: 'No repositories selected', message: 'Select at least one repository to run this action.' });
        document.querySelectorAll('details[open]').forEach(details => { if (details.id !== 'git-operation-details') details.open = false; });
        if (action === 'publish') return guard(() => publish(ids[0]));
        if (action === 'delete-untracked') return ConfirmationModal.show({
            title: 'Delete untracked file?', variant: 'danger', confirmLabel: 'Delete',
            message: `Delete ${path} in ${repoName(repoById(ids[0]))}? Git does not track it, so it cannot be restored from Git. This cannot be undone.`,
            onConfirm: () => guard(() => runAction(action, ids, path, false, 'confirmed'))
        });
        if (!['stash', 'discard', 'unstage-all'].includes(action)) return guard(() => runAction(action, ids, path));
        return guard(async () => {
            // Capture the confirmed snapshots before opening the dialog; refresh cannot broaden them.
            const targets = await withSnapshots(ids.map(id => ({ id })));
            if (action === 'unstage-all') return runAction(action, ids, path, false, null, targets);
            const names = ids.map(id => repoName(repoById(id))).join(', ');
            ConfirmationModal.show({
                title: action === 'stash' ? 'Stash repository changes?' : path ? 'Discard file changes?' : 'Discard repository changes?',
                message: action === 'stash' ? `Stash changes in ${names}?` : `Discard unstaged changes to ${path || 'tracked files'} in ${names}? Staged changes and untracked files are kept. This cannot be undone.`,
                variant: action === 'discard' ? 'danger' : 'confirm',
                requireTyped: action === 'discard' && !path ? workspaceName : null,
                checkbox: action === 'stash' ? { label: 'Include untracked files', checked: false } : null,
                onConfirm: checked => guard(() => runAction(action, ids, path, checked === true, action === 'discard' && !path ? workspaceName : 'confirmed', targets))
            });
        });
    }
    Object.assign(App, { switchBranch, openInVsCode, openFolder, publish, connect, watch, renderOperations, runAction, actionDialog, downloadOperationHistory: downloadAll });
})(window.WorkspaceGitApp = window.WorkspaceGitApp || {});
