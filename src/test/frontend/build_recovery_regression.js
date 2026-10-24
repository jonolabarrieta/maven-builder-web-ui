/* Unit interaction checks use a small DOM double, not a running browser. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

function fixture() {
    const elements = new Map();
    for (const id of ['retry-container', 'retry-button', 'skip-failed-button', 'failed-project-name']) {
        const classes = new Set(['hidden']);
        elements.set(id, { disabled: false, textContent: '', attributes: {},
            classList: { add: name => classes.add(name), remove: name => classes.delete(name), contains: name => classes.has(name) },
            setAttribute(name, value) { this.attributes[name] = value; } });
    }
    const requests = [], errors = [], empty = [];
    let respond = async () => ({ ok: true, json: async () => ({ state: 'accepted' }) });
    const context = { window: {}, document: { getElementById: id => elements.get(id) }, URLSearchParams,
        fetch: async (url, options) => { requests.push({ url, mode: options.body.get('mode') }); return respond(); } };
    vm.runInNewContext(fs.readFileSync('src/main/resources/static/js/build-recovery.js', 'utf8'), context);
    const recovery = context.window.BuildRecovery;
    recovery.init({ workspaceId: 1, onError: error => errors.push(error.message), onEmpty: message => empty.push(message) });
    const failure = (id = 'first', workspaceId = 1) => ({ workspaceId, runId: id, continuationId: id, projectName: 'broken <module>' });
    return { recovery, elements, requests, errors, empty, failure, setResponse: value => respond = value };
}

(async () => {
    let checks = 0;
    let f = fixture();
    f.recovery.show(f.failure('other', 2));
    assert(f.elements.get('retry-container').classList.contains('hidden')); checks++;
    f.recovery.show(f.failure());
    assert.equal(f.elements.get('failed-project-name').textContent, 'broken <module>');
    assert(!f.elements.get('retry-container').classList.contains('hidden')); checks++;
    await f.elements.get('skip-failed-button').onclick();
    assert.equal(f.requests[0].url, '/workspaces/1/build-continuations/first'); assert.equal(f.requests[0].mode, 'skip'); checks++;
    f.recovery.show(f.failure()); assert(f.elements.get('retry-container').classList.contains('hidden')); checks++;
    f = fixture(); f.recovery.show(f.failure());
    let finish; f.setResponse(() => new Promise(resolve => finish = resolve));
    const first = f.elements.get('retry-button').onclick(); await f.elements.get('skip-failed-button').onclick();
    assert.equal(f.requests.length, 1); assert(f.elements.get('skip-failed-button').disabled); checks++;
    finish({ ok: true, json: async () => ({ state: 'accepted' }) }); await first;
    assert.equal(f.requests[0].mode, 'retry'); checks++;
    f = fixture(); f.recovery.show(f.failure());
    f.setResponse(async () => ({ ok: false, json: async () => ({ detail: 'Expired. Start a new build.' }) }));
    await f.elements.get('retry-button').onclick();
    assert.equal(f.errors[0], 'Expired. Start a new build.'); assert(!f.elements.get('skip-failed-button').disabled); checks++;
    f.setResponse(async () => ({ ok: true, json: async () => ({ state: 'empty', message: 'No remaining projects' }) }));
    await f.elements.get('skip-failed-button').onclick(); assert.deepEqual(f.empty, ['No remaining projects']); checks++;
    f = fixture(); f.recovery.show(f.failure());
    f.setResponse(() => new Promise(resolve => finish = resolve)); const pending = f.elements.get('skip-failed-button').onclick();
    f.recovery.show(f.failure('second')); finish({ ok: true, json: async () => ({ state: 'accepted' }) }); await pending;
    assert(!f.elements.get('retry-container').classList.contains('hidden')); assert(!f.elements.get('retry-button').disabled); checks++;
    f.recovery.reset(); f.recovery.show(f.failure('second')); assert(f.elements.get('retry-container').classList.contains('hidden')); checks++;
    console.log(`${checks} build recovery interaction checks passed`);
})().catch(error => { console.error(error); process.exitCode = 1; });
