/* Repository state, comparison selections, and commit drafts are scoped to this page. */
(() => {
    'use strict';
    const $ = id => document.getElementById(id);
    const workspaceId = document.body.dataset.workspaceId;
    const workspaceName = document.body.dataset.workspaceName;
    const base = `/workspaces/${workspaceId}/git`;
    const state = { repositories: [], statuses: new Map(), views: new Map(), selected: new Set(),
        drafts: new Map(), files: new Map(), expanded: new Set(), collapsedGroups: new Set(), visited: new Set(), commitMode: 'staged', busy: new Set(), repository: null, file: null, diff: null, lines: new Set(),
        generation: 0, comparisonGeneration: 0, statusGenerations: new Map(), activeBlock: 0, operations: new Map() };
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
    const fail = error => {
        announce(error.message || 'Git request failed. Refresh and try again.');
        ConfirmationModal.error({ title: 'Git operation could not complete', message: error.message || 'Refresh the repository and try again.' });
    };
    const guard = action => Promise.resolve().then(action).catch(fail);
    async function request(url, data) {
        const response = await fetch(url, data === undefined ? { globalSpinner: false } : { globalSpinner: false, method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) });
        let body;
        try { body = await response.json(); } catch (_) { body = {}; }
        if (!response.ok) {
            const error = new Error(body.message || `Git request failed (${response.status}). Refresh and try again.`);
            error.status = response.status; throw error;
        }
        return body;
    }
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
    function visible(repo) {
        const term = $('git-search').value.trim().toLowerCase();
        const current = state.statuses.get(repo.id);
        if ($('git-changed-only').checked && current && !current.files.length) return false;
        return !term || `${repo.name} ${repo.path}`.toLowerCase().includes(term) || current?.files.some(file => file.path.toLowerCase().includes(term));
    }
    function branchLabel(current) {
        if (!current) return 'Reading Git status…';
        if (current.error) return current.error;
        const synchronization = current.upstream ? `${current.ahead ?? '?'} ahead · ${current.behind ?? '?'} behind · ${current.upstream}` : 'No upstream';
        return `${current.branch}${current.unborn ? ' · no commits yet' : ''} · ${synchronization}`;
    }
    const draftKey = () => state.commitMode === 'selected-all' ? '__batch__' : state.repository;
    const selectedFiles = id => [...(state.files.get(id)?.values() || [])];
    function commitTargets() {
        const ids = state.commitMode === 'selected-all' ? state.repositories.map(repo => repo.id) : [state.repository];
        return ids.filter(id => selectedFiles(id).length).map(id => ({ id, version: state.statuses.get(id)?.version, files: selectedFiles(id) }));
    }
    function setCommitMode(mode) {
        const message = $('git-commit-message').value;
        if (draftKey()) state.drafts.set(draftKey(), message);
        state.commitMode = mode; $('git-commit-mode').value = mode;
        if (!state.drafts.has(draftKey())) state.drafts.set(draftKey(), message);
        $('git-commit-message').value = state.drafts.get(draftKey()) || '';
        renderToolbar();
    }
    function markFile(id, file, selected) {
        let files = state.files.get(id);
        if (!files) { files = new Map(); state.files.set(id, files); }
        if (selected) files.set(file.path, { path: file.path, area: file.area });
        else if (files.get(file.path)?.area === file.area) files.delete(file.path);
    }
    function selectionChanged() {
        const repositories = [...state.files.values()].filter(files => files.size).length;
        if (repositories && state.commitMode === 'staged') setCommitMode(repositories > 1 ? 'selected-all' : 'selected-current');
        else if (repositories > 1 && state.commitMode === 'selected-current') setCommitMode('selected-all');
        renderSidebar();
    }
    function disclosure(label) {
        const button = node('button', 'git-disclosure'); button.type = 'button'; button.setAttribute('aria-label', label);
        const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        svg.setAttribute('viewBox', '0 0 16 16'); svg.setAttribute('fill', 'none'); svg.setAttribute('stroke', 'currentColor'); svg.setAttribute('stroke-width', '1.6'); svg.setAttribute('aria-hidden', 'true');
        const path = document.createElementNS('http://www.w3.org/2000/svg', 'path'); path.setAttribute('d', 'm6 3 5 5-5 5'); svg.append(path); button.append(svg); return button;
    }
    function renderSidebar() {
        const rows = [];
        state.repositories.forEach(repo => {
            let view = state.views.get(repo.id);
            if (!view) {
                const section = node('section', 'git-repository');
                const heading = node('div', 'git-repository-heading');
                const checkbox = node('input'); checkbox.type = 'checkbox'; checkbox.setAttribute('aria-label', `Select repository ${repo.path}`);
                checkbox.addEventListener('change', () => { checkbox.checked ? state.selected.add(repo.id) : state.selected.delete(repo.id); renderSelection(); renderToolbar(); });
                const toggle = disclosure(`Expand files in ${repo.path}`);
                toggle.addEventListener('click', () => { state.expanded.has(repo.id) ? state.expanded.delete(repo.id) : state.expanded.add(repo.id); renderSidebar(); });
                const button = node('button'); button.type = 'button'; const name = node('span', 'git-repo-name', repo.name); const count = node('span', 'git-count'); button.append(name, count);
                button.title = repo.path; button.addEventListener('click', () => guard(() => chooseRepository(repo.id)));
                heading.append(checkbox, toggle, button); const meta = node('div', 'git-repo-meta'); const files = node('div');
                section.append(heading, meta, files); view = { section, checkbox, toggle, button, count, meta, files, groups: new Map(), buttons: new Map() }; state.views.set(repo.id, view);
            }
            const current = state.statuses.get(repo.id);
            view.section.hidden = !visible(repo); view.section.classList.toggle('is-active', state.repository === repo.id);
            view.button.setAttribute('aria-pressed', String(state.repository === repo.id));
            view.checkbox.checked = state.selected.has(repo.id); view.count.textContent = current?.error ? 'Error' : current ? String(current.files.length) : '…';
            view.meta.textContent = `${repo.path} · ${branchLabel(current)}`;
            const open = state.expanded.has(repo.id);
            view.toggle.setAttribute('aria-expanded', String(open));
            view.toggle.setAttribute('aria-label', `${open ? 'Collapse' : 'Expand'} files in ${repo.path}`);
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
                        checkbox.setAttribute('aria-label', `Select all files in ${label} for ${repo.path}`);
                        checkbox.addEventListener('change', () => { entries(state.statuses.get(repo.id)).filter(file => file.group === label && file.kind !== 'gitlink' && file.kind !== 'conflict').forEach(file => markFile(repo.id, file, checkbox.checked)); selectionChanged(); });
                        const toggle = disclosure(`Collapse ${label} in ${repo.path}`); const title = node('span'); toggle.append(title);
                        toggle.addEventListener('click', () => { state.collapsedGroups.has(groupKey) ? state.collapsedGroups.delete(groupKey) : state.collapsedGroups.add(groupKey); renderSidebar(); });
                        const list = node('div'); heading.append(checkbox, toggle); section.append(heading, list);
                        group = { section, heading, title, checkbox, toggle, list }; view.groups.set(label, group);
                    }
                    const eligible = files.filter(file => file.kind !== 'gitlink' && file.kind !== 'conflict');
                    const marked = eligible.filter(file => state.files.get(repo.id)?.get(file.path)?.area === file.area).length;
                    group.title.textContent = `${label} (${files.length})`;
                    group.checkbox.disabled = !eligible.length; group.checkbox.checked = eligible.length > 0 && marked === eligible.length; group.checkbox.indeterminate = marked > 0 && marked < eligible.length;
                    const expanded = !state.collapsedGroups.has(groupKey); group.list.hidden = !expanded;
                    group.toggle.setAttribute('aria-expanded', String(expanded));
                    group.toggle.setAttribute('aria-label', `${expanded ? 'Collapse' : 'Expand'} ${label} in ${repo.path}`);
                    const buttons = files.map(file => {
                        const key = entryKey(file); let item = view.buttons.get(key); let button = item?.button;
                        if (!button) {
                            button = node('button', 'git-file'); button.type = 'button'; button.append(node('span', '', file.path), node('span', 'git-file-status'));
                            button.addEventListener('click', () => guard(async () => { if (state.repository !== repo.id) await chooseRepository(repo.id); await chooseFile({ path: file.path, area: file.area }); }));
                            const row = node('div', 'git-file-row'); const checkbox = node('input'); checkbox.type = 'checkbox';
                            checkbox.disabled = file.kind === 'conflict' || file.kind === 'gitlink';
                            checkbox.setAttribute('aria-label', `Select ${file.path} from ${label} in ${repo.path} for commit`);
                            checkbox.addEventListener('change', () => { markFile(repo.id, file, checkbox.checked); selectionChanged(); });
                            row.append(checkbox, button); item = { row, checkbox, button }; view.buttons.set(key, item);
                        }
                        item.checkbox.disabled = file.kind === 'conflict' || file.kind === 'gitlink';
                        item.checkbox.checked = state.files.get(repo.id)?.get(file.path)?.area === file.area;
                        const mark = file.kind === 'conflict' ? 'Conflict' : file.kind === 'untracked' ? 'New' : file.area === 'staged' ? file.index : file.working;
                        button.lastElementChild.textContent = mark;
                        button.title = file.oldPath ? `${file.oldPath} → ${file.path}` : file.path;
                        button.setAttribute('aria-label', `${file.path}, ${label}, ${mark}`);
                        button.setAttribute('aria-pressed', String(Boolean(state.repository === repo.id && state.file && entryKey(state.file) === key)));
                        return item.row;
                    });
                    synchronize(group.list, buttons); groups.push(group.section);
                }
            }
            synchronize(view.files, groups); rows.push(view.section);
        });
        if (!rows.length) rows.push(node('p', 'git-empty', 'No repositories found. Check the workspace base folder or refresh repositories.'));
        synchronize($('git-repositories'), rows);
        $('git-count').textContent = `${state.repositories.length} ${state.repositories.length === 1 ? 'repository' : 'repositories'}`;
        renderSelection(); renderToolbar();
    }
    function renderSelection() {
        $('git-selected-count').textContent = `${state.selected.size} selected`;
        const available = state.repositories.filter(visible); const selected = available.filter(repo => state.selected.has(repo.id)).length;
        $('git-select-all').checked = available.length > 0 && selected === available.length;
        $('git-select-all').indeterminate = selected > 0 && selected < available.length;
    }
    function renderToolbar() {
        const repo = repository(); const current = status(); const busy = state.busy.has(state.repository);
        $('git-repository-name').textContent = repo?.name || 'Choose a repository';
        $('git-repository-branch').textContent = repo ? branchLabel(current) : 'Review changes, prepare a commit, then push.';
        $('git-repository-actions').hidden = !repo;
        document.querySelectorAll('[data-action]').forEach(button => { button.disabled = busy || !current || Boolean(current.error); });
        $('git-commit-message').disabled = !repo;
        const canCommit = repo && current && !current.error && !busy && current.files.some(staged) && !current.files.some(file => file.kind === 'conflict');
        const targets = commitTargets(); const selectedCount = targets.reduce((sum, target) => sum + target.files.length, 0);
        const totalFiles = [...state.files.values()].reduce((sum, files) => sum + files.size, 0);
        const totalRepositories = [...state.files.values()].filter(files => files.size).length;
        $('git-selected-files-count').textContent = `${totalFiles} ${totalFiles === 1 ? 'file' : 'files'} in ${totalRepositories} ${totalRepositories === 1 ? 'repository' : 'repositories'} selected`;
        const selectedReady = targets.length > 0 && targets.every(target => !state.busy.has(target.id) && state.statuses.get(target.id)?.version && !state.statuses.get(target.id)?.files.some(file => file.kind === 'conflict'));
        const isStaged = state.commitMode === 'staged';
        $('git-commit-button').disabled = !(isStaged ? canCommit : selectedReady) || !$('git-commit-message').value.trim();
        $('git-commit-button').textContent = isStaged ? 'Commit staged changes' : state.commitMode === 'selected-all' ? `Commit ${selectedCount} ${selectedCount === 1 ? 'file' : 'files'} in ${targets.length} ${targets.length === 1 ? 'repository' : 'repositories'}` : `Commit ${selectedCount} selected ${selectedCount === 1 ? 'file' : 'files'}`;
        $('git-commit-help').textContent = isStaged ? 'Commits include staged changes only. Push is a separate action.' : `${selectedCount} selected files · ${targets.length} ${targets.length === 1 ? 'repository' : 'repositories'}. Changes uses the working tree; Staged Changes uses the index. Unselected files keep their staging.`;
        $('git-push-selected').textContent = `Push selected (${state.selected.size})`;
        $('git-push-selected').disabled = !state.selected.size || [...state.selected].some(id => state.busy.has(id) || !state.statuses.get(id)?.version);
        $('git-commit-repository').textContent = state.commitMode === 'selected-all' ? `· ${targets.length} ${targets.length === 1 ? 'repository' : 'repositories'}` : repo ? `· ${repo.path}` : '';
        const diff = state.diff;
        $('git-selection-count').textContent = `${state.lines.size} ${state.lines.size === 1 ? 'line' : 'lines'} selected`;
        $('git-stage-lines').textContent = diff?.area === 'staged' ? 'Unstage selected lines' : 'Stage selected lines';
        $('git-stage-file').textContent = diff?.area === 'staged' ? 'Unstage file' : 'Stage file';
        $('git-stage-lines').disabled = busy || !diff?.partial || !state.lines.size;
        $('git-stage-file').disabled = busy || !diff || diff.kind === 'gitlink';
        $('git-discard-file').disabled = busy || !diff || diff.area !== 'changes' || current?.files.find(file => file.path === diff?.path)?.index === '?' || ['untracked', 'gitlink', 'conflict'].includes(current?.files.find(file => file.path === diff?.path)?.kind);
        $('git-select-block').disabled = busy || !diff?.partial;
        $('git-next-block').disabled = $('git-prev-block').disabled = !diff?.lines?.length;
    }
    async function refreshStatus(repo, generation = state.generation) {
        const version = (state.statusGenerations.get(repo.id) || 0) + 1; state.statusGenerations.set(repo.id, version);
        try {
            const response = await request(`${repoUrl(repo.id)}/status`);
            if (generation !== state.generation || version !== state.statusGenerations.get(repo.id)) return;
            state.statuses.set(repo.id, response);
            const selection = state.files.get(repo.id);
            if (selection) for (const [path, file] of selection)
                if (!entries(response).some(entry => entry.path === path && entry.area === file.area)) selection.delete(path);
        } catch (error) {
            if (generation !== state.generation || version !== state.statusGenerations.get(repo.id)) return;
            state.statuses.set(repo.id, { files: [], error: error.message });
        }
        renderSidebar();
    }
    async function refreshStatuses() {
        const generation = state.generation; let next = 0;
        await Promise.all(Array.from({ length: Math.min(4, state.repositories.length) }, async () => {
            while (next < state.repositories.length && generation === state.generation) await refreshStatus(state.repositories[next++], generation);
        }));
        if (generation !== state.generation) return;
        if (state.repository) await reconcileFile();
    }
    async function discover() {
        const generation = ++state.generation;
        $('git-refresh').disabled = true; announce('Discovering repositories under the workspace base folder…');
        try {
            const result = await watch(await request(`${base}/repositories`));
            if (generation !== state.generation) return;
            if (!result.discovery) throw new Error(result.outcomes.find(outcome => outcome.state === 'failed')?.message || 'Repository scan failed. Check the base folder.');
            state.repositories = result.discovery.repositories;
            state.selected = new Set([...state.selected].filter(id => state.repositories.some(repo => repo.id === id)));
            for (const id of state.files.keys()) if (!state.repositories.some(repo => repo.id === id)) state.files.delete(id);
            if (!state.repositories.some(repo => repo.id === state.repository)) { state.repository = null; state.file = null; state.diff = null; }
            $('git-warnings').textContent = [...result.discovery.warnings, ...(result.discovery.complete ? [] : ['Scan incomplete. Some folders could not be inspected.'])].join('\n');
            renderSidebar(); await refreshStatuses();
            if (!state.repository && state.repositories.length) await chooseRepository(state.repositories[0].id);
            announce(`${state.repositories.length} repositories discovered. Remote information reflects the last fetch.${result.discovery.complete ? '' : ' Scan incomplete; review warnings.'}`);
        } finally { if (generation === state.generation) $('git-refresh').disabled = false; }
    }
    async function chooseRepository(id) {
        if (state.repository !== id) {
            if (draftKey()) state.drafts.set(draftKey(), $('git-commit-message').value);
            if (!state.visited.has(id)) { state.expanded.add(id); state.visited.add(id); }
            state.repository = id; state.file = null; state.diff = null; state.lines.clear();
            $('git-commit-message').value = state.drafts.get(draftKey()) || '';
        }
        renderSidebar();
        if (!status() || status().error) await refreshStatus(repository());
        await reconcileFile();
    }
    async function reconcileFile() {
        const available = entries(status());
        const chosen = available.find(file => state.file && entryKey(file) === entryKey(state.file)) || available[0];
        if (chosen) await chooseFile(chosen, false);
        else {
            ++state.comparisonGeneration; state.file = null; state.diff = null; state.lines.clear();
            $('git-file-name').textContent = 'No pending files'; $('git-comparison-label').textContent = 'The working tree and staging area are clean.';
            $('git-diff').replaceChildren(node('p', 'git-empty', status()?.error || 'No pending changes in this repository.'));
            renderSidebar(); renderToolbar();
        }
    }
    async function chooseFile(file, reveal = true) {
        const generation = ++state.comparisonGeneration; const id = state.repository;
        const same = state.file && entryKey(state.file) === entryKey(file); const scroll = same ? $('git-diff').scrollTop : 0;
        const previousVersion = state.diff?.version; const selected = new Set(state.lines);
        state.file = { path: file.path, area: file.area }; state.diff = null; state.lines.clear(); renderSidebar();
        if (reveal) document.body.classList.add('git-show-diff');
        $('git-file-name').textContent = file.path;
        $('git-comparison-label').textContent = file.area === 'staged' ? 'Last commit → Staging area' : 'Staging area → Working tree';
        $('git-diff').setAttribute('aria-busy', 'true'); renderToolbar();
        try {
            const response = await request(`${repoUrl(id)}/diff?path=${encodeURIComponent(file.path)}&area=${file.area}`);
            if (generation !== state.comparisonGeneration || id !== state.repository) return;
            state.diff = response; if (!same) state.activeBlock = response.lines[0]?.block || 0;
            if (same && previousVersion === response.version) state.lines = selected;
            await renderDiff(); if (generation === state.comparisonGeneration) $('git-diff').scrollTop = scroll;
        } catch (error) {
            if (generation !== state.comparisonGeneration) return;
            $('git-diff').replaceChildren(node('p', 'git-empty', error.message)); announce(error.message);
        } finally { if (generation === state.comparisonGeneration) { $('git-diff').setAttribute('aria-busy', 'false'); renderToolbar(); } }
    }
    function choice(line) {
        const cell = node('td', 'git-line-choice');
        if (!line || line.type === ' ' || !state.diff.partial) return cell;
        const checkbox = node('input'); checkbox.type = 'checkbox'; checkbox.dataset.line = line.id; checkbox.checked = state.lines.has(line.id);
        checkbox.setAttribute('aria-label', `Select ${line.type === '+' ? 'added' : 'deleted'} line ${line.newNumber ?? line.oldNumber}`);
        cell.append(checkbox); return cell;
    }
    function lineCells(line, side) {
        const number = node('td', 'git-line-number', line ? String(side === 'old' ? line.oldNumber ?? '' : line.newNumber ?? '') : '');
        const text = node('td', 'git-line-text', line?.text || '');
        if (line && line.type !== ' ') { const style = line.type === '+' ? 'git-added' : 'git-deleted'; number.classList.add(style); text.classList.add(style); text.dataset.lineHighlight = line.id; }
        if (line && !line.newline) text.append(node('span', 'git-no-newline', 'No newline at end of file'));
        return [number, text];
    }
    async function renderDiff() {
        const diff = state.diff; if (!diff) return;
        const generation = state.comparisonGeneration;
        const container = $('git-diff'); container.replaceChildren();
        if (diff.reason) container.append(node('p', 'git-empty', diff.reason));
        if (!diff.lines.length) return;
        const split = $('git-diff-mode').value === 'split';
        const comparison = diff.area === 'staged' ? ['Last commit', 'Staging area'] : ['Staging area', 'Working tree'];
        const table = node('table'); const caption = node('caption', '', split ? `${comparison[0]} (left) · ${comparison[1]} (right)` : `${comparison[0]} → ${comparison[1]} · Removed and added lines`); table.append(caption);
        const body = node('tbody'); table.append(body); container.append(table);
        const columns = node('colgroup');
        for (const width of split ? ['27px', '42px', '', '27px', '42px', ''] : ['27px', '42px', '42px', '']) {
            const column = node('col'); if (width) column.style.width = width; columns.append(column);
        }
        table.insertBefore(columns, caption.nextSibling);
        let block = -1; let batch = node('tbody'); let processed = 0;
        for (let i = 0; i < diff.lines.length;) {
            if (generation !== state.comparisonGeneration) return;
            const line = diff.lines[i];
            if (line.block !== block) {
                block = line.block;
                const heading = node('tr', 'git-block-heading'); heading.dataset.block = String(block);
                const cell = node('td'); cell.colSpan = split ? 6 : 4; cell.append(document.createTextNode(`Change block ${block + 1} `));
                if (diff.partial) { const button = node('button', '', 'Select block'); button.type = 'button'; button.dataset.selectBlock = String(block); cell.append(button); }
                heading.append(cell); batch.append(heading);
            }
            if (split) {
                if (line.type === ' ') { const row = node('tr'); row.append(choice(null), ...lineCells(line, 'old'), choice(null), ...lineCells(line, 'new')); batch.append(row); i++; }
                else {
                    const deleted = [], added = [];
                    while (i < diff.lines.length && diff.lines[i].type !== ' ' && diff.lines[i].block === block) { const item = diff.lines[i++]; (item.type === '-' ? deleted : added).push(item); }
                    for (let j = 0; j < Math.max(deleted.length, added.length); j++) {
                        const row = node('tr'); row.append(choice(deleted[j]), ...lineCells(deleted[j], 'old'), choice(added[j]), ...lineCells(added[j], 'new')); batch.append(row);
                    }
                }
            } else {
                const row = node('tr'); const cells = lineCells(line, 'new');
                row.append(choice(line), node('td', 'git-line-number', String(line.oldNumber ?? '')), cells[0], cells[1]); batch.append(row); i++;
            }
            if (++processed % 100 === 0) { body.append(...Array.from(batch.children)); await new Promise(resolve => requestAnimationFrame(resolve)); }
        }
        body.append(...Array.from(batch.children)); updateLineSelection();
    }
    function updateLineSelection() {
        document.querySelectorAll('[data-line]').forEach(input => { input.checked = state.lines.has(input.dataset.line); });
        document.querySelectorAll('[data-line-highlight]').forEach(cell => cell.classList.toggle('git-selected-line', state.lines.has(cell.dataset.lineHighlight)));
        renderToolbar();
    }
    function selectBlock(block) {
        if (!state.diff?.partial) return;
        state.activeBlock = block;
        const lines = state.diff.lines.filter(line => line.block === block && line.type !== ' ');
        const selected = lines.every(line => state.lines.has(line.id));
        lines.forEach(line => selected ? state.lines.delete(line.id) : state.lines.add(line.id)); updateLineSelection();
    }
    async function mutate(partial) {
        const diff = state.diff; const id = state.repository; if (!diff || state.busy.has(id)) return;
        state.busy.add(id); renderToolbar();
        try {
            await request(`${repoUrl(id)}/${diff.area === 'staged' ? 'unstage' : 'stage'}`, { path: diff.path, area: diff.area, version: diff.version, lines: partial ? [...state.lines] : null, all: false });
            announce(partial ? 'Selected lines updated in the staging area.' : 'File staging updated.');
        } catch (error) { fail(error); }
        finally { state.busy.delete(id); const repo = state.repositories.find(repo => repo.id === id); if (repo) await refreshStatus(repo); if (id === state.repository) await reconcileFile(); renderToolbar(); }
    }
    const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
    async function watch(operation) {
        state.operations.set(operation.id, operation); renderOperations();
        while (operation.state === 'running') { await delay(350); operation = await request(`${base}/operations/${operation.id}`); state.operations.set(operation.id, operation); renderOperations(); }
        return operation;
    }
    function renderOperations() {
        const items = [];
        [...state.operations.values()].slice(-10).reverse().forEach(operation => operation.outcomes.forEach(outcome => {
            const repo = state.repositories.find(repo => repo.id === outcome.repositoryId);
            const item = node('li', '', `${repo?.path || (outcome.repositoryId === 'discovery' ? 'Repository discovery' : 'Repository')} · ${outcome.state}: ${outcome.message}`);
            item.dataset.state = outcome.state; items.push(item);
        }));
        $('git-operation-results').replaceChildren(...items);
        const running = [...state.operations.values()].filter(op => op.state === 'running').length;
        $('git-operation-heading').textContent = running ? `Operation results · ${running} running` : 'Operation results';
    }
    async function runAction(action, ids, path, includeUntracked = false, confirmation = null, capturedTargets = null) {
        const targets = capturedTargets || ids.map(id => ({ id, version: state.statuses.get(id)?.version }));
        if (ids.some(id => state.busy.has(id))) throw new Error('A Git operation is already running for one of these repositories. Wait for it to finish.');
        ids.forEach(id => state.busy.add(id)); renderToolbar(); $('git-operation-details').open = true;
        try {
            const result = await watch(await request(`${base}/actions`, { action, targets, path: path || null, includeUntracked, confirmation }));
            const failed = result.outcomes.filter(outcome => outcome.state !== 'success');
            announce(failed.length ? `${action} finished with ${failed.length} unsuccessful repositories. Review Operation results.` : `${action} completed for ${ids.length} ${ids.length === 1 ? 'repository' : 'repositories'}.`);
        } finally { ids.forEach(id => state.busy.delete(id)); await refreshStatuses(); renderToolbar(); }
    }
    function actionDialog(action, ids, path = null) {
        if (!ids.length) return ConfirmationModal.info({ title: 'No repositories selected', message: 'Select at least one repository to run this action.' });
        document.querySelectorAll('details[open]').forEach(details => { if (details.id !== 'git-operation-details') details.open = false; });
        if (!['stash', 'discard'].includes(action)) return guard(() => runAction(action, ids, path));
        // Capture the confirmed snapshots before opening the dialog; refresh cannot broaden them.
        const targets = ids.map(id => ({ id, version: state.statuses.get(id)?.version }));
        const names = ids.map(id => state.repositories.find(repo => repo.id === id)?.path || id).join(', ');
        ConfirmationModal.show({
            title: action === 'stash' ? 'Stash repository changes?' : path ? 'Discard file changes?' : 'Discard repository changes?',
            message: action === 'stash' ? `Stash changes in ${names}?` : `Discard unstaged changes to ${path || 'tracked files'} in ${names}? Staged changes and untracked files are kept. This cannot be undone.`,
            variant: action === 'discard' ? 'danger' : 'confirm',
            requireTyped: action === 'discard' && !path ? workspaceName : null,
            checkbox: action === 'stash' ? { label: 'Include untracked files', checked: false } : null,
            onConfirm: checked => guard(() => runAction(action, ids, path, checked === true, action === 'discard' && !path ? workspaceName : 'confirmed', targets))
        });
    }
    $('git-refresh').addEventListener('click', () => guard(discover));
    $('git-push-selected').addEventListener('click', () => actionDialog('push', [...state.selected]));
    $('git-commit-mode').addEventListener('change', () => setCommitMode($('git-commit-mode').value));
    $('git-search').addEventListener('input', renderSidebar); $('git-changed-only').addEventListener('change', renderSidebar);
    $('git-select-all').addEventListener('change', event => { state.repositories.filter(visible).forEach(repo => event.target.checked ? state.selected.add(repo.id) : state.selected.delete(repo.id)); renderSidebar(); });
    document.querySelectorAll('[data-action]').forEach(button => button.addEventListener('click', () => actionDialog(button.dataset.action, state.repository ? [state.repository] : [])));
    document.querySelectorAll('[data-bulk-action]').forEach(button => button.addEventListener('click', () => actionDialog(button.dataset.bulkAction, [...state.selected])));
    $('git-stage-file').addEventListener('click', () => guard(() => mutate(false))); $('git-stage-lines').addEventListener('click', () => guard(() => mutate(true)));
    $('git-discard-file').addEventListener('click', () => actionDialog('discard', [state.repository], state.diff?.path));
    $('git-diff-mode').addEventListener('change', () => guard(renderDiff));
    $('git-diff').addEventListener('change', event => { if (event.target.dataset.line) { event.target.checked ? state.lines.add(event.target.dataset.line) : state.lines.delete(event.target.dataset.line); updateLineSelection(); } });
    $('git-diff').addEventListener('click', event => { const button = event.target.closest('[data-select-block]'); if (button) selectBlock(Number(button.dataset.selectBlock)); });
    $('git-diff').addEventListener('keydown', event => {
        if (!event.target.dataset.line || !['ArrowUp', 'ArrowDown'].includes(event.key)) return;
        const inputs = [...$('git-diff').querySelectorAll('[data-line]')]; const index = inputs.indexOf(event.target); const next = inputs[index + (event.key === 'ArrowDown' ? 1 : -1)];
        if (next) { event.preventDefault(); next.focus(); }
    });
    $('git-select-block').addEventListener('click', () => selectBlock(state.activeBlock));
    function moveBlock(direction) {
        const blocks = [...new Set(state.diff?.lines.map(line => line.block) || [])]; if (!blocks.length) return;
        state.activeBlock = blocks[(Math.max(0, blocks.indexOf(state.activeBlock)) + direction + blocks.length) % blocks.length];
        $('git-diff').querySelector(`[data-block="${state.activeBlock}"]`)?.scrollIntoView({ block: 'nearest' });
    }
    $('git-prev-block').addEventListener('click', () => moveBlock(-1)); $('git-next-block').addEventListener('click', () => moveBlock(1));
    $('git-back').addEventListener('click', () => { document.body.classList.remove('git-show-diff'); state.views.get(state.repository)?.button.focus(); });
    $('git-commit-message').addEventListener('input', () => { state.drafts.set(draftKey(), $('git-commit-message').value); renderToolbar(); });
    $('git-commit-form').addEventListener('submit', event => { event.preventDefault(); guard(async () => {
        const id = state.repository; const current = status(); const draft = $('git-commit-message').value;
        const key = draftKey(); const mode = state.commitMode;
        const targets = mode === 'staged' ? [{ id }] : commitTargets(); const ids = targets.map(target => target.id);
        if (!draft.trim() || !ids.length || ids.some(id => !id || state.busy.has(id))) return;
        ids.forEach(id => state.busy.add(id)); renderToolbar(); $('git-operation-details').open = true;
        try {
            const operation = mode === 'staged'
                ? await request(`${repoUrl(id)}/commit`, { message: draft, version: current.stagedVersion })
                : await request(`${base}/commits`, { message: draft, targets });
            const result = await watch(operation);
            const successful = result.outcomes.filter(outcome => outcome.state === 'success');
            if (mode !== 'staged') for (const outcome of successful) {
                const submitted = targets.find(target => target.id === outcome.repositoryId);
                for (const file of submitted.files) if (state.files.get(outcome.repositoryId)?.get(file.path) === file) state.files.get(outcome.repositoryId).delete(file.path);
            }
            if (result.outcomes.every(outcome => outcome.state === 'success')) {
                if (state.drafts.get(key) === draft) { state.drafts.set(key, ''); if (draftKey() === key) $('git-commit-message').value = ''; }
                announce(`${successful.length} ${successful.length === 1 ? 'commit created' : 'commits created'}. Unselected changes remain pending; push when ready.`);
            } else announce(`${successful.length} commits created; other targets failed or have an uncertain outcome. Review Operation results before trying again.`);
        } finally { ids.forEach(id => state.busy.delete(id)); await refreshStatuses(); renderToolbar(); }
    }); });
    document.querySelectorAll('a[href]').forEach(link => link.addEventListener('click', event => {
        if (![...state.drafts.values()].some(draft => draft.trim()) || event.ctrlKey || event.metaKey) return;
        event.preventDefault(); ConfirmationModal.show({ title: 'Leave commit drafts?', message: 'Commit messages on this page have not been submitted. Leave and discard these drafts?', onConfirm: () => { window.location.href = link.href; } });
    }));
    let refreshTimer;
    function returned() {
        if (document.hidden || $('git-refresh').disabled) return;
        clearTimeout(refreshTimer); refreshTimer = setTimeout(() => guard(refreshStatuses), 150);
    }
    window.addEventListener('focus', returned); document.addEventListener('visibilitychange', returned);
    if (matchMedia('(max-width:700px)').matches) $('git-diff-mode').value = 'unified';
    window.WorkspaceGit = { state, discover, refreshStatuses, chooseRepository, chooseFile, mutate, actionDialog, renderDiff, setCommitMode, commitTargets };
    guard(discover);
})();
