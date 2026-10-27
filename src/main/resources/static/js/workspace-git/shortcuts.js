/* Workspace Git page: single-key shortcuts, active only while focus is outside text entry and dialogs. */
(App => {
    'use strict';
    const { $, state } = App;
    const { guard } = App;
    const typing = target => target.closest?.('input, textarea, select, [contenteditable="true"], .CodeMirror');
    const dialogOpen = () => [...document.querySelectorAll('dialog')].some(dialog => dialog.open)
        || Boolean(document.getElementById('confirmation-modal-backdrop')?.offsetParent);
    /** File buttons of the active repository in list order, including expanded untracked folders. */
    function fileButtons() {
        const view = state.views.get(state.repository);
        return view ? [...view.files.querySelectorAll('.git-file')].filter(button => button.offsetParent !== null) : [];
    }
    function moveFile(direction) {
        const buttons = fileButtons(); if (!buttons.length) return;
        const index = buttons.findIndex(button => button.getAttribute('aria-pressed') === 'true');
        const next = buttons[Math.min(buttons.length - 1, Math.max(0, index === -1 ? 0 : index + direction))];
        if (next && next !== buttons[index]) { next.click(); next.focus({ preventScroll: true }); next.scrollIntoView({ block: 'nearest' }); }
    }
    function stageCurrent() {
        const file = state.file; const repo = state.repositories.find(item => item.id === state.repository);
        if (!repo || !file || !['changes', 'staged'].includes(file.area)) return;
        const entry = state.statuses.get(repo.id)?.files.find(item => item.path === file.path);
        if (entry && ['conflict', 'gitlink'].includes(entry.kind)) return;
        guard(() => App.stageFiles(repo, [{ path: file.path, area: file.area }]));
    }
    const KEYS = {
        j: () => moveFile(1), k: () => moveFile(-1), s: stageCurrent,
        ']': () => App.moveBlock(1), '[': () => App.moveBlock(-1),
        c: () => { $('git-commit-message').focus(); }, '?': () => $('git-shortcuts-dialog').showModal()
    };
    function initShortcuts() {
        document.addEventListener('keydown', event => {
            if (event.ctrlKey || event.metaKey || event.altKey || event.defaultPrevented) return;
            const action = KEYS[event.key];
            if (!action || typing(event.target) || dialogOpen()) return;
            event.preventDefault(); action();
        });
        $('git-shortcuts-open').addEventListener('click', () => $('git-shortcuts-dialog').showModal());
        $('git-shortcuts-close').addEventListener('click', () => $('git-shortcuts-dialog').close());
    }
    Object.assign(App, { initShortcuts });
})(window.WorkspaceGitApp = window.WorkspaceGitApp || {});
