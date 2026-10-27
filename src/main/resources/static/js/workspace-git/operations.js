/* Workspace Git page: operations. Scripts share window.WorkspaceGitApp and load in template order. */
(App => {
    'use strict';
    const { $, workspaceId, workspaceName, base, state, announce, node, repoUrl } = App;
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
    function watch(operation) {
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
    function renderOperations() {
        const items = [];
        [...state.operations.values()].slice(-10).reverse().forEach(operation => operation.outcomes.forEach(outcome => {
            const repo = state.repositories.find(repo => repo.id === outcome.repositoryId);
            const item = node('li', '', `${repo?.path || (outcome.repositoryId === 'discovery' ? 'Repository discovery' : 'Repository')} · ${outcome.state}: ${outcome.message}`);
            item.dataset.state = outcome.state; items.push(item);
        }));
        $('git-operation-results').replaceChildren(...items);
        const running = [...state.operations.values()].filter(op => op.state === 'running').length;
        $('git-operation-heading').textContent = running ? `Operation results · ${running} running` : 'Operation results';
    }
    async function runAction(action, ids, path, includeUntracked = false, confirmation = null, capturedTargets = null, extra = {}) {
        const targets = capturedTargets || ids.map(id => ({ id, version: state.statuses.get(id)?.version }));
        if (ids.some(id => state.busy.has(id))) throw new Error('A Git operation is already running for one of these repositories. Wait for it to finish.');
        ids.forEach(id => state.busy.add(id)); App.renderToolbar(); $('git-operation-details').open = true;
        let result;
        try {
            result = await watch(await request(`${base}/actions`, { action, targets, path: path || null, includeUntracked, confirmation, ...extra }));
            const failed = result.outcomes.filter(outcome => outcome.state !== 'success');
            announce(failed.length ? `${action} finished with ${failed.length} unsuccessful repositories. Review Operation results.` : `${action} completed for ${ids.length} ${ids.length === 1 ? 'repository' : 'repositories'}.`);
        } finally {
            ids.forEach(id => state.busy.delete(id)); await App.refreshStatuses(); App.renderToolbar();
            if (ids.includes(state.repository)) guard(() => Promise.all([App.loadAmend(), App.refreshHistory()]));
        }
        if (action === 'pull') offerRebase(result);
    }
    /** A refused fast-forward pull on diverged branches can continue as an explicitly confirmed rebase pull. */
    function offerRebase(result) {
        const diverged = result.outcomes.filter(outcome => outcome.state === 'failed')
            .map(outcome => outcome.repositoryId).filter(id => state.statuses.get(id)?.ahead > 0 && state.statuses.get(id)?.behind > 0);
        if (!diverged.length) return;
        const names = diverged.map(id => state.repositories.find(repo => repo.id === id)?.path || id).join(', ');
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
            announce(`${state.repositories.find(repo => repo.id === id)?.path || 'Repository'} is now on ${result.current}.`);
        } finally {
            state.busy.delete(id); const repo = state.repositories.find(item => item.id === id);
            if (repo) await App.refreshStatus(repo); App.flushRender(); App.renderToolbar();
            if (id === state.repository) await Promise.all([App.reconcileFile(), App.loadAmend(), App.refreshHistory(), App.refreshStashes()]);
        }
    }
    async function openInVsCode() {
        const id = state.repository; if (!id) return;
        const result = await request(`${repoUrl(id)}/open-editor`, {});
        announce(`Opening ${result.opened} in Visual Studio Code.`);
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
            message: `Delete ${path} in ${state.repositories.find(repo => repo.id === ids[0])?.path || ids[0]}? Git does not track it, so it cannot be restored from Git. This cannot be undone.`,
            onConfirm: () => guard(() => runAction(action, ids, path, false, 'confirmed'))
        });
        if (!['stash', 'discard', 'unstage-all'].includes(action)) return guard(() => runAction(action, ids, path));
        return guard(async () => {
            // Capture the confirmed snapshots before opening the dialog; refresh cannot broaden them.
            const targets = await withSnapshots(ids.map(id => ({ id })));
            if (action === 'unstage-all') return runAction(action, ids, path, false, null, targets);
            const names = ids.map(id => state.repositories.find(repo => repo.id === id)?.path || id).join(', ');
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
    Object.assign(App, { switchBranch, openInVsCode, publish, connect, watch, renderOperations, runAction, actionDialog });
})(window.WorkspaceGitApp = window.WorkspaceGitApp || {});
