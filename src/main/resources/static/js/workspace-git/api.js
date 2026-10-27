/* Workspace Git page: api. Scripts share window.WorkspaceGitApp and load in template order. */
(App => {
    'use strict';
    const { $, announce, repoUrl, repository, status } = App;
    const fail = error => {
        announce(error.message || 'Git request failed. Refresh and try again.');
        ConfirmationModal.error({ title: 'Git operation could not complete', message: error.message || 'Refresh the repository and try again.' });
    };
    const guard = action => Promise.resolve().then(action).catch(fail);
    /** JSON transport; data is sent with POST unless {@code options.method} says otherwise, and {@code options.signal} abandons superseded reads. */
    async function request(url, data, options = {}) {
        const init = data === undefined ? { globalSpinner: false } : { globalSpinner: false, method: options.method || 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) };
        const response = await fetch(url, options.signal ? { ...init, signal: options.signal } : init);
        let body;
        try { body = await response.json(); } catch (_) { body = {}; }
        if (!response.ok) {
            const error = new Error(body.message || `Git request failed (${response.status}). Refresh and try again.`);
            error.status = response.status; error.code = body.code;
            // Identities are forgotten by a server restart: rediscover once, then repeat reads but never mutations.
            if (error.code === 'unknown-repository' && !options.retried && !rediscovery) {
                await rediscover();
                if (data === undefined) return request(url, data, { ...options, retried: true });
                error.message = 'Repositories were refreshed because the server no longer recognized this one. Review the current state, then repeat the action.';
            }
            throw error;
        }
        return body;
    }
    let rediscovery = null;
    function rediscover() {
        rediscovery ||= Promise.resolve().then(() => App.discover(true)).catch(() => {}).finally(() => { rediscovery = null; });
        return rediscovery;
    }
    const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
    /** Yields to the browser; frames are paused in background tabs, so a short timer resolves it there. */
    const nextFrame = () => new Promise(resolve => { requestAnimationFrame(() => resolve()); setTimeout(resolve, 50); });
    // Listing versions identify changes by metadata; mutations submit content-identified snapshots.
    const snapshot = async id => (await request(`${repoUrl(id)}/status?full=true`)).version;
    const withSnapshots = targets => Promise.all(targets.map(async target => ({ ...target, version: await snapshot(target.id) })));
    Object.assign(App, { nextFrame, fail, guard, request, delay, snapshot, withSnapshots });
})(window.WorkspaceGitApp = window.WorkspaceGitApp || {});
