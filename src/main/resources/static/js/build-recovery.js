/* Recovery actions reference a server-owned failed queue, never the current selection. */
(function () {
    'use strict';
    let options, failure = null, busy = false, generation = 0;
    const used = new Set();
    const byId = id => document.getElementById(id);
    function reset() {
        if (failure?.continuationId) used.add(failure.continuationId);
        failure = null; busy = false; generation++;
        byId('retry-container')?.classList.add('hidden');
    }
    function buttons(disabled) {
        for (const id of ['retry-button', 'skip-failed-button']) byId(id).disabled = disabled;
        byId('retry-container').setAttribute('aria-busy', String(disabled));
    }
    async function submit(mode) {
        if (busy || !failure) return;
        const submitted = failure, requestGeneration = generation;
        busy = true; buttons(true);
        try {
            const body = new URLSearchParams({ mode });
            const response = await fetch(`/workspaces/${options.workspaceId}/build-continuations/${encodeURIComponent(submitted.continuationId)}`, { method: 'POST', body });
            if (!response.ok) {
                let message = 'This recovery action is unavailable. Review the workspace and start a fresh build.';
                try { const data = await response.json(); message = data.detail || data.message || message; } catch (_) { /* retain useful local guidance */ }
                throw new Error(message);
            }
            const result = await response.json();
            used.add(submitted.continuationId);
            if (requestGeneration !== generation) return;
            reset();
            if (result.state === 'empty') options.onEmpty(result.message);
            else options.beforeStart?.();
        } catch (error) {
            if (requestGeneration === generation) options.onError(error);
        } finally {
            if (requestGeneration === generation) { busy = false; buttons(false); }
        }
    }
    function show(value) {
        if (!options || Number(value.workspaceId) !== Number(options.workspaceId) || !value.continuationId || used.has(value.continuationId)) return;
        failure = value; generation++; busy = false;
        byId('failed-project-name').textContent = value.projectName;
        byId('retry-container').classList.remove('hidden'); buttons(false);
    }
    window.BuildRecovery = { reset, show, init(value) {
        options = value;
        byId('retry-button').onclick = () => submit('retry');
        byId('skip-failed-button').onclick = () => submit('skip');
    } };
}());
