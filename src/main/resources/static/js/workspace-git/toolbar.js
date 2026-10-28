/* Workspace Git page: toolbar. Scripts share window.WorkspaceGitApp and load in template order. */
(App => {
    'use strict';
    const { $, state, repository, status, upstreamLabel, repoName } = App;
    function renderToolbar() {
        const repo = repository(); const current = status(); const busy = state.busy.has(state.repository);
        $('git-repository-name').textContent = repo ? repoName(repo) : 'Choose a repository';
        $('git-repository-name').title = repo?.absolutePath || '';
        $('git-repository-branch').textContent = repo ? `${current?.unborn ? 'No commits yet · ' : ''}${upstreamLabel(current)}` : 'Review changes, prepare a commit, then push.';
        // The branch control opens the switcher; the text keeps upstream and ahead/behind details next to it.
        $('git-branch-button').hidden = !repo || !current || Boolean(current.error);
        $('git-branch-button').disabled = busy; $('git-branch-name').textContent = current?.branch === 'detached HEAD' ? 'detached' : current?.branch || '';
        $('git-branch-button').setAttribute('aria-label', `Switch branch, current ${current?.branch || 'unknown'}`);
        $('git-repository-actions').hidden = !repo;
        document.querySelectorAll('[data-action]').forEach(button => { button.disabled = busy || !current || Boolean(current.error); });
        $('git-commit-message').disabled = !repo;
        const targets = App.commitTargets(); const selectedCount = targets.reduce((sum, target) => sum + target.files.length, 0);
        const files = `${selectedCount} ${selectedCount === 1 ? 'file' : 'files'}`, repositories = `${targets.length} ${targets.length === 1 ? 'repository' : 'repositories'}`;
        $('git-selected-files-count').textContent = `${files} in ${repositories} marked`;
        const selectedReady = targets.length > 0 && targets.every(target => !state.busy.has(target.id) && state.statuses.get(target.id)?.version && !state.statuses.get(target.id)?.files.some(file => file.kind === 'conflict'));
        $('git-commit-button').disabled = $('git-commit-push-button').disabled = !selectedReady || !$('git-commit-message').value.trim();
        $('git-publish').hidden = !repo || !current || Boolean(current.error) || Boolean(current.upstream) || current.unborn || current.branch === 'detached HEAD';
        $('git-publish').disabled = busy;
        const entry = current?.files.find(file => file.path === state.file?.path);
        const untracked = state.file?.area === 'changes' && (entry ? entry.index === '?' && entry.kind !== 'gitlink'
            : [...state.folderFiles.values()].some(files => Array.isArray(files) && files.some(file => file.path === state.file.path)));
        $('git-delete-file').hidden = !untracked; $('git-delete-file').disabled = busy || !untracked;
        $('git-commit-button').textContent = targets.length ? `Commit ${files}${targets.length > 1 ? ` in ${repositories}` : ''}` : 'Commit';
        $('git-commit-help').textContent = targets.length
            ? `${files} · ${repositories}. Changes uses the working tree; Staged Changes uses the index. Unmarked files, including staged ones, are not committed. You confirm the list before anything runs.`
            : 'Mark files, or check a repository to mark all of its files. Only marked files are committed.';
        const bulk = App.bulkTargets();
        $('git-push-selected').textContent = `Push selected (${bulk.length})`;
        $('git-push-selected').disabled = !bulk.length || bulk.some(id => state.busy.has(id) || !state.statuses.get(id)?.version);
        $('git-commit-repository').textContent = targets.length ? `· ${repositories}` : '';
        const dirty = state.repositories.filter(item => state.statuses.get(item.id)?.files.length);
        $('git-download-changed').disabled = !dirty.length;
        $('git-download-changed').title = dirty.length ? `Download the absolute paths of ${dirty.length} ${dirty.length === 1 ? 'repository' : 'repositories'} with uncommitted changes` : 'No repository has uncommitted changes';
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
    }
    /** Absolute paths of repositories whose latest known status has uncommitted changes. */
    function downloadChanged() {
        const paths = state.repositories.filter(repo => state.statuses.get(repo.id)?.files.length).map(repo => repo.absolutePath);
        if (!paths.length) return;
        App.download('changed-repositories', paths);
        App.announce(`Downloaded the paths of ${paths.length} ${paths.length === 1 ? 'repository' : 'repositories'} with changes.`);
    }
    /** Copies the active repository's absolute path, showing it for manual copying when the clipboard is refused. */
    async function copyPath() {
        const repo = repository(); if (!repo) return;
        try { await navigator.clipboard.writeText(repo.absolutePath); App.announce(`Copied ${repo.absolutePath}`); }
        catch (_) {
            await App.choose({ title: 'Copy repository path', message: 'The browser did not allow clipboard access. Select the path and copy it.', text: { label: 'Absolute path', value: repo.absolutePath }, confirmLabel: 'Close' });
        }
    }
    Object.assign(App, { renderToolbar, downloadChanged, copyPath });
})(window.WorkspaceGitApp = window.WorkspaceGitApp || {});
