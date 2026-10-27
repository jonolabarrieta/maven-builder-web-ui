/* Workspace Git page: choice dialog for actions that need a selection (remote, branch) before confirming. */
(App => {
    'use strict';
    const { $, node } = App;
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
    Object.assign(App, { choose });
})(window.WorkspaceGitApp = window.WorkspaceGitApp || {});
