/* Workspace Git page: choice dialog for actions that need a selection (remote, branch), and commit confirmation. */
(App => {
    'use strict';
    const { $, node, state, repoById, repoName } = App;
    /**
     * Opens the page's modal choice dialog. Options are rendered as a select; an optional text field collects a
     * free value. Resolves with { choice, text } on confirmation, or null when cancelled.
     */
    function choose({ title, message = '', label = 'Choose', options = [], value = null, text = null, confirmLabel = 'Continue' }) {
        const dialog = $('git-choice-dialog');
        $('git-choice-title').textContent = title; $('git-choice-message').textContent = message;
        $('git-choice-label').textContent = label;
        // Options carrying a group are placed in labelled optgroups, in first-seen order.
        const select = $('git-choice-select'); const groups = new Map(); const children = [];
        options.forEach(option => {
            const element = node('option', '', option.label); element.value = option.value;
            if (!option.group) return children.push(element);
            if (!groups.has(option.group)) { const group = node('optgroup'); group.label = option.group; groups.set(option.group, group); children.push(group); }
            groups.get(option.group).append(element);
        });
        select.replaceChildren(...children);
        if (value !== null) select.value = value;
        $('git-choice-select-row').hidden = !options.length;
        const input = $('git-choice-text'); $('git-choice-text-row').hidden = !text;
        if (text) { $('git-choice-text-label').textContent = text.label; input.value = text.value || ''; input.placeholder = text.placeholder || ''; }
        $('git-choice-confirm').textContent = confirmLabel;
        return new Promise(resolve => {
            const finish = result => { dialog.removeEventListener('close', closed); $('git-choice-form').removeEventListener('submit', submitted); if (dialog.open) dialog.close(); resolve(result); };
            // close events are delivered asynchronously; one left over from a previous use must not cancel this one.
            const closed = () => { if (!dialog.open) finish(null); };
            const submitted = event => {
                event.preventDefault();
                if (event.submitter?.value === 'cancel') return finish(null);
                finish({ choice: options.length ? select.value : null, text: text ? input.value.trim() : null });
            };
            dialog.addEventListener('close', closed); $('git-choice-form').addEventListener('submit', submitted);
            dialog.showModal(); (text && !options.length ? input : options.length ? select : $('git-choice-confirm')).focus();
        });
    }
    const AREAS = { changes: 'working tree', staged: 'staged version' };
    /** Lists every target repository with its marked files; resolves true only when the user confirms. */
    function confirmCommit({ targets, push, message }) {
        const dialog = $('git-commit-dialog');
        const files = targets.reduce((sum, target) => sum + target.files.length, 0);
        $('git-commit-dialog-title').textContent = push ? 'Commit and push?' : 'Commit marked files?';
        $('git-commit-dialog-summary').textContent = `${files} ${files === 1 ? 'file' : 'files'} in ${targets.length} ${targets.length === 1 ? 'repository' : 'repositories'}. `
            + (push ? 'Each repository gets its own commit, then its current branch is pushed.' : 'Each repository gets its own commit. Nothing is pushed.')
            + ' Unmarked changes, including staged ones, are not committed.';
        $('git-commit-dialog-message').textContent = message.trim().split('\n')[0];
        const items = targets.map(target => {
            const repo = repoById(target.id); const current = state.statuses.get(target.id);
            const item = node('li', 'git-commit-dialog-repository');
            const heading = node('div', 'git-commit-dialog-heading');
            heading.append(node('strong', '', repoName(repo)));
            if (current?.branch) heading.append(node('span', 'git-branch-badge', current.branch));
            heading.append(node('span', 'git-commit-dialog-count', `${target.files.length} ${target.files.length === 1 ? 'file' : 'files'}`));
            const list = node('ul');
            target.files.forEach(file => { const entry = node('li'); entry.append(node('code', '', file.path), node('span', 'git-commit-dialog-area', AREAS[file.area] || file.area)); list.append(entry); });
            item.title = repo?.absolutePath || ''; item.append(heading, list); return item;
        });
        $('git-commit-dialog-list').replaceChildren(...items);
        $('git-commit-dialog-confirm').textContent = push ? 'Commit & Push' : 'Commit';
        return new Promise(resolve => {
            const finish = result => { dialog.removeEventListener('close', closed); $('git-commit-dialog-form').removeEventListener('submit', submitted); if (dialog.open) dialog.close(); resolve(result); };
            const closed = () => { if (!dialog.open) finish(false); };
            const submitted = event => { event.preventDefault(); finish(event.submitter?.value === 'confirm'); };
            dialog.addEventListener('close', closed); $('git-commit-dialog-form').addEventListener('submit', submitted);
            dialog.showModal(); $('git-commit-dialog-confirm').focus();
        });
    }
    Object.assign(App, { choose, confirmCommit });
})(window.WorkspaceGitApp = window.WorkspaceGitApp || {});
