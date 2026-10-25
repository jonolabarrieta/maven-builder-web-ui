/** Consumer-scoped project groups. Shared projects retain their source identity. */
(function () {
    'use strict';
    const table = () => document.getElementById('project-table-body');
    const consumerId = () => document.getElementById('workspace-git-status')?.dataset.url?.match(/workspaces\/(\d+)/)?.[1];
    const rows = group => Array.from(table()?.querySelectorAll('tr[data-id]') || []).filter(row => !group || row.dataset.contextGroupId === String(group));
    const identity = row => `${row.dataset.consumerId}:${row.dataset.contextGroupId || ''}:${row.dataset.id}`;
    let identities = new Map();
    let modalContext = {};
    const storageKey = () => `mvnbuilder:collapsed-groups:${consumerId()}`;
    function collapsed() { try { return new Set(JSON.parse(localStorage.getItem(storageKey()) || '[]')); } catch (_) { return new Set(); } }
    function syncSelection(selected) {
        document.querySelectorAll('.group-checkbox').forEach(box => {
            const members = rows(box.dataset.groupId), count = members.filter(r => selected.has(r.dataset.id)).length;
            box.checked = members.length > 0 && count === members.length;
            box.indeterminate = count > 0 && count < members.length;
            box.disabled = members.length === 0;
        });
    }
    function reconcile(selected) {
        const current = new Map(rows().map(r => [r.dataset.id, identity(r)]));
        for (const id of selected) if (identities.has(id) && identities.get(id) !== current.get(id)) selected.delete(id);
        identities = current;
    }
    function initialize() {
        const hidden = collapsed();
        document.querySelectorAll('.group-disclosure').forEach(button => {
            const folded = hidden.has(button.dataset.groupId);
            rows(button.dataset.groupId).forEach(row => { row.hidden = folded; });
            document.querySelectorAll(`.workspace-group-end[data-group-id="${button.dataset.groupId}"]`).forEach(footer => { footer.hidden = folded; });
            button.setAttribute('aria-expanded', String(!folded));
            button.textContent = folded ? 'Expand' : 'Collapse';
            button.setAttribute('aria-label', `${folded ? 'Expand' : 'Collapse'} group ${button.dataset.groupName || ''}`.trim());
        });
        rows().filter(r => r.dataset.imported === 'true').forEach(row => row.querySelectorAll('[data-move], .project-order-handle').forEach(button => { button.disabled = true; button.setAttribute('aria-disabled', 'true'); }));
        const selection = window.WorkspaceProjectSelection;
        if (selection) { reconcile(selection.selected); selection.render(); }
    }
    async function request(path, options) {
        const response = await fetch(path, options);
        if (!response.ok) {
            let message = await response.text();
            try { const parsed = JSON.parse(message); message = parsed.detail || parsed.message || parsed.error || message; } catch (_) {}
            throw new Error(message || 'The workspace changed. Refresh and try again.');
        }
        return response;
    }
    async function refreshTable() {
        await htmx.ajax('GET', `/workspaces/${consumerId()}/project-table`, {target: '#project-table-body', swap: 'outerHTML'});
        initialize();
    }
    window.openGroupModal = function () {
        document.getElementById('group-modal')?.classList.remove('hidden');
    };
    window.openParentWorkspacesModal = function () {
        document.getElementById('parent-workspaces-modal')?.classList.remove('hidden');
    };
    const report = error => ConfirmationModal.error({title: 'Group action failed', message: error.message});
    async function run(group, data, button) {
        if (button) button.disabled = true;
        try {
            if (data.get('action') === 'build') { window.BuildRecovery?.reset(); if (typeof clearLogs === 'function') clearLogs(); }
            const response = await request(`/workspaces/${consumerId()}/groups/${group}/action`, {method: 'POST', body: data});
            const message = await response.text();
            if (message.startsWith('No eligible')) ConfirmationModal.show({title: 'No repositories to update', message, variant: 'info'});
            if (['enable', 'disable', 'checkout'].includes(data.get('action'))) await refreshTable();
        } catch (error) { report(error); }
        finally { if (button) button.disabled = false; }
    }
    function confirmAction(group, data, button, label) {
        const action = data.get('action');
        const paths = [...new Set(rows(group).map(r => r.querySelector('[data-path]')?.dataset.path).filter(Boolean))];
        if (['discard', 'unstage', 'stash', 'checkout', 'pull'].includes(action)) {
            ConfirmationModal.show({title: `${action} in ${label || 'group'}?`,
                message: `This acts on repositories used by the group. Projects sharing a repository, including in other workspaces, will observe the same working-tree changes. Project folders:\n${paths.join('\n')}`,
                variant: action === 'discard' ? 'danger' : 'confirm', confirmLabel: 'Run action',
                requireTyped: action === 'discard' ? label : null,
                onConfirm: () => run(group, data, button)});
        } else run(group, data, button);
    }
    document.addEventListener('click', event => {
        const disclosure = event.target.closest('.group-disclosure');
        if (disclosure) {
            const hidden = collapsed(), id = disclosure.dataset.groupId;
            if (hidden.has(id)) hidden.delete(id); else hidden.add(id);
            try { localStorage.setItem(storageKey(), JSON.stringify([...hidden])); } catch (_) {}
            initialize();
        }
        const build = event.target.closest('.group-build');
        if (build) { const data = new FormData(); data.set('action', 'build'); run(build.dataset.groupId, data, build); }
        const remove = event.target.closest('[data-confirm-group-remove]');
        if (remove) {
            event.preventDefault();
            const form = remove.closest('form'), name = remove.dataset.confirmGroupRemove || 'this group';
            if (remove.dataset.imported === 'true') {
                ConfirmationModal.show({
                    title: 'Remove parent workspace?',
                    message: `Remove "${name}" from this workspace? Its projects will be removed from this workspace immediately. The parent workspace and files on disk are kept.`,
                    variant: 'danger',
                    confirmLabel: 'Remove parent workspace',
                    onConfirm: () => form.submit()
                });
            } else {
                ConfirmationModal.show({
                    title: 'Delete group?',
                    message: `Delete "${name}"? Its projects will stay in this workspace without a group unless you also choose to delete them. Files on disk are always kept.`,
                    variant: 'danger',
                    confirmLabel: 'Delete group',
                    checkbox: {label: 'Also delete these projects from this workspace', checked: false},
                    onConfirm: deleteProjects => {
                        form.querySelector('input[name="deleteProjects"]').value = deleteProjects ? 'true' : 'false';
                        form.submit();
                    }
                });
            }
        }
        const removeParent = event.target.closest('[data-confirm-parent-remove]');
        if (removeParent) {
            event.preventDefault();
            const form = removeParent.closest('form'), name = removeParent.dataset.confirmParentRemove || 'this parent workspace';
            ConfirmationModal.show({
                title: 'Remove parent workspace?',
                message: `Remove "${name}" from this workspace? Its projects will be removed from this workspace immediately. The parent workspace and files on disk are kept.`,
                variant: 'danger',
                confirmLabel: 'Remove parent workspace',
                onConfirm: () => form.submit()
            });
        }
        const move = event.target.closest('.block-move');
        if (move) moveBlock(move).catch(report);
        const context = event.target.closest('[data-consumer-id]');
        if (context) modalContext = {consumerId: context.dataset.consumerId, contextGroupId: context.dataset.contextGroupId};
    });
    document.addEventListener('change', event => {
        if (!event.target.matches('.group-checkbox')) return;
        const selection = window.WorkspaceProjectSelection;
        rows(event.target.dataset.groupId).forEach(row => { if (event.target.checked) selection.selected.add(row.dataset.id); else selection.selected.delete(row.dataset.id); });
        selection.render();
    });
    document.addEventListener('submit', event => {
        if (event.target.matches('.group-refresh-form')) {
            event.preventDefault();
            const form = event.target, button = form.querySelector('button');
            button.disabled = true; form.setAttribute('aria-busy', 'true');
            request(form.action, {method: 'POST', headers: {'X-Group-Refresh': 'true'}}).then(refreshTable).catch(report).finally(() => {
                button.disabled = false; form.removeAttribute('aria-busy');
            });
            return;
        }
        if (event.target.matches('#group-projects-form')) {
            const selected = event.target.querySelectorAll('input[name="projectIds"]:not(:disabled):checked');
            if (!selected.length) {
                event.preventDefault();
                report(new Error('Select at least one project for this group.'));
            }
            return;
        }
        if (!event.target.matches('.group-action-form')) return;
        event.preventDefault(); const form = event.target, data = new FormData(form);
        for (const name of ['includeUntracked', 'createNew']) data.set(name, form.elements[name].checked ? 'true' : 'false');
        if (form.elements.selectedOnly.checked) {
            const ids = rows(form.dataset.groupId).filter(r => window.WorkspaceProjectSelection.selected.has(r.dataset.id)).map(r => r.dataset.id);
            if (!ids.length) { report(new Error('Select at least one member of this group.')); return; }
            ids.forEach(id => data.append('projectIds', id));
        }
        confirmAction(form.dataset.groupId, data, form.querySelector('button'), form.dataset.groupName);
    });
    function contextFor(path, element) {
        const id = String(path).match(/\/projects\/(\d+)/)?.[1];
        if (!id) return null;
        const scope = element?.closest?.('[data-consumer-id]');
        const row = rows().find(r => r.dataset.id === id);
        return scope ? {consumerId: scope.dataset.consumerId, contextGroupId: scope.dataset.contextGroupId}
            : row ? {consumerId: row.dataset.consumerId, contextGroupId: row.dataset.contextGroupId} : modalContext;
    }
    document.addEventListener('htmx:configRequest', event => {
        const context = contextFor(event.detail.path, event.detail.elt);
        if (context?.consumerId) { event.detail.parameters.consumerId = context.consumerId; if (context.contextGroupId) event.detail.parameters.contextGroupId = context.contextGroupId; }
    });
    // Existing project modal actions also use fetch; carry the same validated context.
    const originalFetch = window.fetch;
    window.fetch = function (input, options) {
        if (typeof input === 'string' && input.startsWith('/projects/')) {
            const context = contextFor(input);
            if (context?.consumerId) { const url = new URL(input, location.origin); url.searchParams.set('consumerId', context.consumerId); if (context.contextGroupId) url.searchParams.set('contextGroupId', context.contextGroupId); input = url.pathname + url.search; }
        }
        return originalFetch.call(this, input, options);
    };
    function blockKeys() { return [...new Set(Array.from(table()?.querySelectorAll('[data-block-key]') || [], r => r.dataset.blockKey))]; }
    async function saveOrder(url, order) {
        await request(url, {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(order)});
        await refreshTable();
    }
    async function moveBlock(button) {
        const header = button.closest('[data-block-key]'), keys = blockKeys(), index = keys.indexOf(header.dataset.blockKey), target = index + Number(button.dataset.direction);
        if (target < 0 || target >= keys.length) return;
        [keys[index], keys[target]] = [keys[target], keys[index]];
        await saveOrder(`/workspaces/${consumerId()}/groups/order`, keys);
        table()?.querySelector(`[data-block-key="${header.dataset.blockKey}"] .block-move[data-direction="${button.dataset.direction}"]`)?.focus();
    }
    const legacyMove = window.moveProject;
    window.moveProject = async function (button, direction) {
        if (!table()?.querySelector('.workspace-group-header')) return legacyMove(button, direction);
        const row = button.closest('tr[data-id]');
        if (row.dataset.imported === 'true') return;
        const group = row.dataset.contextGroupId;
        if (!group) { button.dataset.direction = String(direction); return moveBlock(button).catch(report); }
        const ids = rows(group).map(r => r.dataset.id), index = ids.indexOf(row.dataset.id), next = index + direction;
        if (next < 0 || next >= ids.length) return;
        [ids[index], ids[next]] = [ids[next], ids[index]];
        try { await saveOrder(`/workspaces/${consumerId()}/groups/${group}/order`, ids); table()?.querySelector(`tr[data-id="${row.dataset.id}"] [data-move="${direction}"]`)?.focus(); } catch (error) { report(error); }
    };
    document.addEventListener('htmx:afterSwap', initialize);
    document.addEventListener('DOMContentLoaded', initialize);
    window.WorkspaceGroups = {initialize, syncSelection, reconcile};
})();
