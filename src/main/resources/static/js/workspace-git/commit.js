/* Workspace Git page: commit. Scripts share window.WorkspaceGitApp and load in template order. */
(App => {
    'use strict';
    const { $, base, state, announce, repoUrl, staged, status } = App;
    const { request, withSnapshots } = App;
    const draftKey = () => state.commitMode === 'selected-all' ? '__batch__' : state.repository;
    const selectedFiles = id => [...(state.files.get(id)?.values() || [])];
    function commitTargets() {
        const ids = state.commitMode === 'selected-all' ? state.repositories.map(repo => repo.id) : [state.repository];
        return ids.filter(id => selectedFiles(id).length).map(id => ({ id, version: state.statuses.get(id)?.version, files: selectedFiles(id) }));
    }
    async function loadAmend() {
        const id = state.repository; if (!id) return;
        const amend = await request(`${repoUrl(id)}/amend`);
        if (state.repository !== id) return;
        state.amendState = { id, ...amend };
        if (state.amending && !amend.allowed) setAmend(false);
        App.renderToolbar();
    }
    /** Amending prefills the last commit's message and restores the previous draft when switched off. */
    function setAmend(on) {
        const amend = state.amendState;
        if (on && amend?.id === state.repository && amend.allowed) {
            if (!state.amending) state.amendDraft = $('git-commit-message').value;
            state.amending = true; $('git-commit-message').value = amend.message;
        } else {
            if (state.amending) $('git-commit-message').value = state.amendDraft ?? '';
            state.amending = false;
        }
        $('git-amend').checked = state.amending; state.drafts.set(draftKey(), $('git-commit-message').value); App.renderToolbar();
    }
    function setCommitMode(mode) {
        if (mode !== 'staged' && state.amending) setAmend(false);
        const message = $('git-commit-message').value;
        if (draftKey()) state.drafts.set(draftKey(), message);
        state.commitMode = mode; $('git-commit-mode').value = mode;
        if (!state.drafts.has(draftKey())) state.drafts.set(draftKey(), message);
        $('git-commit-message').value = state.drafts.get(draftKey()) || '';
        App.renderToolbar();
    }
    /** Commits the current scope; {@code push} turns it into Commit & Push for every target. */
    async function submitCommit({ push = false } = {}) {
        const id = state.repository; const current = status(); const draft = $('git-commit-message').value;
        const key = draftKey(); const mode = state.commitMode;
        const targets = mode === 'staged' ? [{ id }] : commitTargets(); const ids = targets.map(target => target.id);
        if (!draft.trim() || !ids.length || ids.some(id => !id || state.busy.has(id))) return;
        ids.forEach(id => state.busy.add(id)); App.renderToolbar(); $('git-operation-details').open = true;
        try {
            const operation = mode === 'staged'
                ? await request(`${repoUrl(id)}/commit`, { message: draft, version: current.stagedVersion, push, amend: state.amending, head: state.amending ? state.amendState.head : null })
                : await request(`${base}/commits`, { message: draft, targets: await withSnapshots(targets), push });
            const result = await App.watch(operation);
            // "committed" means the commit exists but its push did not succeed; the commit itself is never repeated.
            const successful = result.outcomes.filter(outcome => outcome.state === 'success' || outcome.state === 'committed');
            const unpushed = result.outcomes.filter(outcome => outcome.state === 'committed');
            if (mode !== 'staged') for (const outcome of successful) {
                const submitted = targets.find(target => target.id === outcome.repositoryId);
                for (const file of submitted.files) if (state.files.get(outcome.repositoryId)?.get(file.path) === file) state.files.get(outcome.repositoryId).delete(file.path);
            }
            if (successful.length === result.outcomes.length) {
                if (state.drafts.get(key) === draft) { state.drafts.set(key, ''); if (draftKey() === key) $('git-commit-message').value = ''; }
                const created = `${successful.length} ${successful.length === 1 ? 'commit created' : 'commits created'}`;
                if (unpushed.length) announce(`${created}; ${unpushed.length} could not be pushed. Review Operation results; use Publish branch where no upstream is configured.`);
                else announce(push ? `${created} and pushed.` : `${created}. Unselected changes remain pending; push when ready.`);
            } else announce(`${successful.length} commits created; other targets failed or have an uncertain outcome. Review Operation results before trying again.`);
        } finally {
            if (state.amending) { state.amending = false; $('git-amend').checked = false; }
            ids.forEach(id => state.busy.delete(id)); await App.refreshStatuses(); App.renderToolbar();
            await Promise.all([loadAmend(), App.refreshHistory()]);
        }
    }
    Object.assign(App, { draftKey, selectedFiles, commitTargets, setCommitMode, submitCommit, loadAmend, setAmend });
})(window.WorkspaceGitApp = window.WorkspaceGitApp || {});
