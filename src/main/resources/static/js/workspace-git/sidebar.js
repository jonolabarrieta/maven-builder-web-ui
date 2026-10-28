/* Workspace Git page: sidebar. Scripts share window.WorkspaceGitApp and load in template order. */
(App => {
    'use strict';
    const { $, base, state, announce, node, repoUrl, entryKey, repository, status, entries, synchronize, repoName, branchBadge, upstreamLabel } = App;
    const { guard, request, delay } = App;
    function visible(repo) {
        const term = $('git-search').value.trim().toLowerCase();
        const current = state.statuses.get(repo.id);
        if ($('git-changed-only').checked && current && !current.files.length) return false;
        return !term || `${repoName(repo)} ${current?.branch || ''}`.toLowerCase().includes(term) || current?.files.some(file => file.path.toLowerCase().includes(term));
    }
    function markFile(id, file, selected) {
        let files = state.files.get(id);
        if (!files) { files = new Map(); state.files.set(id, files); }
        if (selected) files.set(file.path, { path: file.path, area: file.area });
        else if (files.get(file.path)?.area === file.area) files.delete(file.path);
    }
    function selectionChanged(id) { renderRepositories([id]); App.renderToolbar(); }
    const markable = file => file.kind !== 'gitlink' && file.kind !== 'conflict';
    /** Files a repository checkbox marks; a path listed in both areas counts once, from Changes. */
    function eligible(id) {
        const unique = new Map();
        for (const file of entries(state.statuses.get(id))) if (markable(file) && !unique.has(file.path)) unique.set(file.path, file);
        return [...unique.values()];
    }
    function counts(id) {
        const files = eligible(id); const marks = state.files.get(id);
        if (!marks?.size) return { eligible: files.length, marked: 0 };
        const present = new Set(entries(state.statuses.get(id)).map(entryKey));
        return { eligible: files.length, marked: [...marks.values()].filter(mark => present.has(entryKey(mark))).length };
    }
    function markAllFiles(id) { state.files.set(id, new Map(eligible(id).map(file => [file.path, { path: file.path, area: file.area }]))); }
    /** Checking a repository marks all of its files and targets it for bulk actions; unchecking clears both. */
    function setRepositoryChecked(id, checked) {
        checked ? state.selected.add(id) : state.selected.delete(id);
        const current = state.statuses.get(id);
        if (!current || current.error) { checked ? state.markAll.add(id) : state.markAll.delete(id); if (!checked) state.files.delete(id); return; }
        state.markAll.delete(id);
        if (checked) markAllFiles(id); else state.files.delete(id);
    }
    /** Repositories with marked files, plus clean repositories the user checked. */
    function bulkTargets() {
        return state.repositories.map(repo => repo.id).filter(id => { const { eligible: files, marked } = counts(id); return marked > 0 || (files === 0 && state.selected.has(id)); });
    }
    function disclosure(label) {
        const button = node('button', 'git-disclosure'); button.type = 'button'; button.setAttribute('aria-label', label);
        const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        svg.setAttribute('viewBox', '0 0 16 16'); svg.setAttribute('fill', 'none'); svg.setAttribute('stroke', 'currentColor'); svg.setAttribute('stroke-width', '1.6'); svg.setAttribute('aria-hidden', 'true');
        const path = document.createElementNS('http://www.w3.org/2000/svg', 'path'); path.setAttribute('d', 'm6 3 5 5-5 5'); svg.append(path); button.append(svg); return button;
    }
    const folderKey = (id, path) => `${id}\0${path}`;
    const statusMark = file => file.kind === 'conflict' ? 'Conflict' : file.kind === 'untracked' ? 'New' : file.kind === 'untracked-dir' ? 'New folder'
        : file.area === 'staged' ? file.index : file.working;
    /** Files of an expanded untracked folder are listed under it and behave like individual untracked files. */
    async function loadFolder(id, path) {
        const key = folderKey(id, path); state.folderFiles.set(key, 'loading'); renderRepositories([id]);
        try {
            const files = await request(`${repoUrl(id)}/untracked?dir=${encodeURIComponent(path)}`);
            if (state.openFolders.has(key)) state.folderFiles.set(key, files.map(file => ({ ...file, area: 'changes', group: 'Changes', folder: path })));
        } catch (error) { state.folderFiles.set(key, { error: error.message }); }
        renderRepositories([id]);
    }
    function toggleFolder(id, path) {
        const key = folderKey(id, path);
        if (state.openFolders.delete(key)) { state.folderFiles.delete(key); renderRepositories([id]); return; }
        state.openFolders.add(key); return loadFolder(id, path);
    }
    function fileRow(repo, view, file, label) {
        const key = entryKey(file); let item = view.buttons.get(key);
        if (!item) {
            const button = node('button', 'git-file'); button.type = 'button'; button.append(node('span', '', file.path), node('span', 'git-file-status'));
            button.addEventListener('click', () => guard(async () => {
                if (!(await App.leaveEditor())) return;
                if (state.repository !== repo.id) await chooseRepository(repo.id, { reconcile: false });
                await App.chooseFile({ path: file.path, area: file.area });
            }));
            const row = node('div', 'git-file-row'); const checkbox = node('input'); checkbox.type = 'checkbox';
            checkbox.setAttribute('aria-label', `Select ${file.path} from ${label} in ${repoName(repo)} for commit`);
            checkbox.addEventListener('change', () => { markFile(repo.id, file, checkbox.checked); selectionChanged(repo.id); });
            const stage = node('button', 'git-file-action', file.area === 'staged' ? '−' : '+'); stage.type = 'button';
            stage.setAttribute('aria-label', `${file.area === 'staged' ? 'Unstage' : 'Stage'} ${file.path}`); stage.title = file.area === 'staged' ? 'Unstage file' : 'Stage file';
            stage.addEventListener('click', () => guard(() => stageFiles(repo, [file])));
            row.append(checkbox, button, stage);
            if (file.kind === 'untracked-dir') {
                const folder = disclosure(`Expand untracked folder ${file.path}`); folder.classList.add('git-folder-toggle');
                folder.addEventListener('click', () => guard(() => toggleFolder(repo.id, file.path))); row.insertBefore(folder, button);
                item = { row, checkbox, button, folder, stage };
            } else item = { row, checkbox, button, stage };
            view.buttons.set(key, item);
        }
        item.row.classList.toggle('git-folder-file', Boolean(file.folder));
        item.checkbox.disabled = file.kind === 'conflict' || file.kind === 'gitlink';
        item.stage.hidden = file.kind === 'conflict' || file.kind === 'gitlink'; item.stage.disabled = state.busy.has(repo.id);
        item.checkbox.checked = state.files.get(repo.id)?.get(file.path)?.area === file.area;
        const mark = statusMark(file);
        item.button.lastElementChild.textContent = mark;
        item.button.title = file.oldPath ? `${file.oldPath} → ${file.path}` : file.path;
        item.button.setAttribute('aria-label', `${file.path}, ${label}, ${mark}`);
        item.button.setAttribute('aria-pressed', String(Boolean(state.repository === repo.id && state.file && entryKey(state.file) === key)));
        if (item.folder) {
            const open = state.openFolders.has(folderKey(repo.id, file.path));
            item.folder.setAttribute('aria-expanded', String(open));
            item.folder.setAttribute('aria-label', `${open ? 'Collapse' : 'Expand'} untracked folder ${file.path}`);
        }
        return item.row;
    }
    /** Rows for a group, with the loaded contents of open untracked folders directly below each folder. */
    function groupRows(repo, view, files, label) {
        const rows = [];
        for (const file of files) {
            rows.push(fileRow(repo, view, file, label));
            if (file.kind !== 'untracked-dir' || !state.openFolders.has(folderKey(repo.id, file.path))) continue;
            const contents = state.folderFiles.get(folderKey(repo.id, file.path));
            if (Array.isArray(contents)) contents.forEach(child => rows.push(fileRow(repo, view, child, label)));
            else rows.push(node('p', 'git-folder-state', contents === 'loading' || !contents ? 'Loading folder…' : contents.error));
        }
        return rows;
    }
    /**
     * Stages (Changes) or unstages (Staged Changes) whole files from the list. Each file is validated against a fresh
     * content snapshot, because every change to the index produces a new one.
     */
    async function stageFiles(repo, files) {
        const id = repo.id; if (!files.length || state.busy.has(id)) return;
        state.busy.add(id); renderRepositories([id]);
        let done = 0;
        try {
            for (const file of files) {
                const version = await App.snapshot(id);
                await request(`${repoUrl(id)}/${file.area === 'staged' ? 'unstage' : 'stage'}`, { path: file.path, area: file.area, version, lines: null, all: false });
                done++;
            }
            announce(files.length === 1 ? `${files[0].path} ${files[0].area === 'staged' ? 'unstaged' : 'staged'}.` : `${done} files staged.`);
        } finally {
            state.busy.delete(id); await refreshStatus(repo); flushRender();
            if (id === state.repository) await App.reconcileFile();
            App.renderToolbar();
        }
    }
    function repositoryView(repo) {
        let view = state.views.get(repo.id);
        if (view) return view;
        const section = node('section', 'git-repository');
        const heading = node('div', 'git-repository-heading');
        const checkbox = node('input'); checkbox.type = 'checkbox'; checkbox.setAttribute('aria-label', `Select repository ${repoName(repo)} and all of its files`);
        checkbox.addEventListener('change', () => { setRepositoryChecked(repo.id, checkbox.checked); renderRepositories([repo.id]); App.renderToolbar(); });
        const toggle = disclosure(`Expand files in ${repoName(repo)}`);
        toggle.addEventListener('click', () => { state.expanded.has(repo.id) ? state.expanded.delete(repo.id) : state.expanded.add(repo.id); renderRepositories([repo.id]); });
        const button = node('button'); button.type = 'button'; const name = node('span', 'git-repo-name', repoName(repo)); const count = node('span', 'git-count'); button.append(name, count);
        button.title = repo.absolutePath; button.addEventListener('click', () => guard(async () => { if (await App.leaveEditor()) await chooseRepository(repo.id); }));
        heading.append(checkbox, toggle, button); const meta = node('div', 'git-repo-meta'); const files = node('div');
        const badge = node('span', 'git-branch-badge'); const upstream = node('span', 'git-repo-upstream'); meta.append(badge, upstream);
        section.append(heading, meta, files);
        view = { section, checkbox, toggle, button, count, meta, badge, upstream, files, groups: new Map(), buttons: new Map() }; state.views.set(repo.id, view);
        return view;
    }
    /** Updates one repository entry in place; other entries are not touched. */
    function renderRepository(repo) {
        const view = repositoryView(repo); const current = state.statuses.get(repo.id);
        view.section.hidden = !visible(repo); view.section.classList.toggle('is-active', state.repository === repo.id);
        view.button.setAttribute('aria-pressed', String(state.repository === repo.id));
        const { eligible: files, marked } = counts(repo.id);
        view.checkbox.checked = files ? marked === files : state.selected.has(repo.id) || state.markAll.has(repo.id);
        view.checkbox.indeterminate = marked > 0 && marked < files;
        view.count.textContent = current?.error ? 'Error' : current ? String(current.files.length) : '…';
        const badge = branchBadge(current); view.badge.textContent = badge; view.badge.hidden = !badge;
        view.badge.classList.toggle('is-detached', current?.branch === 'detached HEAD');
        view.badge.title = badge ? `Current branch: ${current.branch}` : '';
        view.upstream.textContent = upstreamLabel(current);
        const open = state.expanded.has(repo.id);
        view.toggle.setAttribute('aria-expanded', String(open));
        view.toggle.setAttribute('aria-label', `${open ? 'Collapse' : 'Expand'} files in ${repoName(repo)}`);
        const groups = [];
        if (open) {
            for (const label of ['Staged Changes', 'Changes', 'Conflicts']) {
                const files = entries(current).filter(file => file.group === label);
                if (!files.length) continue;
                let group = view.groups.get(label);
                const groupKey = `${repo.id}\0${label}`;
                if (!group) {
                    const section = node('div', 'git-file-group'); const heading = node('div', 'git-file-group-heading');
                    const checkbox = node('input'); checkbox.type = 'checkbox'; checkbox.disabled = label === 'Conflicts';
                    checkbox.setAttribute('aria-label', `Select all files in ${label} for ${repoName(repo)}`);
                    checkbox.addEventListener('change', () => {
                        entries(state.statuses.get(repo.id)).filter(file => file.group === label && file.kind !== 'gitlink' && file.kind !== 'conflict').forEach(file => markFile(repo.id, file, checkbox.checked));
                        selectionChanged(repo.id);
                    });
                    const toggle = disclosure(`Collapse ${label} in ${repoName(repo)}`); const title = node('span'); toggle.append(title);
                    toggle.addEventListener('click', () => { state.collapsedGroups.has(groupKey) ? state.collapsedGroups.delete(groupKey) : state.collapsedGroups.add(groupKey); renderRepositories([repo.id]); });
                    const action = node('button', 'git-group-action', label === 'Staged Changes' ? 'Unstage all' : 'Stage all'); action.type = 'button';
                    action.setAttribute('aria-label', `${action.textContent} in ${label} for ${repoName(repo)}`); action.hidden = label === 'Conflicts';
                    action.addEventListener('click', () => {
                        if (label === 'Staged Changes') return App.actionDialog('unstage-all', [repo.id]);
                        guard(() => stageFiles(repo, entries(state.statuses.get(repo.id)).filter(file => file.group === label && file.kind !== 'gitlink' && file.kind !== 'conflict')));
                    });
                    const list = node('div'); heading.append(checkbox, toggle, action); section.append(heading, list);
                    group = { section, heading, title, checkbox, toggle, list, action }; view.groups.set(label, group);
                }
                const eligible = files.filter(file => file.kind !== 'gitlink' && file.kind !== 'conflict');
                const marked = eligible.filter(file => state.files.get(repo.id)?.get(file.path)?.area === file.area).length;
                group.title.textContent = `${label} (${files.length})`;
                group.action.disabled = !eligible.length || state.busy.has(repo.id);
                group.checkbox.disabled = !eligible.length; group.checkbox.checked = eligible.length > 0 && marked === eligible.length; group.checkbox.indeterminate = marked > 0 && marked < eligible.length;
                const expanded = !state.collapsedGroups.has(groupKey); group.list.hidden = !expanded;
                group.toggle.setAttribute('aria-expanded', String(expanded));
                group.toggle.setAttribute('aria-label', `${expanded ? 'Collapse' : 'Expand'} ${label} in ${repoName(repo)}`);
                synchronize(group.list, groupRows(repo, view, files, label)); groups.push(group.section);
            }
        }
        synchronize(view.files, groups);
        return view.section;
    }
    function renderSummary() {
        const uncommitted = state.repositories.filter(repo => state.statuses.get(repo.id)?.files.length).length;
        $('git-changed-count').textContent = `(${uncommitted})`;
        $('git-changed-count').setAttribute('aria-label', `${uncommitted} ${uncommitted === 1 ? 'repository has' : 'repositories have'} uncommitted changes`);
        $('git-count').textContent = `${state.repositories.length} ${state.repositories.length === 1 ? 'repository' : 'repositories'}`;
        renderSelection(); App.renderToolbar();
    }
    /** Full render: used after discovery and when search or filters change the visible set. */
    function renderSidebar() {
        pending.clear();
        const rows = state.repositories.map(renderRepository);
        if (!rows.length) rows.push(node('p', 'git-empty', 'No repositories. The Git view lists the repositories of workspace projects, including disabled ones; add projects or refresh repositories.'));
        synchronize($('git-repositories'), rows);
        renderSummary();
    }
    function renderRepositories(ids) {
        for (const id of new Set(ids)) { const repo = state.repositories.find(item => item.id === id); if (repo && state.views.has(id)) renderRepository(repo); else if (repo) return renderSidebar(); }
        renderSummary();
    }
    // Status responses arrive one by one; each marks only its repository and rendering happens once per frame.
    // Frames are paused in background tabs, so a short timer also flushes pending entries.
    const pending = new Set(); let scheduled = false;
    function scheduleRepository(id) {
        pending.add(id);
        if (scheduled) return;
        scheduled = true; requestAnimationFrame(flushRender); setTimeout(flushRender, 50);
    }
    function flushRender() {
        scheduled = false;
        if (!pending.size) return;
        const ids = [...pending]; pending.clear(); renderRepositories(ids);
    }
    function renderSelection() {
        const targets = new Set(bulkTargets());
        $('git-selected-count').textContent = `${targets.size} selected`;
        const available = state.repositories.filter(visible); const selected = available.filter(repo => targets.has(repo.id)).length;
        $('git-select-all').checked = available.length > 0 && selected === available.length;
        $('git-select-all').indeterminate = selected > 0 && selected < available.length;
    }
    async function refreshStatus(repo, generation = state.generation) {
        const version = (state.statusGenerations.get(repo.id) || 0) + 1; state.statusGenerations.set(repo.id, version);
        try {
            const response = await request(`${repoUrl(repo.id)}/status`);
            if (generation !== state.generation || version !== state.statusGenerations.get(repo.id)) return;
            state.statuses.set(repo.id, response); state.statusAt.set(repo.id, Date.now());
            const selection = state.files.get(repo.id);
            if (selection) for (const [path, file] of selection)
                if (!entries(response).some(entry => entry.path === path && entry.area === file.area)) selection.delete(path);
            // A repository checked before its status arrived marks its files now.
            if (state.markAll.delete(repo.id)) markAllFiles(repo.id);
        } catch (error) {
            if (generation !== state.generation || version !== state.statusGenerations.get(repo.id)) return;
            state.statuses.set(repo.id, { files: [], error: error.message });
        }
        for (const key of state.openFolders) if (key.startsWith(`${repo.id}\0`)) {
            const path = key.slice(repo.id.length + 1);
            if (state.statuses.get(repo.id).files.some(file => file.path === path && file.kind === 'untracked-dir')) loadFolder(repo.id, path);
            else { state.openFolders.delete(key); state.folderFiles.delete(key); }
        }
        scheduleRepository(repo.id);
    }
    async function refreshStatuses() {
        const generation = state.generation; let next = 0;
        await Promise.all(Array.from({ length: Math.min(4, state.repositories.length) }, async () => {
            while (next < state.repositories.length && generation === state.generation) await refreshStatus(state.repositories[next++], generation);
        }));
        flushRender();
        if (generation !== state.generation) return;
        if (state.repository) await App.reconcileFile();
    }
    /** Returning to the page refreshes the active repository now and only stale others, a few at a time. */
    async function refreshOnReturn() {
        const generation = state.generation; const active = repository();
        if (active) { await refreshStatus(active, generation); flushRender(); if (generation === state.generation) await App.reconcileFile(); }
        const now = Date.now();
        const stale = state.repositories.filter(repo => repo !== active && now - (state.statusAt.get(repo.id) || 0) >= state.refreshIntervalMs);
        for (let i = 0; i < stale.length && generation === state.generation; i += 4) {
            await Promise.all(stale.slice(i, i + 4).map(repo => refreshStatus(repo, generation)));
            if (i + 4 < stale.length) await delay(100);
        }
        flushRender();
    }
    async function discover(refresh = false) {
        const generation = ++state.generation;
        $('git-refresh').disabled = true; announce('Finding the repositories of the workspace projects…');
        try {
            const result = await App.watch(await request(`${base}/repositories${refresh ? '?refresh=true' : ''}`), refresh ? { action: 'discovery', label: 'Refresh repositories' } : null);
            if (generation !== state.generation) return;
            if (!result.discovery) throw new Error(result.outcomes.find(outcome => outcome.state === 'failed')?.message || 'Repository discovery failed. Check the workspace project folders.');
            state.repositories = result.discovery.repositories;
            state.selected = new Set([...state.selected].filter(id => state.repositories.some(repo => repo.id === id)));
            state.markAll = new Set([...state.markAll].filter(id => state.repositories.some(repo => repo.id === id)));
            for (const id of state.files.keys()) if (!state.repositories.some(repo => repo.id === id)) state.files.delete(id);
            if (!state.repositories.some(repo => repo.id === state.repository)) { state.repository = null; state.file = null; state.diff = null; }
            $('git-warnings').textContent = [...result.discovery.warnings, ...(result.discovery.complete ? [] : ['Discovery incomplete. Some projects could not be inspected.'])].join('\n');
            renderSidebar(); await refreshStatuses();
            if (!state.repository && state.repositories.length) await chooseRepository(state.repositories[0].id);
            announce(`${state.repositories.length} repositories discovered. Remote information reflects the last fetch.${result.discovery.complete ? '' : ' Discovery incomplete; review warnings.'}`);
        } finally { if (generation === state.generation) $('git-refresh').disabled = false; }
    }
    /** {@code reconcile: false} is used when the caller selects a specific file right after switching. */
    async function chooseRepository(id, { reconcile = true } = {}) {
        const switched = state.repository !== id;
        if (switched) {
            App.cancelComparison();
            if (App.draftKey()) state.drafts.set(App.draftKey(), $('git-commit-message').value);
            if (!state.visited.has(id)) { state.expanded.add(id); state.visited.add(id); }
            state.repository = id; state.file = null; state.diff = null; state.lines.clear();
            $('git-commit-message').value = state.drafts.get(App.draftKey()) || '';
        }
        renderSidebar();
        if (!status() || status().error) { await refreshStatus(repository()); flushRender(); }
        if (switched) guard(() => Promise.all([App.refreshHistory(), App.refreshStashes()]));
        if (reconcile) await App.reconcileFile();
    }
    Object.assign(App, { stageFiles, visible, markFile, selectionChanged, setRepositoryChecked, bulkTargets, counts, disclosure, renderSidebar, renderRepositories, flushRender, refreshOnReturn, renderSelection, refreshStatus, refreshStatuses, discover, chooseRepository });
})(window.WorkspaceGitApp = window.WorkspaceGitApp || {});
