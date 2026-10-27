/* Workspace Git page: syntax highlighting and word-level change marks for comparisons. */
(App => {
    'use strict';
    const { state } = App;
    const EXTENSIONS = {
        java: 'java', kt: 'kotlin', kts: 'kotlin', json: 'json', xml: 'xml', xsd: 'xml', xsl: 'xml', wsdl: 'xml', svg: 'xml', html: 'xml', htm: 'xml', xhtml: 'xml',
        yml: 'yaml', yaml: 'yaml', properties: 'properties', sql: 'sql', js: 'javascript', mjs: 'javascript', cjs: 'javascript', jsx: 'javascript',
        ts: 'typescript', tsx: 'typescript', css: 'css', scss: 'scss', less: 'less', sh: 'bash', bash: 'bash', zsh: 'bash', md: 'markdown', markdown: 'markdown',
        py: 'python', go: 'go', rs: 'rust', ini: 'ini', toml: 'ini', cfg: 'ini', gradle: 'kotlin', groovy: 'java', cs: 'csharp', c: 'c', h: 'c', cpp: 'cpp', php: 'php'
    };
    const NAMES = { dockerfile: 'dockerfile', makefile: 'makefile', mvnw: 'bash', gradlew: 'bash', '.gitignore': 'bash', '.env': 'bash' };
    const MAX_TOKENS = 500;
    /** The highlight.js language for a path, or null to render plain text. */
    function languageFor(path) {
        if (typeof hljs === 'undefined') return null;
        const name = path.split('/').pop().toLowerCase();
        const language = NAMES[name] || (name.startsWith('dockerfile') ? 'dockerfile' : EXTENSIONS[name.includes('.') ? name.split('.').pop() : '']);
        return language && hljs.getLanguage(language) ? language : null;
    }
    const escapeHtml = text => text.replace(/[&<>"']/g, character => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#x27;' })[character]);
    /** Splits highlighted HTML at newlines, closing open spans at each line end and reopening them on the next line. */
    function splitHtml(html) {
        const lines = []; const open = []; let current = '';
        for (const [token] of html.matchAll(/<span[^>]*>|<\/span>|\n|[^<\n]+/g)) {
            if (token === '\n') { lines.push(current + '</span>'.repeat(open.length)); current = open.join(''); }
            else if (token === '</span>') { open.pop(); current += token; }
            else if (token.startsWith('<span')) { open.push(token); current += token; }
            else current += token;
        }
        lines.push(current + '</span>'.repeat(open.length));
        return lines;
    }
    function highlight(texts, language) {
        if (!language) return texts.map(escapeHtml);
        try {
            const lines = splitHtml(hljs.highlight(texts.join('\n'), { language, ignoreIllegals: true }).value);
            return lines.length === texts.length ? lines : texts.map(escapeHtml);
        } catch (_) { return texts.map(escapeHtml); }
    }
    const tokens = text => text.match(/\w+|\s+|[^\w\s]/g) || [];
    /** Character ranges of tokens outside the longest common token subsequence of two lines. */
    function changedRanges(before, after) {
        const a = tokens(before), b = tokens(after);
        if (!a.length || !b.length || a.length > MAX_TOKENS || b.length > MAX_TOKENS) return null;
        const table = Array.from({ length: a.length + 1 }, () => new Uint16Array(b.length + 1));
        for (let i = a.length - 1; i >= 0; i--) for (let j = b.length - 1; j >= 0; j--)
            table[i][j] = a[i] === b[j] ? table[i + 1][j + 1] + 1 : Math.max(table[i + 1][j], table[i][j + 1]);
        const keepA = new Set(), keepB = new Set();
        for (let i = 0, j = 0; i < a.length && j < b.length;) {
            if (a[i] === b[j]) { keepA.add(i++); keepB.add(j++); }
            else if (table[i + 1][j] >= table[i][j + 1]) i++; else j++;
        }
        // Lines sharing no visible token were rewritten entirely; marking every word would add nothing.
        if (![...keepA].some(index => /\S/.test(a[index]))) return null;
        const ranges = (list, keep) => {
            const text = list.join(''); const result = []; let offset = 0;
            list.forEach((token, index) => {
                const end = offset + token.length;
                if (!keep.has(index) && /\S/.test(token)) {
                    const last = result.at(-1);
                    if (last && !/\S/.test(text.slice(last[1], offset))) last[1] = end; else result.push([offset, end]);
                }
                offset = end;
            });
            return result;
        };
        const old = ranges(a, keepA), current = ranges(b, keepB);
        return old.length || current.length ? { old, new: current } : null;
    }
    /** Wraps character ranges of an HTML line in marks, splitting marks around tags so nesting stays valid. */
    function markRanges(html, ranges) {
        if (!ranges?.length) return html;
        let result = '', position = 0, marked = false;
        const inside = at => ranges.some(([start, end]) => at >= start && at < end);
        for (const [token] of html.matchAll(/<[^>]+>|&[^;]+;|[^<&]/g)) {
            if (token.startsWith('<')) { if (marked) { result += '</mark>'; marked = false; } result += token; continue; }
            const wanted = inside(position);
            if (wanted !== marked) { result += wanted ? '<mark class="git-word">' : '</mark>'; marked = wanted; }
            result += token; position++;
        }
        return result + (marked ? '</mark>' : '');
    }
    /**
     * HTML for every displayed line keyed by "id:old" or "id:new". Each block's old and new sides are highlighted as
     * whole texts so constructs spanning lines keep their colours. Returns null when highlighting does not apply.
     */
    function renderLines(diff, { single = false } = {}) {
        const language = languageFor(diff.path);
        if (diff.lines.length > state.highlightMaxLines) return null;
        const html = new Map();
        // The full-file view is one continuous text, so it is highlighted as a whole for exact colouring.
        const blocks = new Map();
        diff.lines.forEach(line => { const key = single ? 0 : line.block; if (!blocks.has(key)) blocks.set(key, []); blocks.get(key).push(line); });
        for (const lines of blocks.values()) {
            const old = lines.filter(line => line.type !== '+'), current = lines.filter(line => line.type !== '-');
            highlight(old.map(line => line.text), language).forEach((value, index) => html.set(`${old[index].id}:old`, value));
            highlight(current.map(line => line.text), language).forEach((value, index) => html.set(`${current[index].id}:new`, value));
            // Pair the n-th deleted and n-th added line of each run, as the side-by-side view does.
            for (let i = 0; i < lines.length;) {
                if (lines[i].type === ' ') { i++; continue; }
                const deleted = [], added = [];
                while (i < lines.length && lines[i].type !== ' ') (lines[i].type === '-' ? deleted : added).push(lines[i++]);
                for (let k = 0; k < Math.min(deleted.length, added.length); k++) {
                    const ranges = changedRanges(deleted[k].text, added[k].text); if (!ranges) continue;
                    html.set(`${deleted[k].id}:old`, markRanges(html.get(`${deleted[k].id}:old`), ranges.old));
                    html.set(`${added[k].id}:new`, markRanges(html.get(`${added[k].id}:new`), ranges.new));
                }
            }
        }
        return { language, html };
    }
    Object.assign(App, { languageFor, splitHtml, renderLines, changedRanges, markRanges });
})(window.WorkspaceGitApp = window.WorkspaceGitApp || {});
