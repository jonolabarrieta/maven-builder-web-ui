/* Workspace Git page: state. Scripts share window.WorkspaceGitApp and load in template order. */
(App => {
    'use strict';
    const $ = id => document.getElementById(id);
    const workspaceId = document.body.dataset.workspaceId;
    const workspaceName = document.body.dataset.workspaceName;
    const base = `/workspaces/${workspaceId}/git`;
    const state = { repositories: [], statuses: new Map(), views: new Map(), selected: new Set(),
        drafts: new Map(), files: new Map(), expanded: new Set(), collapsedGroups: new Set(), visited: new Set(), markAll: new Set(), busy: new Set(), repository: null, file: null, diff: null, lines: new Set(),
        generation: 0, comparisonGeneration: 0, statusGenerations: new Map(), activeBlock: 0, operations: new Map(), operationMeta: new Map(),
        openFolders: new Set(), folderFiles: new Map(), comparison: null,
        statusAt: new Map(), channel: 'idle', highlightMaxLines: Number(document.body.dataset.highlightMaxLines) || 5000,
        staleFetchHours: Number(document.body.dataset.staleFetchHours) || 24, refreshIntervalMs: Math.max(5, Number(document.body.dataset.refreshInterval) || 60) * 1000 };
    const announce = text => { $('git-announcement').textContent = text; };
    const node = (tag, className, text) => {
        const element = document.createElement(tag);
        if (className) element.className = className;
        if (text !== undefined) element.textContent = text;
        return element;
    };
    const repoUrl = id => `${base}/repositories/${encodeURIComponent(id)}`;
    const changed = file => file.kind === 'conflict' || file.working !== '.';
    const staged = file => !['.', '?'].includes(file.index);
    const entryKey = file => `${file.area}\0${file.path}`;
    const repository = () => state.repositories.find(repo => repo.id === state.repository);
    const status = () => state.statuses.get(state.repository);
    function entries(current) {
        const result = [];
        for (const file of current?.files || []) {
            if (file.kind === 'conflict') result.push({ ...file, area: 'changes', group: 'Conflicts' });
            else {
                if (changed(file)) result.push({ ...file, area: 'changes', group: 'Changes' });
                if (staged(file)) result.push({ ...file, area: 'staged', group: 'Staged Changes' });
            }
        }
        return result;
    }
    function synchronize(parent, children) {
        children.forEach((child, index) => { if (parent.children[index] !== child) parent.insertBefore(child, parent.children[index] || null); });
        while (parent.children.length > children.length) parent.lastElementChild.remove();
    }
    /** Folder name, with parent folders only where two repositories share a name. */
    const repoName = repo => repo?.displayName || repo?.name || 'Repository';
    const repoById = id => state.repositories.find(repo => repo.id === id);
    /** The branch badge text: branch name, or the detached/unborn state. */
    function branchBadge(current) {
        if (!current || current.error) return '';
        if (current.branch === 'detached HEAD') return 'detached';
        return current.unborn ? `${current.branch} · no commits` : current.branch;
    }
    function upstreamLabel(current) {
        if (!current) return 'Reading Git status…';
        if (current.error) return current.error;
        return current.upstream ? `${current.ahead ?? '?'} ahead · ${current.behind ?? '?'} behind · ${current.upstream}` : 'No upstream';
    }
    const pad = value => String(value).padStart(2, '0');
    /** Local date and time for file names, e.g. 20261028-1405. */
    function stamp(date = new Date()) { return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}-${pad(date.getHours())}${pad(date.getMinutes())}`; }
    /** Downloads plain text lines as {@code <workspace>-<what>-<stamp>.txt}. */
    function download(what, lines) {
        const safe = String(workspaceName || 'workspace').replace(/[^\w.-]+/g, '-');
        const link = node('a'); link.href = URL.createObjectURL(new Blob([lines.join('\n') + '\n'], { type: 'text/plain;charset=utf-8' }));
        link.download = `${safe}-${what}-${stamp()}.txt`; document.body.append(link); link.click(); link.remove();
        setTimeout(() => URL.revokeObjectURL(link.href), 1000);
    }
    function branchLabel(current) {
        if (!current) return 'Reading Git status…';
        if (current.error) return current.error;
        const synchronization = current.upstream ? `${current.ahead ?? '?'} ahead · ${current.behind ?? '?'} behind · ${current.upstream}` : 'No upstream';
        return `${current.branch}${current.unborn ? ' · no commits yet' : ''} · ${synchronization}`;
    }
    Object.assign(App, { $, workspaceId, workspaceName, base, state, announce, node, repoUrl, changed, staged, entryKey, repository, status, entries, synchronize, branchLabel, repoName, repoById, branchBadge, upstreamLabel, stamp, download });
})(window.WorkspaceGitApp = window.WorkspaceGitApp || {});
