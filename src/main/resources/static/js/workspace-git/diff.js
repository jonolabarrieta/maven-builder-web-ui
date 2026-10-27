/* Workspace Git page: diff. Scripts share window.WorkspaceGitApp and load in template order. */
(App => {
    'use strict';
    const { $, state, announce, node, repoUrl, staged, entryKey, repository, status, entries } = App;
    const { fail, guard, request } = App;
    async function reconcileFile() {
        // A comparison opened from history stays until the user picks a working-tree file.
        if (state.file?.area === 'commit') return;
        const available = entries(status());
        const chosen = available.find(file => state.file && entryKey(file) === entryKey(state.file)) || available[0];
        if (chosen) await chooseFile(chosen, false);
        else {
            ++state.comparisonGeneration; cancelComparison(); setLoading(false); state.file = null; state.diff = null; state.lines.clear();
            $('git-file-name').textContent = 'No pending files'; $('git-comparison-label').textContent = 'The working tree and staging area are clean.';
            $('git-diff').replaceChildren(node('p', 'git-empty', status()?.error || 'No pending changes in this repository.'));
            App.renderRepositories([state.repository]);
        }
    }
    let loadingTimer;
    /** Dims the outdated comparison at once and shows the spinner only when loading is noticeably slow. */
    function setLoading(loading) {
        clearTimeout(loadingTimer);
        $('git-diff').classList.toggle('is-loading', loading); $('git-diff').setAttribute('aria-busy', String(loading));
        if (!loading) $('git-diff-loading').hidden = true;
        else loadingTimer = setTimeout(() => { $('git-diff-loading').hidden = false; }, 150);
    }
    function cancelComparison() { state.comparison?.abort(); state.comparison = null; }
    function comparisonError(message, retry) {
        const panel = node('div', 'git-diff-error'); panel.setAttribute('role', 'alert');
        const button = node('button', '', 'Retry'); button.type = 'button'; button.addEventListener('click', () => guard(retry));
        panel.append(node('p', '', message), button); $('git-diff').replaceChildren(panel); announce(message);
    }
    async function chooseFile(file, reveal = true) {
        const generation = ++state.comparisonGeneration; const id = state.repository;
        // Only the latest selection may load; superseded requests are abandoned at the transport.
        cancelComparison(); const controller = new AbortController(); state.comparison = controller;
        const same = state.file && entryKey(state.file) === entryKey(file); const scroll = same ? $('git-diff').scrollTop : 0;
        const previousVersion = state.diff?.version; const selected = new Set(state.lines);
        state.file = { path: file.path, area: file.area }; state.diff = null; state.lines.clear(); App.renderRepositories([id]);
        if (reveal) document.body.classList.add('git-show-diff');
        $('git-file-name').textContent = file.path;
        $('git-comparison-label').textContent = file.area === 'staged' ? 'Last commit → Staging area' : 'Staging area → Working tree';
        setLoading(true); App.renderToolbar();
        try {
            const response = await request(`${repoUrl(id)}/diff?path=${encodeURIComponent(file.path)}&area=${file.area}`, undefined, { signal: controller.signal });
            if (generation !== state.comparisonGeneration || id !== state.repository) return;
            state.diff = response; if (!same) state.activeBlock = response.lines[0]?.block || 0;
            if (same && previousVersion === response.version) state.lines = selected;
            await renderDiff(); if (generation === state.comparisonGeneration) $('git-diff').scrollTop = scroll;
        } catch (error) {
            if (generation !== state.comparisonGeneration || error.name === 'AbortError') return;
            comparisonError(error.message || 'The comparison could not be loaded.', () => chooseFile(file, false));
        } finally { if (generation === state.comparisonGeneration) { state.comparison = null; setLoading(false); App.renderToolbar(); } }
    }
    function choice(line) {
        const cell = node('td', 'git-line-choice');
        if (!line || line.type === ' ' || !state.diff.partial) return cell;
        const checkbox = node('input'); checkbox.type = 'checkbox'; checkbox.dataset.line = line.id; checkbox.checked = state.lines.has(line.id);
        checkbox.setAttribute('aria-label', `Select ${line.type === '+' ? 'added' : 'deleted'} line ${line.newNumber ?? line.oldNumber}`);
        cell.append(checkbox); return cell;
    }
    let rendered = null;
    function lineCells(line, side) {
        const number = node('td', 'git-line-number', line ? String(side === 'old' ? line.oldNumber ?? '' : line.newNumber ?? '') : '');
        const text = node('td', 'git-line-text');
        // Highlighted HTML is produced by highlight.js from escaped text; anything else is inserted as plain text.
        const html = line && rendered?.html.get(`${line.id}:${line.type === '-' ? 'old' : line.type === '+' ? 'new' : side}`);
        if (html !== undefined && html !== null) text.innerHTML = html; else text.textContent = line?.text || '';
        if (line && line.type !== ' ') { const style = line.type === '+' ? 'git-added' : 'git-deleted'; number.classList.add(style); text.classList.add(style); text.dataset.lineHighlight = line.id; }
        if (line && !line.newline) text.append(node('span', 'git-no-newline', 'No newline at end of file'));
        return [number, text];
    }
    // Unchanged lines hidden between change blocks can be revealed per gap or for the whole file. The full file is
    // fetched once per comparison version; its changed lines carry the same identities as the comparison.
    const contextKey = diff => `${state.repository}\0${diff.path}\0${diff.area}\0${diff.version}`;
    function contextFor(diff) {
        if (state.context?.key !== contextKey(diff)) state.context = { key: contextKey(diff), full: null, gaps: new Set() };
        return state.context;
    }
    async function loadFullContext() {
        const diff = state.diff; const context = contextFor(diff);
        if (context.full) return true;
        const generation = state.comparisonGeneration;
        const response = await request(`${repoUrl(state.repository)}/diff?path=${encodeURIComponent(diff.path)}&area=${diff.area}&context=all`);
        if (generation !== state.comparisonGeneration) return false;
        if (response.version !== diff.version) {
            comparisonError('This file changed after the comparison was loaded. Refresh the comparison before revealing more lines.', () => chooseFile(state.file, false));
            return false;
        }
        context.full = response.lines; return true;
    }
    /** Comparison lines plus revealed unchanged lines; collapsed runs become gap items keyed by their first old line. */
    function displayItems(diff) {
        const context = contextFor(diff); const items = [];
        if (diff.area === 'commit') return diff.lines.map(line => ({ line }));
        if (!context.full) {
            let lastOld = 0;
            diff.lines.forEach((line, index) => {
                if (index === 0 || line.block !== diff.lines[index - 1].block) {
                    const first = diff.lines.slice(index).find(item => item.block === line.block && item.oldNumber)?.oldNumber;
                    if (first && first > lastOld + 1) items.push({ gap: lastOld + 1, count: first - lastOld - 1 });
                }
                items.push({ line }); if (line.oldNumber) lastOld = line.oldNumber;
            });
            if (diff.lines.at(-1)?.newline !== false) items.push({ gap: lastOld + 1, count: null });
            return items;
        }
        const shown = new Set(diff.lines.filter(line => line.type === ' ').map(line => line.oldNumber));
        let block = context.full.find(line => line.type !== ' ')?.block ?? 0; let hidden = [];
        const flush = () => {
            if (!hidden.length) return;
            if (state.showFullFile || context.gaps.has(hidden[0].oldNumber)) hidden.forEach(line => items.push({ line: { ...line, block } }));
            else items.push({ gap: hidden[0].oldNumber, count: hidden.length });
            hidden = [];
        };
        for (const line of context.full) {
            if (line.type !== ' ') { flush(); block = line.block; items.push({ line }); }
            else if (shown.has(line.oldNumber)) { flush(); items.push({ line: { ...line, block } }); }
            else hidden.push(line);
        }
        flush();
        return items;
    }
    async function revealGap(key) {
        if (!state.diff || !(await loadFullContext())) return;
        contextFor(state.diff).gaps.add(key); const scroll = $('git-diff').scrollTop;
        await renderDiff(); $('git-diff').scrollTop = scroll;
    }
    async function toggleFullFile() {
        state.showFullFile = !state.showFullFile; $('git-full-file').setAttribute('aria-pressed', String(state.showFullFile));
        if (state.diff) { const scroll = $('git-diff').scrollTop; await renderDiff(); $('git-diff').scrollTop = scroll; }
    }
    function gapRow(item, columns) {
        const row = node('tr', 'git-gap'); const cell = node('td'); cell.colSpan = columns;
        const button = node('button', '', item.count === null ? 'Show remaining lines' : `Show ${item.count} hidden ${item.count === 1 ? 'line' : 'lines'}`);
        button.type = 'button'; button.dataset.gap = String(item.gap); cell.append(button); row.append(cell); return row;
    }
    async function renderDiff() {
        const diff = state.diff; if (!diff) return;
        const generation = state.comparisonGeneration;
        if (state.showFullFile && diff.area !== 'commit' && diff.lines.length && !(await loadFullContext())) return;
        if (generation !== state.comparisonGeneration) return;
        const container = $('git-diff'); container.replaceChildren();
        if (diff.reason) container.append(node('p', 'git-empty', diff.reason));
        if (!diff.lines.length) return;
        const items = displayItems(diff); const lines = items.filter(item => item.line).map(item => item.line);
        rendered = App.renderLines({ path: diff.path, lines }, { single: state.showFullFile });
        if (!rendered) container.append(node('p', 'git-notice', `Syntax highlighting is off for comparisons over ${state.highlightMaxLines} lines.`));
        const split = $('git-diff-mode').value === 'split';
        const comparison = diff.area === 'commit' ? ['Parent commit', 'Commit'] : diff.area === 'staged' ? ['Last commit', 'Staging area'] : ['Staging area', 'Working tree'];
        const table = node('table'); const caption = node('caption', '', split ? `${comparison[0]} (left) · ${comparison[1]} (right)` : `${comparison[0]} → ${comparison[1]} · Removed and added lines`); table.append(caption);
        const body = node('tbody'); table.append(body); container.append(table);
        const columns = node('colgroup');
        // In split mode the layout script sizes the old side's text column from the adjustable ratio.
        for (const width of split ? ['27px', '42px', '', '27px', '42px', ''] : ['27px', '42px', '42px', '']) {
            const column = node('col'); if (width) column.style.width = width; columns.append(column);
        }
        table.insertBefore(columns, caption.nextSibling);
        let block = -1; let batch = node('tbody'); let processed = 0;
        for (let i = 0; i < items.length;) {
            if (generation !== state.comparisonGeneration) return;
            if (items[i].gap !== undefined) { batch.append(gapRow(items[i++], split ? 6 : 4)); continue; }
            const line = items[i].line;
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
                    while (i < items.length && items[i].line && items[i].line.type !== ' ' && items[i].line.block === block) { const item = items[i++].line; (item.type === '-' ? deleted : added).push(item); }
                    for (let j = 0; j < Math.max(deleted.length, added.length); j++) {
                        const row = node('tr'); row.append(choice(deleted[j]), ...lineCells(deleted[j], 'old'), choice(added[j]), ...lineCells(added[j], 'new')); batch.append(row);
                    }
                }
            } else {
                const row = node('tr'); const cells = lineCells(line, 'new');
                row.append(choice(line), node('td', 'git-line-number', String(line.oldNumber ?? '')), cells[0], cells[1]); batch.append(row); i++;
            }
            if (++processed % 100 === 0) { body.append(...Array.from(batch.children)); await App.nextFrame(); }
        }
        body.append(...Array.from(batch.children)); updateLineSelection();
    }
    function updateLineSelection() {
        document.querySelectorAll('[data-line]').forEach(input => { input.checked = state.lines.has(input.dataset.line); });
        document.querySelectorAll('[data-line-highlight]').forEach(cell => cell.classList.toggle('git-selected-line', state.lines.has(cell.dataset.lineHighlight)));
        App.renderToolbar();
    }
    function selectBlock(block) {
        if (!state.diff?.partial) return;
        state.activeBlock = block;
        const lines = state.diff.lines.filter(line => line.block === block && line.type !== ' ');
        const selected = lines.every(line => state.lines.has(line.id));
        lines.forEach(line => selected ? state.lines.delete(line.id) : state.lines.add(line.id)); updateLineSelection();
    }
    async function mutate(partial) {
        const diff = state.diff; const id = state.repository; if (!diff || diff.area === 'commit' || state.busy.has(id)) return;
        state.busy.add(id); App.renderToolbar();
        try {
            await request(`${repoUrl(id)}/${diff.area === 'staged' ? 'unstage' : 'stage'}`, { path: diff.path, area: diff.area, version: diff.version, lines: partial ? [...state.lines] : null, all: false });
            announce(partial ? 'Selected lines updated in the staging area.' : 'File staging updated.');
        } catch (error) { fail(error); }
        finally { state.busy.delete(id); const repo = state.repositories.find(repo => repo.id === id); if (repo) await App.refreshStatus(repo); if (id === state.repository) await reconcileFile(); App.renderToolbar(); }
    }
    function moveBlock(direction) {
        const blocks = [...new Set(state.diff?.lines.map(line => line.block) || [])]; if (!blocks.length) return;
        state.activeBlock = blocks[(Math.max(0, blocks.indexOf(state.activeBlock)) + direction + blocks.length) % blocks.length];
        $('git-diff').querySelector(`[data-block="${state.activeBlock}"]`)?.scrollIntoView({ block: 'nearest' });
    }
    Object.assign(App, { revealGap, toggleFullFile, cancelComparison, reconcileFile, chooseFile, choice, lineCells, renderDiff, updateLineSelection, selectBlock, mutate, moveBlock });
})(window.WorkspaceGitApp = window.WorkspaceGitApp || {});
