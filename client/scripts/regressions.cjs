const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const assert = require('node:assert/strict');
const crypto = require('node:crypto').webcrypto;
const ts = require('typescript');
const root = path.join(__dirname, '../src');
const compile = (name) => ts.transpileModule(fs.readFileSync(path.join(root, name), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText;
const mockModule = { exports: {} };
vm.runInNewContext(compile('store/mockData.ts'), { module: mockModule, exports: mockModule.exports });
const demo = mockModule.exports;
function create(init) {
  let state;
  const get = () => state;
  const set = (patch) => { state = { ...state, ...(typeof patch === 'function' ? patch(state) : patch) }; };
  state = init(set, get);
  return Object.assign(() => state, { getState: get, setState: set, subscribe: () => () => {} });
}
function fresh() {
  const api = {
    fetchGraph: async () => ({ methods: [] }), fetchAnalysis: async () => ({ issues: [], metrics: {} }),
    optimizeCode: async (files) => ({ files: files.map(f => ({ ...f, source: f.source + '\n// optimized' })) }),
    decomposeMonolith: async (files) => ({ files }),
    streamVerify: async (req, log, done) => done({ allPassed: false, behaviorMatched: false, testResults: [] }),
    runCode: async () => ({ compiled: true, stdout: 'ok', stderr: '', exitCode: 0 }),
  };
  const module = { exports: {} };
  const imports = { zustand: { create }, './mockData': demo, '../api/apiClient': { ApiClient: api, ApiError: Error },
    './graphEdits': {}, './graphMapper': { toIssues: x => x, toMethodInfos: () => [],
      pickInitialMethod: () => null, toMetrics: () => ({ speedup: '—' }) } };
  vm.runInNewContext(compile('store/useStudioStore.ts'), { module, exports: module.exports,
    require: name => { assert.ok(imports[name], name); return imports[name]; }, console, crypto,
    setTimeout: () => 0, clearTimeout() {}, localStorage: { getItem: () => null }, window: undefined });
  return { store: module.exports.useStudioStore, api };
}
let count = 0;
async function check(name, action) { await action(); count++; console.log(`PASS ${name}`); }
(async () => {
  await check('demo starts pending and responds to stdin', async () => {
    assert.ok(demo.INITIAL_TESTS.every(t => t.status === 'PENDING' && t.durationMs === 0));
    assert.match(demo.INITIAL_JAVA_CODE, /new Scanner\(System.in\)/);
    assert.match(demo.INITIAL_JAVA_CODE, /line.split\("\\\\s\+"\)/);
  });
  await check('offline actions release busy flags', async () => {
    const { store: s } = fresh(); await s.getState().runTests(); await s.getState().runOptimization();
    assert.equal(s.getState().isRunningTests, false); assert.equal(s.getState().isCompiling, false);
  });
  await check('editor changes survive tab switches', async () => {
    const { store: s } = fresh(); s.getState().setCode('edited');
    s.getState().selectFile('file-order'); s.getState().setActiveTabId('tab-main');
    assert.equal(s.getState().code, 'edited'); assert.equal(s.getState().activeFileId, 'file-main');
  });
  await check('failed and incomplete gate never commits candidate', async () => {
    for (const passed of [false, true]) {
      const { store: s, api } = fresh(); const source = s.getState().code;
      api.streamVerify = async (req, log, done) => done({ allPassed: passed, behaviorMatched: passed, testResults: [] });
      s.setState({ isBackendOnline: true }); await s.getState().runOptimization();
      assert.equal(s.getState().code, source); assert.equal(s.getState().isOptimized, false);
      assert.ok(s.getState().tests.every(t => t.status === 'FAILED'));
    }
  });
  await check('all files and unique test ids go through exact verification', async () => {
    const { store: s, api } = fresh(); let request;
    s.setState({ isBackendOnline: true, tests: [
      { id: 'a', name: 'same', stdin: '1', status: 'PENDING' }, { id: 'b', name: 'same', stdin: '2', status: 'PENDING' }] });
    api.streamVerify = async (req, log, done) => { request = req; done({ allPassed: false, behaviorMatched: false,
      testResults: [{ name: 'a', status: 'PASSED' }, { name: 'b', status: 'FAILED' }] }); };
    await s.getState().runTests(); assert.equal(request.original.length, 6); assert.equal(request.modified.length, 6);
    assert.equal(request.mode, 'EXACT'); assert.equal(s.getState().tests[0].status, 'PASSED');
    assert.equal(s.getState().tests[1].status, 'FAILED');
  });
  await check('late mutation cannot overwrite manual edit', async () => {
    const { store: s, api } = fresh(); let resolve;
    s.setState({ isBackendOnline: true }); api.mutateGraph = () => new Promise(r => { resolve = r; });
    const pending = s.getState().applyMutation({ action: 'DELETE_NODE', nodeId: 'x' }, 'test');
    s.getState().setCode('new edit'); resolve({ files: [{ path: 'com.example/Main.java', source: 'stale' }] });
    assert.equal(await pending, false); assert.equal(s.getState().code, 'new edit');
  });
  await check('editing a test invalidates an in-flight gate', async () => {
    const { store: s, api } = fresh(); let complete;
    s.setState({ isBackendOnline: true });
    api.streamVerify = async (req, log, done) => { complete = () => done({ allPassed: true, behaviorMatched: true,
      testResults: req.tests.map(t => ({ name: t.name, status: 'PASSED' })) }); };
    await s.getState().runOptimization(); s.getState().updateTestCase('t1', { stdin: 'changed' }); complete();
    assert.equal(s.getState().isOptimized, false); assert.equal(s.getState().tests[0].status, 'PENDING');
  });
  await check('successful gate commits every file and retains measured speedup', async () => {
    const { store: s, api } = fresh();
    s.setState({ isBackendOnline: true });
    api.streamVerify = async (req, log, done) => done({ allPassed: true, behaviorMatched: true, speedup: '1.3x',
      testResults: req.tests.map(t => ({ name: t.name, status: 'PASSED', durationMs: 1 })) });
    await s.getState().runOptimization(); await s.getState().refreshGraphAndAnalysis();
    assert.equal(s.getState().isOptimized, true); assert.equal(s.getState().metrics.speedup, '1.3x');
    assert.ok(s.getState().openTabs.every(t => t.code.endsWith('// optimized')));
    s.getState().setCode('manual'); assert.equal(s.getState().isOptimized, false);
    assert.ok(s.getState().tests.every(t => t.status === 'PENDING'));
  });
  await check('incomplete/malformed SSE fails; only complete valid report succeeds', async () => {
    for (const [payload, expected] of [
      ['event: log\ndata: hello\n\n', 'error'], ['event: complete\ndata: broken\n\n', 'error'],
      ['event: complete\ndata: {"allPassed":true}\n\n', 'error'],
      ['event: complete\ndata: {"allPassed":true,"behaviorMatched":true,"testResults":[]}\n\n', 'complete']]) {
      const module = { exports: {} }; const events = [];
      vm.runInNewContext(compile('api/apiClient.ts'), { module, exports: module.exports, TextDecoder, AbortController,
        setTimeout: () => 0, clearTimeout() {}, fetch: async () => new Response(payload) });
      await module.exports.ApiClient.streamVerify({}, () => {}, () => events.push('complete'), () => events.push('error'));
      assert.deepEqual(events, [expected]);
    }
  });
  console.log(`${count} regression groups passed`);
})().catch(error => { console.error(error); process.exitCode = 1; });
