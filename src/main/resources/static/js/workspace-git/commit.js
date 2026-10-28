/* Workspace Git page: commit. Scripts share window.WorkspaceGitApp and load in template order. */
(App => {
    'use strict';
    const { $, base, state, announce } = App;
    const { request, withSnapshots } = App;
    // One commit form covers every repository with marked files, so it keeps a single draft.
    const draftKey = () => '__commit__';
    const selectedFiles = id => [...(state.files.get(id)?.values() || [])];
    /** Marked files grouped by repository; only repositories with marked files are targets. */
    function commitTargets() {
        return state.repositories.map(repo => repo.id).filter(id => selectedFiles(id).length)
            .map(id => ({ id, version: state.statuses.get(id)?.version, files: selectedFiles(id) }));
    }
    /** Commits exactly the marked files after confirmation; {@code push} turns it into Commit & Push for every target. */
    async function submitCommit({ push = false } = {}) {
        const draft = $('git-commit-message').value; const key = draftKey();
        const planned = commitTargets(); const ids = planned.map(target => target.id);
        if (!draft.trim() || !ids.length || ids.some(id => state.busy.has(id))) return;
        // Snapshots are taken before confirming, so changes made while the dialog is open are rejected as stale.
        const targets = await withSnapshots(planned);
        if (!(await App.confirmCommit({ targets, push, message: draft }))) { announce('Commit cancelled. Your message and selections are kept.'); return; }
        ids.forEach(id => state.busy.add(id)); App.renderToolbar(); $('git-operation-details').open = true;
        try {
            const operation = await request(`${base}/commits`, { message: draft, targets, push });
            const result = await App.watch(operation, { action: push ? 'commit-push' : 'commit', label: push ? 'Commit & Push' : 'Commit', targets: ids });
            // "committed" means the commit exists but its push did not succeed; the commit itself is never repeated.
            const successful = result.outcomes.filter(outcome => outcome.state === 'success' || outcome.state === 'committed');
            const unpushed = result.outcomes.filter(outcome => outcome.state === 'committed');
            for (const outcome of successful) {
                const submitted = targets.find(target => target.id === outcome.repositoryId);
                for (const file of submitted.files) if (state.files.get(outcome.repositoryId)?.get(file.path) === file) state.files.get(outcome.repositoryId).delete(file.path);
            }
            if (successful.length === result.outcomes.length) {
                if (state.drafts.get(key) === draft || $('git-commit-message').value === draft) { state.drafts.set(key, ''); $('git-commit-message').value = ''; }
                const created = `${successful.length} ${successful.length === 1 ? 'commit created' : 'commits created'}`;
                if (unpushed.length) announce(`${created}; ${unpushed.length} could not be pushed. Review the operation history; use Publish branch where no upstream is configured.`);
                else announce(push ? `${created} and pushed.` : `${created}. Unmarked changes remain pending; push when ready.`);
            } else announce(`${successful.length} commits created; other targets failed or have an uncertain outcome. Review the operation history before trying again.`);
        } finally {
            ids.forEach(id => state.busy.delete(id)); await App.refreshStatuses(); App.renderToolbar();
            await App.refreshHistory();
        }
    }
    Object.assign(App, { draftKey, selectedFiles, commitTargets, submitCommit });
})(window.WorkspaceGitApp = window.WorkspaceGitApp || {});
