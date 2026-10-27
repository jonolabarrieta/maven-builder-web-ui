/* Workspace Git page: toolbar. Scripts share window.WorkspaceGitApp and load in template order. */
(App => {
    'use strict';
    const { $, state, staged, repository, status, branchLabel } = App;
    function renderToolbar() {
        const repo = repository(); const current = status(); const busy = state.busy.has(state.repository);
        $('git-repository-name').textContent = repo?.name || 'Choose a repository';
        $('git-repository-branch').textContent = repo ? branchLabel(current) : 'Review changes, prepare a commit, then push.';
        // The branch control opens the switcher; the text keeps upstream and ahead/behind details next to it.
        $('git-branch-button').hidden = !repo || !current || Boolean(current.error);
        $('git-branch-button').disabled = busy; $('git-branch-name').textContent = current?.branch || '';
        $('git-branch-button').setAttribute('aria-label', `Switch branch, current ${current?.branch || 'unknown'}`);
        $('git-repository-actions').hidden = !repo;
        document.querySelectorAll('[data-action]').forEach(button => { button.disabled = busy || !current || Boolean(current.error); });
        $('git-commit-message').disabled = !repo;
        const canCommit = repo && current && !current.error && !busy && current.files.some(staged) && !current.files.some(file => file.kind === 'conflict');
        const targets = App.commitTargets(); const selectedCount = targets.reduce((sum, target) => sum + target.files.length, 0);
        const totalFiles = [...state.files.values()].reduce((sum, files) => sum + files.size, 0);
        const totalRepositories = [...state.files.values()].filter(files => files.size).length;
        $('git-selected-files-count').textContent = `${totalFiles} ${totalFiles === 1 ? 'file' : 'files'} in ${totalRepositories} ${totalRepositories === 1 ? 'repository' : 'repositories'} selected`;
        const selectedReady = targets.length > 0 && targets.every(target => !state.busy.has(target.id) && state.statuses.get(target.id)?.version && !state.statuses.get(target.id)?.files.some(file => file.kind === 'conflict'));
        const isStaged = state.commitMode === 'staged';
        $('git-commit-button').disabled = $('git-commit-push-button').disabled = !(isStaged ? canCommit : selectedReady) || !$('git-commit-message').value.trim();
        $('git-publish').hidden = !repo || !current || Boolean(current.error) || Boolean(current.upstream) || current.unborn || current.branch === 'detached HEAD';
        $('git-publish').disabled = busy;
        const entry = current?.files.find(file => file.path === state.file?.path);
        const untracked = state.file?.area === 'changes' && (entry ? entry.index === '?' && entry.kind !== 'gitlink'
            : [...state.folderFiles.values()].some(files => Array.isArray(files) && files.some(file => file.path === state.file.path)));
        $('git-delete-file').hidden = !untracked; $('git-delete-file').disabled = busy || !untracked;
        $('git-commit-button').textContent = isStaged ? 'Commit staged changes' : state.commitMode === 'selected-all' ? `Commit ${selectedCount} ${selectedCount === 1 ? 'file' : 'files'} in ${targets.length} ${targets.length === 1 ? 'repository' : 'repositories'}` : `Commit ${selectedCount} selected ${selectedCount === 1 ? 'file' : 'files'}`;
        $('git-commit-help').textContent = isStaged ? 'Commits include staged changes only. Push is a separate action.' : `${selectedCount} selected files · ${targets.length} ${targets.length === 1 ? 'repository' : 'repositories'}. Changes uses the working tree; Staged Changes uses the index. Unselected files keep their staging.`;
        $('git-push-selected').textContent = `Push selected (${state.selected.size})`;
        $('git-push-selected').disabled = !state.selected.size || [...state.selected].some(id => state.busy.has(id) || !state.statuses.get(id)?.version);
        $('git-commit-repository').textContent = state.commitMode === 'selected-all' ? `· ${targets.length} ${targets.length === 1 ? 'repository' : 'repositories'}` : repo ? `· ${repo.path}` : '';
        const diff = state.diff;
        $('git-selection-count').textContent = `${state.lines.size} ${state.lines.size === 1 ? 'line' : 'lines'} selected`;
        $('git-stage-lines').textContent = diff?.area === 'staged' ? 'Unstage selected lines' : 'Stage selected lines';
        $('git-stage-file').textContent = diff?.area === 'staged' ? 'Unstage file' : 'Stage file';
        $('git-stage-lines').disabled = busy || !diff?.partial || !state.lines.size;
        $('git-stage-file').disabled = busy || !diff || diff.kind === 'gitlink';
        $('git-discard-file').disabled = busy || !diff || diff.area !== 'changes' || current?.files.find(file => file.path === diff?.path)?.index === '?' || ['untracked', 'gitlink', 'conflict'].includes(current?.files.find(file => file.path === diff?.path)?.kind);
        $('git-select-block').disabled = busy || !diff?.partial;
        $('git-next-block').disabled = $('git-prev-block').disabled = !diff?.lines?.length;
        $('git-full-file').disabled = !diff?.lines?.length || diff.area === 'commit';
        $('git-edit-file').disabled = busy || Boolean(state.editor) || !diff || diff.area !== 'changes' || ['conflict', 'gitlink', 'symlink', 'binary', 'oversized', 'untracked-dir'].includes(diff.kind);
        if (diff?.area === 'commit') ['git-stage-lines', 'git-stage-file', 'git-discard-file', 'git-select-block'].forEach(id => { $(id).disabled = true; });
        const amend = state.amendState?.id === state.repository ? state.amendState : null;
        $('git-amend-row').hidden = !isStaged; $('git-amend').disabled = busy || !amend?.allowed;
        $('git-amend-row').title = amend && !amend.allowed ? amend.reason : 'Replace the last commit with the staged changes and this message';
        if (state.amending) {
            $('git-commit-button').disabled = $('git-commit-push-button').disabled = !repo || !current || Boolean(current.error) || busy || !amend?.allowed
                || current.files.some(file => file.kind === 'conflict') || !$('git-commit-message').value.trim();
            $('git-commit-button').textContent = 'Amend last commit';
            $('git-commit-help').textContent = 'Amend replaces the last commit with the staged changes and this message. Only unpushed commits can be amended.';
        }
    }
    Object.assign(App, { renderToolbar });
})(window.WorkspaceGitApp = window.WorkspaceGitApp || {});
