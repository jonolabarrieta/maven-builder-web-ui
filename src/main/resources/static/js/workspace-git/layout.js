/* Workspace Git page: resizable panes. Sizes persist in localStorage and never apply to the narrow stacked layout. */
(App => {
    'use strict';
    const { $, state } = App;
    const KEY = 'mvnbuilder.git.layout';
    const DEFAULTS = { sidebar: 300, commit: null, split: 0.5, hidden: false };
    // Fixed table columns before each side's text in split mode: selection box + line number.
    const SPLIT_GUTTER = 69;
    let layout = { ...DEFAULTS };
    const narrow = () => matchMedia('(max-width:700px)').matches;
    const clamp = (value, min, max) => Math.min(Math.max(value, min), Math.max(min, max));
    const limits = {
        sidebar: () => [200, Math.max(260, $('git-layout').clientWidth - 480)],
        commit: () => [90, Math.max(120, document.querySelector('.git-workbench').clientHeight - 260)],
        split: () => [0.2, 0.8]
    };
    function load() {
        try { return { ...DEFAULTS, ...JSON.parse(localStorage.getItem(KEY) || '{}') }; } catch (_) { return { ...DEFAULTS }; }
    }
    function save() { try { localStorage.setItem(KEY, JSON.stringify(layout)); } catch (_) { /* Private mode keeps the session layout only. */ } }
    function apply() {
        const root = $('git-layout');
        root.style.setProperty('--git-sidebar-width', `${Math.round(clamp(layout.sidebar, ...limits.sidebar()))}px`);
        root.classList.toggle('is-sidebar-hidden', layout.hidden);
        const area = $('git-commit-area');
        area.classList.toggle('is-sized', layout.commit !== null);
        area.style.height = layout.commit === null ? '' : `${Math.round(clamp(layout.commit, ...limits.commit()))}px`;
        const toggle = $('git-sidebar-toggle'); const label = layout.hidden ? 'Show repository list' : 'Hide repository list';
        toggle.setAttribute('aria-expanded', String(!layout.hidden)); toggle.title = `${label} (Ctrl+B)`; toggle.querySelector('.sr-only').textContent = label;
        describe(); placeSplitHandle();
    }
    function describe() {
        const sidebar = $('git-sidebar-resizer'), commit = $('git-commit-resizer'), split = $('git-split-resizer');
        const [sidebarMin, sidebarMax] = limits.sidebar(); const width = Math.round(clamp(layout.sidebar, sidebarMin, sidebarMax));
        Object.entries({ 'aria-valuemin': sidebarMin, 'aria-valuemax': sidebarMax, 'aria-valuenow': width, 'aria-valuetext': `Repository list ${width} pixels wide` })
            .forEach(([name, value]) => sidebar.setAttribute(name, String(value)));
        const height = Math.round($('git-commit-area').getBoundingClientRect().height);
        commit.setAttribute('aria-valuenow', String(height)); commit.setAttribute('aria-valuetext', `Commit area ${height} pixels high`);
        const percent = Math.round(clamp(layout.split, ...limits.split()) * 100);
        Object.entries({ 'aria-valuemin': 20, 'aria-valuemax': 80, 'aria-valuenow': percent, 'aria-valuetext': `Left side ${percent} percent` })
            .forEach(([name, value]) => split.setAttribute(name, String(value)));
    }
    /** The split handle sits on the boundary between the old and new text columns of the comparison table. */
    function placeSplitHandle() {
        const handle = $('git-split-resizer'); const diff = $('git-diff');
        const visible = !narrow() && $('git-diff-mode').value === 'split' && Boolean(diff.querySelector('table'));
        handle.hidden = !visible;
        if (!visible) return;
        const text = diff.clientWidth - 2 * SPLIT_GUTTER; const left = Math.round(text * clamp(layout.split, ...limits.split()));
        const column = diff.querySelector('table colgroup col:nth-child(3)');
        if (column) column.style.width = `${left}px`;
        handle.style.left = `${diff.offsetLeft + SPLIT_GUTTER + left}px`;
    }
    function set(name, value, persist) {
        layout = { ...layout, [name]: value === null ? null : clamp(value, ...limits[name]()) };
        apply(); if (persist) save();
    }
    /** Pointer dragging plus arrow keys (Shift for larger steps); every change is clamped and persisted. */
    function bind(handle, name, { fromPointer, step, keys, current }) {
        handle.addEventListener('pointerdown', event => {
            if (event.button !== 0 || narrow()) return;
            event.preventDefault(); try { handle.setPointerCapture(event.pointerId); } catch (_) { /* Synthetic pointers cannot be captured. */ } handle.classList.add('is-dragging');
            const move = moveEvent => set(name, fromPointer(moveEvent), false);
            const end = () => { handle.classList.remove('is-dragging'); handle.removeEventListener('pointermove', move); save(); };
            handle.addEventListener('pointermove', move); handle.addEventListener('pointerup', end, { once: true }); handle.addEventListener('pointercancel', end, { once: true });
        });
        handle.addEventListener('keydown', event => {
            const direction = keys[event.key]; if (!direction) return;
            event.preventDefault(); set(name, current() + direction * step * (event.shiftKey ? 4 : 1), true);
        });
    }
    function toggleSidebar() {
        layout = { ...layout, hidden: !layout.hidden }; apply(); save();
        (layout.hidden ? $('git-sidebar-toggle') : $('git-sidebar-resizer')).focus();
    }
    function restoreLayout() { layout = load(); apply(); }
    function initLayout() {
        layout = load();
        bind($('git-sidebar-resizer'), 'sidebar', {
            fromPointer: event => event.clientX - $('git-layout').getBoundingClientRect().left, step: 16,
            keys: { ArrowLeft: -1, ArrowRight: 1 }, current: () => clamp(layout.sidebar, ...limits.sidebar())
        });
        bind($('git-commit-resizer'), 'commit', {
            fromPointer: event => document.querySelector('.git-workbench').getBoundingClientRect().bottom - event.clientY, step: 16,
            keys: { ArrowUp: 1, ArrowDown: -1 }, current: () => $('git-commit-area').getBoundingClientRect().height
        });
        bind($('git-split-resizer'), 'split', {
            fromPointer: event => (event.clientX - $('git-diff').getBoundingClientRect().left - SPLIT_GUTTER) / Math.max(1, $('git-diff').clientWidth - 2 * SPLIT_GUTTER), step: 0.02,
            keys: { ArrowLeft: -1, ArrowRight: 1 }, current: () => clamp(layout.split, ...limits.split())
        });
        $('git-sidebar-toggle').addEventListener('click', toggleSidebar);
        document.addEventListener('keydown', event => {
            if ((event.ctrlKey || event.metaKey) && !event.altKey && !event.shiftKey && event.key.toLowerCase() === 'b' && !narrow()) { event.preventDefault(); toggleSidebar(); }
        });
        new ResizeObserver(() => { describe(); placeSplitHandle(); }).observe($('git-diff'));
        new MutationObserver(placeSplitHandle).observe($('git-diff'), { childList: true });
        $('git-diff-mode').addEventListener('change', placeSplitHandle);
        window.addEventListener('resize', apply);
        apply();
    }
    Object.assign(App, { initLayout, restoreLayout, toggleSidebar, placeSplitHandle, layoutState: () => ({ ...layout }) });
})(window.WorkspaceGitApp = window.WorkspaceGitApp || {});
