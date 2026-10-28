/**
 * RUN entries on the workspace page: launch modal, configuration editor, console
 * tabs for running processes and the build guard prompt.
 *
 * Console integration reuses the page's tab helpers (projectTabs, logLines,
 * switchTab, closeTab, appendLineToContainer). RUN tabs use the key "run:<id>" so
 * they never collide with build labels.
 */
(function () {
    'use strict';

    const MAX_LINES = 2000;
    const tabs = new Map(); // key -> { entryId, name, state, exitCode, lastSeq }

    const wsId = () => (typeof workspaceId !== 'undefined' ? workspaceId : null);
    const keyOf = entryId => `run:${entryId}`;
    const show = id => document.getElementById(id)?.classList.remove('hidden');
    const hide = id => document.getElementById(id)?.classList.add('hidden');

    async function failureText(response, fallback) {
        try {
            const text = (await response.text()).trim();
            if (text && !['<', '{', '['].includes(text[0])) return text;
            const parsed = JSON.parse(text);
            return parsed.message || parsed.detail || parsed.error || fallback;
        } catch (_) {
            return fallback;
        }
    }

    // ---- Modals ---------------------------------------------------------------------

    function openLaunch() {
        show('run-launch-modal');
        htmx.ajax('GET', `/workspaces/${wsId()}/run/modal`, { target: '#run-launch-modal-content' });
    }

    function openConfig() {
        hide('run-launch-modal');
        document.getElementById('workspace-actions-menu')?.removeAttribute('open');
        show('run-config-modal');
        htmx.ajax('GET', `/workspaces/${wsId()}/run/entries`, { target: '#run-config-modal-content' });
    }

    function updateLaunchSubmit() {
        const form = document.getElementById('run-launch-form');
        const submit = document.getElementById('run-launch-submit');
        if (!form || !submit) return;
        submit.disabled = !form.querySelector('input[name="entryIds"]:checked:not(:disabled)');
    }

    async function submitLaunch(form) {
        const data = new FormData(form);
        if (!data.getAll('entryIds').length) return;
        const submit = document.getElementById('run-launch-submit');
        if (submit) submit.disabled = true;
        try {
            const response = await fetch(form.dataset.startUrl, { method: 'POST', body: data });
            if (!response.ok) throw new Error(await failureText(response, 'The selected entries could not be started.'));
            const result = await response.json();
            hide('run-launch-modal');
            if (result.started.length) {
                result.started.forEach(id => ensureTab(id, nameFromForm(form, id)));
                switchTab(keyOf(result.started[0]));
            }
            if (result.errors.length) {
                ConfirmationModal.error({ title: 'Some entries did not start', message: result.errors.join('\n') });
            }
        } catch (error) {
            ConfirmationModal.error({ title: 'Unable to start', message: error.message });
        } finally {
            updateLaunchSubmit();
        }
    }

    function nameFromForm(form, id) {
        const input = form.querySelector(`input[name="entryIds"][value="${id}"]`);
        return input?.closest('label')?.querySelector('.font-semibold')?.textContent || `Entry ${id}`;
    }

    function addEnvRow() {
        const rows = document.getElementById('run-env-rows');
        if (!rows) return;
        const row = document.createElement('div');
        row.className = 'run-env-row flex gap-2';
        row.innerHTML = '<input name="envName" type="text" aria-label="Variable name" placeholder="NAME" class="w-2/5 px-3 py-2 bg-gray-50 border border-gray-200 rounded-xl text-sm font-mono outline-none focus:ring-2 focus:ring-indigo-500">'
            + '<input name="envValue" type="text" aria-label="Variable value" placeholder="value" class="flex-1 px-3 py-2 bg-gray-50 border border-gray-200 rounded-xl text-sm font-mono outline-none focus:ring-2 focus:ring-indigo-500">'
            + '<button type="button" data-run-env-remove class="px-2 py-1 rounded-lg border border-gray-200 text-xs text-gray-700 hover:bg-gray-50" aria-label="Remove variable">Remove</button>';
        rows.appendChild(row);
        row.querySelector('input').focus();
    }

    function syncDirectoryPlaceholder() {
        const select = document.getElementById('run-entry-project');
        const input = document.getElementById('run-entry-directory');
        if (!select || !input) return;
        const directory = select.selectedOptions[0]?.dataset.directory;
        input.placeholder = directory ? directory : 'Required when no project is selected';
    }

    function initializeConfig() {
        syncDirectoryPlaceholder();
        const list = document.getElementById('run-entry-list');
        if (!list || list.dataset.sortable || typeof Sortable === 'undefined') return;
        list.dataset.sortable = 'true';
        Sortable.create(list, {
            handle: '.run-drag-handle',
            animation: 150,
            onEnd: async () => {
                const ids = [...list.querySelectorAll('[data-entry-id]')].map(li => Number(li.dataset.entryId));
                const response = await fetch(list.dataset.orderUrl, {
                    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(ids)
                });
                if (!response.ok) ConfirmationModal.error({ title: 'Unable to reorder', message: await failureText(response, 'The order could not be saved.') });
            }
        });
    }

    document.addEventListener('click', event => {
        const close = event.target.closest('[data-run-close]');
        if (close) { hide(close.dataset.runClose); return; }
        if (event.target.closest('[data-run-open]')) { openLaunch(); return; }
        if (event.target.closest('[data-run-open-config]')) { openConfig(); return; }
        if (event.target.closest('[data-run-env-add]')) { addEnvRow(); return; }
        const remove = event.target.closest('[data-run-env-remove]');
        if (remove) { remove.closest('.run-env-row')?.remove(); return; }
        const del = event.target.closest('[data-run-delete-url]');
        if (del) {
            ConfirmationModal.confirm({
                title: 'Delete RUN entry?',
                message: `Delete "${del.dataset.runDeleteName}"? If it is running it will be stopped.`,
                confirmLabel: 'Delete entry',
                onConfirm: () => htmx.ajax('DELETE', del.dataset.runDeleteUrl, { target: '#run-config-modal-content' })
            });
        }
    });
    document.addEventListener('change', event => {
        if (event.target.matches('#run-launch-form input[name="entryIds"]')) updateLaunchSubmit();
        if (event.target.id === 'run-entry-project') syncDirectoryPlaceholder();
    });
    document.addEventListener('submit', event => {
        if (event.target.id !== 'run-launch-form') return;
        event.preventDefault();
        submitLaunch(event.target);
    });
    document.addEventListener('htmx:afterSwap', event => {
        const id = event.detail.target?.id;
        if (id === 'run-launch-modal-content') updateLaunchSubmit();
        if (id === 'run-config-modal-content') initializeConfig();
    });

    // ---- Console tabs -----------------------------------------------------------------

    function ensureTab(entryId, name) {
        const key = keyOf(entryId);
        let tab = tabs.get(key);
        if (tab) {
            if (name && tab.name !== name) { tab.name = name; renderTab(key); }
            return tab;
        }
        tab = { entryId, name: name || `Entry ${entryId}`, state: 'RUNNING', exitCode: null, lastSeq: 0 };
        tabs.set(key, tab);
        projectTabs[key] = true;
        logLines[key] = [];

        const button = document.createElement('button');
        button.id = `tab-${key}`;
        button.onclick = () => switchTab(key);
        button.setAttribute('aria-pressed', 'false');
        button.className = 'px-3 py-2.5 text-[10px] font-bold uppercase tracking-widest transition-all border-b-2 border-transparent text-console-muted hover:text-gray-300 flex items-center space-x-2 shrink-0';
        const actions = document.createElement('span');
        actions.className = 'run-tab-actions flex items-center gap-1 mr-1';
        const group = document.createElement('div');
        group.className = 'project-log-tab run-log-tab flex items-center shrink-0';
        group.dataset.runKey = key;
        group.append(button, actions);
        group.addEventListener('auxclick', event => {
            if (event.button !== 1) return;
            event.preventDefault();
            event.stopPropagation();
            closeTab(key, event);
        });
        group.addEventListener('mousedown', event => { if (event.button === 1) event.preventDefault(); });
        document.getElementById('log-tabs').appendChild(group);

        const container = document.createElement('div');
        container.id = `log-container-${key}`;
        container.className = 'absolute inset-0 p-4 overflow-y-auto console text-gray-300 space-y-1 hidden';
        document.getElementById('log-containers').appendChild(container);
        renderTab(key);
        return tab;
    }

    function actionButton(label, title, onClick, extra) {
        const b = document.createElement('button');
        b.type = 'button';
        b.textContent = label;
        b.title = title;
        b.setAttribute('aria-label', title);
        b.className = `text-[10px] font-bold uppercase tracking-widest px-1 rounded hover:bg-white/10 ${extra}`;
        b.onclick = event => { event.stopPropagation(); onClick(); };
        return b;
    }

    function renderTab(key) {
        const tab = tabs.get(key);
        const button = document.getElementById(`tab-${key}`);
        const group = button?.closest('.run-log-tab');
        if (!tab || !button || !group) return;
        const running = tab.state === 'RUNNING';
        const crashed = tab.state === 'CRASHED';
        const dot = running ? 'bg-green-500' : crashed ? 'bg-red-500' : 'bg-gray-500';
        const status = running ? 'running' : crashed ? `crashed${tab.exitCode == null ? '' : ` (exit ${tab.exitCode})`}` : 'stopped';
        button.innerHTML = `<span class="w-1.5 h-1.5 ${dot} rounded-full"></span><span>&#9654; ${escapeHtml(tab.name)}</span>`
            + `<span class="${crashed ? 'text-red-400' : 'text-console-muted'} normal-case tracking-normal">${escapeHtml(status)}</span>`;
        button.title = `RUN ${tab.name}: ${status}`;
        const actions = group.querySelector('.run-tab-actions');
        actions.replaceChildren();
        if (running) actions.append(actionButton('Stop', `Stop ${tab.name}`, () => control(tab, 'stop'), 'text-red-400 hover:text-red-300'));
        actions.append(actionButton('Restart', `Restart ${tab.name}`, () => control(tab, 'restart'), 'text-indigo-400 hover:text-white'));
        if (!running) {
            const close = document.createElement('button');
            close.type = 'button';
            close.setAttribute('aria-label', `Close log for ${tab.name}`);
            close.className = 'icon-button text-console-muted hover:text-white hover:bg-white/10';
            close.onclick = event => closeTab(key, event);
            close.innerHTML = '<svg aria-hidden="true" xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" /></svg>';
            actions.append(close);
        }
    }

    async function control(tab, action) {
        try {
            const response = await fetch(`/workspaces/${wsId()}/run/${tab.entryId}/${action}`, { method: 'POST' });
            if (!response.ok) throw new Error(await failureText(response, `Unable to ${action} ${tab.name}.`));
        } catch (error) {
            ConfirmationModal.error({ title: `Unable to ${action}`, message: error.message });
        }
    }

    function appendLine(key, content) {
        const lines = logLines[key];
        if (!lines) return;
        lines.push(content);
        appendLineToContainer(key, null, content);
        if (lines.length > MAX_LINES) {
            lines.splice(0, lines.length - MAX_LINES);
            const container = document.getElementById(`log-container-${key}`);
            while (container && container.children.length > MAX_LINES) container.firstElementChild.remove();
        }
    }

    function apply(message) {
        const key = keyOf(message.entryId);
        const tab = ensureTab(message.entryId, message.name);
        if (message.type === 'line') {
            if (message.seq <= tab.lastSeq) return;
            tab.lastSeq = message.seq;
            appendLine(key, message.content);
            return;
        }
        if (message.started && logLines[key].length) appendLine(key, '──────── restarted ────────');
        tab.state = message.state;
        tab.exitCode = message.exitCode;
        renderTab(key);
    }

    /** Called by closeTab before a tab is removed; false keeps running RUN tabs open. */
    function beforeClose(key) {
        const tab = tabs.get(key);
        if (!tab) return true;
        if (tab.state === 'RUNNING') return false;
        tabs.delete(key);
        fetch(`/workspaces/${wsId()}/run/${tab.entryId}/log`, { method: 'DELETE' }).catch(() => {});
        return true;
    }

    /** Subscribes to this workspace's RUN topic and restores tabs from the server. */
    async function connected(client) {
        if (!wsId()) return;
        const pending = [];
        let restored = false;
        client.subscribe(`/topic/run/${wsId()}`, frame => {
            const message = JSON.parse(frame.body);
            if (restored) apply(message); else pending.push(message);
        });
        try {
            const response = await fetch(`/workspaces/${wsId()}/run/state`);
            if (response.ok) {
                for (const snapshot of await response.json()) {
                    const key = keyOf(snapshot.entryId);
                    const tab = ensureTab(snapshot.entryId, snapshot.name);
                    snapshot.lines.forEach(line => { if (line.seq > tab.lastSeq) { tab.lastSeq = line.seq; appendLine(key, line.content); } });
                    tab.lastSeq = Math.max(tab.lastSeq, snapshot.lastSeq);
                    tab.state = snapshot.state;
                    tab.exitCode = snapshot.exitCode;
                    renderTab(key);
                }
            }
        } catch (error) {
            console.error('Unable to restore RUN tabs', error);
        }
        restored = true;
        // Live messages that arrived while the snapshot loaded; lines already in it are skipped by seq.
        pending.forEach(message => {
            const tab = tabs.get(keyOf(message.entryId));
            if (message.type === 'state' && tab && message.seq < tab.lastSeq) return;
            apply(message);
        });
    }

    // ---- Build guard ------------------------------------------------------------------

    function withGuard(url, mode) {
        return url + (url.includes('?') ? '&' : '?') + 'runGuard=' + encodeURIComponent(mode);
    }

    /** Asks how to treat running entries; resolves to a mode or null when canceled. */
    function askGuard(body) {
        const names = (body.entries || []).map(e => e.name).join(', ');
        return new Promise(resolve => {
            let answered = false;
            ConfirmationModal.show({
                variant: 'confirm',
                title: 'Running services use these projects',
                message: `${names} ${body.entries && body.entries.length === 1 ? 'is' : 'are'} running from projects in this build.`,
                confirmLabel: 'Build',
                checkbox: { label: 'Stop them now and restart them after a successful build', checked: true },
                onConfirm: checked => { answered = true; resolve(checked === false ? 'IGNORE' : 'STOP_RESTART'); },
                onCancel: () => { if (!answered) resolve(null); },
                onClose: () => { if (!answered) resolve(null); }
            });
        });
    }

    async function guardBody(response) {
        if (response.status !== 409) return null;
        try {
            const body = await response.clone().json();
            return body && body.runGuard ? body : null;
        } catch (_) {
            return null;
        }
    }

    /** fetch() that answers the build guard and re-sends the request. */
    async function guardedFetch(url, init) {
        const response = await fetch(url, init);
        const body = await guardBody(response);
        if (!body) return response;
        const mode = await askGuard(body);
        if (!mode) return new Response('Build canceled.', { status: 200 });
        return fetch(withGuard(url, mode), init);
    }

    // htmx-triggered builds (Build All, ranges, submodules): answer the 409 and repeat.
    document.addEventListener('htmx:responseError', event => {
        const xhr = event.detail.xhr;
        if (!xhr || xhr.status !== 409) return;
        let body;
        try { body = JSON.parse(xhr.responseText); } catch (_) { return; }
        if (!body || !body.runGuard) return;
        event.detail.appErrorModalHandled = true;
        event.stopPropagation();
        const config = event.detail.requestConfig || {};
        const path = config.path || event.detail.pathInfo?.requestPath;
        if (!path) return;
        askGuard(body).then(mode => {
            if (mode) htmx.ajax((config.verb || 'post').toUpperCase(), withGuard(path, mode), { source: event.detail.elt, swap: 'none' });
        });
    }, true);

    window.RunTabs = Object.freeze({ connected, beforeClose, openLaunch, openConfig, isRunTab: key => tabs.has(key), title: key => tabs.get(key)?.name });
    window.RunGuard = Object.freeze({ fetch: guardedFetch, ask: askGuard });
})();
