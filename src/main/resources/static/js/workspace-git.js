/* Workspace Git page bootstrap: wires controls after the workspace-git/*.js scripts have loaded. */
(App => {
    'use strict';
    const { $, state, changed } = App;
    const { guard } = App;
    $('git-refresh').addEventListener('click', () => guard(() => App.discover(true)));
    $('git-push-selected').addEventListener('click', () => App.actionDialog('push', [...state.selected]));
    $('git-commit-mode').addEventListener('change', () => App.setCommitMode($('git-commit-mode').value));
    let searchTimer;
    $('git-search').addEventListener('input', () => { clearTimeout(searchTimer); searchTimer = setTimeout(App.renderSidebar, 150); });
    // The uncommitted-changes filter is remembered between visits.
    const CHANGED_ONLY = 'mvnbuilder.git.changedOnly';
    try { $('git-changed-only').checked = localStorage.getItem(CHANGED_ONLY) === 'true'; } catch (_) { /* Storage unavailable. */ }
    $('git-changed-only').addEventListener('change', () => {
        try { localStorage.setItem(CHANGED_ONLY, String($('git-changed-only').checked)); } catch (_) { /* Storage unavailable. */ }
        App.renderSidebar();
    });
    $('git-select-all').addEventListener('change', event => { state.repositories.filter(App.visible).forEach(repo => event.target.checked ? state.selected.add(repo.id) : state.selected.delete(repo.id)); App.renderSidebar(); });
    document.querySelectorAll('[data-action]').forEach(button => button.addEventListener('click', () => App.actionDialog(button.dataset.action, state.repository ? [state.repository] : [])));
    document.querySelectorAll('[data-bulk-action]').forEach(button => button.addEventListener('click', () => App.actionDialog(button.dataset.bulkAction, [...state.selected])));
    $('git-stage-file').addEventListener('click', () => guard(() => App.mutate(false))); $('git-stage-lines').addEventListener('click', () => guard(() => App.mutate(true)));
    $('git-discard-file').addEventListener('click', () => App.actionDialog('discard', [state.repository], state.diff?.path));
    $('git-diff-mode').addEventListener('change', () => guard(App.renderDiff));
    $('git-diff').addEventListener('change', event => { if (event.target.dataset.line) { event.target.checked ? state.lines.add(event.target.dataset.line) : state.lines.delete(event.target.dataset.line); App.updateLineSelection(); } });
    $('git-diff').addEventListener('click', event => {
        const button = event.target.closest('[data-select-block]'); if (button) App.selectBlock(Number(button.dataset.selectBlock));
        const gap = event.target.closest('[data-gap]'); if (gap) guard(() => App.revealGap(Number(gap.dataset.gap)));
    });
    $('git-full-file').addEventListener('click', () => guard(App.toggleFullFile));
    $('git-commit-push-button').addEventListener('click', () => guard(() => App.submitCommit({ push: true })));
    $('git-commit-message').addEventListener('keydown', event => {
        if (event.key !== 'Enter' || !(event.ctrlKey || event.metaKey)) return;
        event.preventDefault();
        const button = event.shiftKey ? $('git-commit-push-button') : $('git-commit-button');
        if (!button.disabled) event.shiftKey ? button.click() : $('git-commit-form').requestSubmit(button);
    });
    $('git-publish').addEventListener('click', () => App.actionDialog('publish', state.repository ? [state.repository] : []));
    $('git-delete-file').addEventListener('click', () => App.actionDialog('delete-untracked', [state.repository], state.file?.path));
    $('git-diff').addEventListener('keydown', event => {
        if (!event.target.dataset.line || !['ArrowUp', 'ArrowDown'].includes(event.key)) return;
        const inputs = [...$('git-diff').querySelectorAll('[data-line]')]; const index = inputs.indexOf(event.target); const next = inputs[index + (event.key === 'ArrowDown' ? 1 : -1)];
        if (next) { event.preventDefault(); next.focus(); }
    });
    $('git-select-block').addEventListener('click', () => App.selectBlock(state.activeBlock));
    $('git-prev-block').addEventListener('click', () => App.moveBlock(-1)); $('git-next-block').addEventListener('click', () => App.moveBlock(1));
    $('git-back').addEventListener('click', () => { document.body.classList.remove('git-show-diff'); state.views.get(state.repository)?.button.focus(); });
    $('git-commit-message').addEventListener('input', () => { state.drafts.set(App.draftKey(), $('git-commit-message').value); App.renderToolbar(); });
    $('git-commit-form').addEventListener('submit', event => { event.preventDefault(); guard(App.submitCommit); });
    document.querySelectorAll('a[href]').forEach(link => link.addEventListener('click', event => {
        if ((![...state.drafts.values()].some(draft => draft.trim()) && !App.editorDirty()) || event.ctrlKey || event.metaKey) return;
        event.preventDefault(); ConfirmationModal.show({ title: 'Leave commit drafts?', message: 'Commit messages on this page have not been submitted. Leave and discard these drafts?', onConfirm: () => { window.location.href = link.href; } });
    }));
    let refreshTimer;
    function returned() {
        if (document.hidden || $('git-refresh').disabled) return;
        clearTimeout(refreshTimer); refreshTimer = setTimeout(() => guard(App.refreshOnReturn), 150);
    }
    window.addEventListener('focus', returned); document.addEventListener('visibilitychange', returned);
    if (matchMedia('(max-width:700px)').matches) $('git-diff-mode').value = 'unified';
    // Stable test and debugging surface; functions resolve through the shared namespace.
    window.WorkspaceGit = { state, discover: App.discover, refreshStatuses: App.refreshStatuses, chooseRepository: App.chooseRepository, chooseFile: App.chooseFile, mutate: App.mutate,
        actionDialog: App.actionDialog, renderDiff: App.renderDiff, setCommitMode: App.setCommitMode, commitTargets: App.commitTargets };
    $('git-amend').addEventListener('change', event => App.setAmend(event.target.checked));
    $('git-branch-button').addEventListener('click', () => guard(App.switchBranch));
    $('git-open-editor').addEventListener('click', () => guard(App.openInVsCode));
    App.initLayout(); App.initSync(); App.initHistory(); App.initStashes(); App.initShortcuts(); App.initEditor(); App.connect(); guard(() => App.discover());
})(window.WorkspaceGitApp);
