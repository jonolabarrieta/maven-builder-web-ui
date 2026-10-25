/** Keyboard and screen-reader behavior for the application's existing overlays. */
(function () {
    'use strict';

    const labels = {
        'new-workspace-modal': 'New Workspace',
        'import-workspace-modal': 'Import Workspace',
        'edit-workspace-modal': 'Workspace Settings',
        'branch-modal': 'Select Branch',
        'bulk-branch-modal': 'Change Selected Branches',
        'm2-modal': 'Local M2 Repository',
        'explorer-modal': 'Add Projects',
        'directory-explorer-modal': 'Choose base folder',
        'children-modal': 'Project Submodules',
        'project-properties-modal': 'Project Properties',
        'profile-manager-modal': 'Manage Build Profiles',
        'group-modal': 'Project Group',
        'parent-workspaces-modal': 'Parent Workspaces'
    };
    const focusableSelector = 'a[href], button:not([disabled]), input:not([disabled]):not([type="hidden"]), '
        + 'select:not([disabled]), textarea:not([disabled]), summary, [tabindex]:not([tabindex="-1"])';
    const observed = new WeakSet();
    const stack = [];
    const observer = new MutationObserver(syncDialogs);
    let recentTrigger = null;
    let triggerTime = 0;

    document.addEventListener('click', event => {
        recentTrigger = event.target.closest('button, a, summary');
        triggerTime = Date.now();
    }, true);

    function confirmationIsOpen() {
        const confirmation = document.getElementById('confirmation-modal-overlay');
        return confirmation && getComputedStyle(confirmation).display !== 'none';
    }

    function focusableInside(dialog) {
        return Array.from(dialog.querySelectorAll(focusableSelector)).filter(element => {
            const closedDetails = element.closest('details:not([open])');
            return element.getClientRects().length > 0
                && getComputedStyle(element).visibility !== 'hidden'
                && (!closedDetails || closedDetails.querySelector('summary') === element);
        });
    }

    function focusInside(dialog) {
        const controls = focusableInside(dialog);
        const firstField = controls.find(element => element.matches('input, select, textarea'));
        (firstField || controls[0] || dialog).focus();
    }

    function registerDialogs() {
        document.querySelectorAll('[id$="-modal"]').forEach(dialog => {
            if (!Object.hasOwn(labels, dialog.id) || observed.has(dialog)) return;
            observed.add(dialog);
            dialog.setAttribute('role', 'dialog');
            dialog.setAttribute('aria-modal', 'true');
            dialog.setAttribute('aria-label', labels[dialog.id]);
            dialog.tabIndex = -1;
            observer.observe(dialog, { attributes: true, attributeFilter: ['class'] });
        });
    }

    function syncDialogs() {
        registerDialogs();
        const previousTop = stack.at(-1);
        for (let index = stack.length - 1; index >= 0; index--) {
            const dialog = stack[index].dialog;
            if (!dialog.isConnected || dialog.classList.contains('hidden')) stack.splice(index, 1);
        }

        const visible = Array.from(document.querySelectorAll('[id$="-modal"]')).filter(dialog =>
            observed.has(dialog) && !dialog.classList.contains('hidden'));
        let opened = false;
        for (const dialog of visible) {
            if (stack.some(entry => entry.dialog === dialog)) continue;
            const opener = Date.now() - triggerTime < 250 && recentTrigger?.isConnected
                ? recentTrigger : document.activeElement;
            stack.push({ dialog, opener });
            opened = true;
            if (!confirmationIsOpen()) focusInside(dialog);
        }

        if (previousTop && !stack.some(entry => entry.dialog === previousTop.dialog) && !opened && !confirmationIsOpen()) {
            const opener = previousTop.opener;
            const closedDetails = opener?.closest('details:not([open])');
            const summary = closedDetails?.querySelector('summary');
            if (summary && opener !== summary) {
                summary.focus();
            } else if (opener && opener.isConnected && opener.getClientRects().length > 0) {
                opener.focus();
            } else if (stack.length) {
                focusInside(stack.at(-1).dialog);
            }
        }
    }

    document.addEventListener('keydown', event => {
        if (!stack.length || confirmationIsOpen()) return;
        const dialog = stack.at(-1).dialog;
        if (event.key === 'Escape') {
            event.preventDefault();
            event.stopPropagation();
            dialog.classList.add('hidden');
            return;
        }
        if (event.key !== 'Tab') return;
        const controls = focusableInside(dialog);
        if (!controls.length) {
            event.preventDefault();
            dialog.focus();
        } else if (event.shiftKey && (document.activeElement === controls[0] || !dialog.contains(document.activeElement))) {
            event.preventDefault();
            controls.at(-1).focus();
        } else if (!event.shiftKey && (document.activeElement === controls.at(-1) || !dialog.contains(document.activeElement))) {
            event.preventDefault();
            controls[0].focus();
        }
    }, true);

    document.addEventListener('focusin', event => {
        if (!stack.length || confirmationIsOpen()) return;
        const dialog = stack.at(-1).dialog;
        if (!dialog.contains(event.target)) focusInside(dialog);
    });

    document.addEventListener('htmx:afterSwap', () => {
        syncDialogs();
        const dialog = stack.at(-1)?.dialog;
        if (dialog && (document.activeElement === dialog || !dialog.contains(document.activeElement))) {
            focusInside(dialog);
        }
    });

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', syncDialogs, { once: true });
    } else {
        syncDialogs();
    }
})();
