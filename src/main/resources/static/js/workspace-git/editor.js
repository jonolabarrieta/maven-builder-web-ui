/* Workspace Git page: edit a changed file's working-tree text before committing. CodeMirror loads on first use. */
(App => {
    'use strict';
    const { $, state, repoUrl, announce } = App;
    const { request, guard } = App;
    const MODES = {
        java: 'text/x-java', kotlin: 'text/x-kotlin', csharp: 'text/x-csharp', c: 'text/x-csrc', cpp: 'text/x-c++src', json: 'application/json',
        xml: 'xml', yaml: 'yaml', properties: 'properties', ini: 'properties', sql: 'text/x-sql', javascript: 'javascript', typescript: 'text/typescript',
        css: 'css', scss: 'text/x-scss', less: 'text/x-less', bash: 'shell', markdown: 'markdown', python: 'python', go: 'go'
    };
    let loading = null;
    /** Loads the editor library and modes listed in the page, in order, once. */
    function loadLibrary() {
        if (window.CodeMirror) return Promise.resolve();
        const assets = JSON.parse($('git-editor-assets').textContent);
        loading ||= assets.reduce((chain, url) => chain.then(() => new Promise((resolve, reject) => {
            if (url.endsWith('.css')) { const link = document.createElement('link'); link.rel = 'stylesheet'; link.href = url; document.head.append(link); return resolve(); }
            const script = document.createElement('script'); script.src = url; script.onload = resolve;
            script.onerror = () => reject(new Error('The editor could not be loaded. Reload the page and try again.')); document.head.append(script);
        })), Promise.resolve()).catch(error => { loading = null; throw error; });
        return loading;
    }
    const mode = path => /\.html?$/i.test(path) ? 'htmlmixed' : MODES[App.languageFor(path)] || null;
    const dirty = () => Boolean(state.editor && state.editor.cm.getValue() !== state.editor.saved);
    function setStatus(text) { $('git-editor-state').textContent = text; }
    function show(open) {
        $('git-editor').hidden = !open; $('git-diff').hidden = open;
        document.querySelector('.git-selection-toolbar').classList.toggle('is-editing', open);
    }
    async function openEditor() {
        const file = state.file; const id = state.repository;
        if (!file || file.area !== 'changes' || !id) return;
        const content = await request(`${repoUrl(id)}/file?path=${encodeURIComponent(file.path)}`);
        if (!content.editable) return ConfirmationModal.info({ title: 'This file cannot be edited here', message: content.reason });
        await loadLibrary();
        if (state.repository !== id || state.file?.path !== file.path) return;
        $('git-editor-host').replaceChildren();
        const cm = CodeMirror($('git-editor-host'), {
            value: content.content, mode: mode(file.path), lineNumbers: true, matchBrackets: true, styleActiveLine: true, indentUnit: 4,
            extraKeys: { 'Ctrl-S': () => guard(save), 'Cmd-S': () => guard(save), Esc: () => guard(cancel) }
        });
        state.editor = { repository: id, path: file.path, hash: content.hash, saved: content.content, cm };
        cm.on('change', () => setStatus(dirty() ? 'Unsaved changes' : 'No changes'));
        $('git-editor-name').textContent = file.path; setStatus(`Line endings ${content.eol.toUpperCase()}${content.bom ? ' · BOM' : ''} · saving keeps them`);
        show(true); cm.refresh(); cm.focus(); App.renderToolbar();
    }
    function close() {
        state.editor = null; $('git-editor-host').replaceChildren(); show(false); App.renderToolbar();
    }
    async function save() {
        const editor = state.editor; if (!editor) return;
        $('git-editor-save').disabled = true;
        try {
            const saved = await request(`${repoUrl(editor.repository)}/file`, { path: editor.path, content: editor.cm.getValue(), hash: editor.hash }, { method: 'PUT' });
            close(); announce(`${saved.path} saved. The comparison shows the new working-tree content; nothing was staged.`);
            const repo = state.repositories.find(item => item.id === editor.repository);
            if (repo) await App.refreshStatus(repo); App.flushRender();
            if (state.repository === editor.repository) await App.chooseFile({ path: editor.path, area: 'changes' }, false);
        } catch (error) {
            if (error.status !== 409) throw error;
            setStatus('Changed on disk; your edits are still here');
            ConfirmationModal.show({
                title: 'File changed on disk', variant: 'confirm', confirmLabel: 'Reload file', cancelLabel: 'Keep my edits',
                message: `${editor.path} was modified after you opened it, so it was not saved. Copy your edits first if you need them, then reload to see the current content.`,
                onConfirm: () => guard(async () => { close(); await openEditorFor(editor.path); })
            });
        } finally { $('git-editor-save').disabled = false; }
    }
    async function openEditorFor(path) { if (state.file?.path === path) await openEditor(); }
    async function cancel() { if (await leaveEditor()) announce('Editing cancelled; the file was not changed.'); }
    /** Resolves true when there is no editor or the user agrees to discard unsaved edits; the editor is then closed. */
    function leaveEditor() {
        if (!state.editor) return Promise.resolve(true);
        if (!dirty()) { close(); return Promise.resolve(true); }
        return new Promise(resolve => {
            let decided = false;
            ConfirmationModal.show({
                title: 'Discard unsaved edits?', variant: 'danger', confirmLabel: 'Discard edits', cancelLabel: 'Keep editing',
                message: `${state.editor.path} has edits that are not saved. Discard them?`,
                onConfirm: () => { decided = true; close(); resolve(true); },
                onClose: () => { if (!decided) resolve(false); }
            });
        });
    }
    async function copyEdits() {
        if (!state.editor) return;
        await navigator.clipboard.writeText(state.editor.cm.getValue()); setStatus('Edits copied to the clipboard');
    }
    function initEditor() {
        $('git-edit-file').addEventListener('click', () => guard(openEditor));
        $('git-editor-save').addEventListener('click', () => guard(save));
        $('git-editor-cancel').addEventListener('click', () => guard(cancel));
        $('git-editor-copy').addEventListener('click', () => guard(copyEdits));
        window.addEventListener('beforeunload', event => { if (dirty()) { event.preventDefault(); event.returnValue = ''; } });
    }
    Object.assign(App, { initEditor, openEditor, leaveEditor, editorDirty: dirty });
})(window.WorkspaceGitApp = window.WorkspaceGitApp || {});
