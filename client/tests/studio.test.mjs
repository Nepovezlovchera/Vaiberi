import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const output = fileURLToPath(new URL('../../tmp/studio-test.cjs', import.meta.url));
await build({ entryPoints: [fileURLToPath(new URL('./entry.ts', import.meta.url))],
  outfile: output, bundle: true, platform: 'node', format: 'cjs', logLevel: 'silent' });
globalThis.localStorage = { getItem: () => null, setItem() {}, removeItem() {} };
const { useStudioStore: store, ApiClient, gatePassed, reportedTests } = createRequire(import.meta.url)(output);
const initial = store.getState();
const realStreamVerify = ApiClient.streamVerify;
const report = (updates = {}) => ({ allPassed: true, behaviorMatched: true, speedup: '1.3x',
  passed: 1, total: 1, compileOutput: '', testResults: [{ name: 'case', status: 'PASSED', durationMs: 2 }], ...updates });
beforeEach(() => {
  store.setState(initial, true);
  ApiClient.fetchGraph = async () => ({ methods: [] });
  ApiClient.fetchAnalysis = async () => ({ issues: [], metrics: { currentComplexity: 'O(n)', predictedComplexity: 'O(n)', issuesCount: 0 } });
  ApiClient.streamVerify = realStreamVerify;
});

test('gate rejects mismatches, missing results and compile failures', () => {
  assert.equal(gatePassed(report()), true);
  assert.equal(gatePassed(report({ behaviorMatched: false })), false);
  assert.equal(gatePassed(report({ allPassed: false })), false);
  assert.equal(gatePassed(report({ testResults: [] })), false);
  const tests = [{ id: 't', name: 'missing', stdin: '', status: 'PENDING', durationMs: 0 }];
  assert.equal(reportedTests(tests, report())[0].status, 'FAILED');
  assert.equal(reportedTests(tests, report({ compileOutput: 'ERROR Main.java:4: bad type' }))[0].errorDetails,
    'ERROR Main.java:4: bad type');
});

test('multi-file edits survive switching tabs; baseline includes all original sources', async () => {
  await store.getState().importProject([
    { name: 'demo/App.java', content: 'package demo; class App { public static void main(String[] a) { Helper.f(); } }' },
    { name: 'demo/Helper.java', content: 'package demo; class Helper { static void f() {} }' },
  ]);
  const baseline = structuredClone(store.getState().baselineFiles);
  const helper = store.getState().getProjectFiles().find(f => f.path.endsWith('Helper.java'));
  store.getState().selectFile('imp-file-1');
  store.getState().setCode(helper.source.replace('f()', 'g()'));
  store.getState().selectFile('imp-file-0');
  store.getState().selectFile('imp-file-1');
  assert.match(store.getState().code, /g\(\)/);
  assert.deepEqual(store.getState().baselineFiles, baseline);
  assert.equal(store.getState().getProjectFiles().length, 2);
});

test('graph and analysis receive every source, including non-active files', async () => {
  await store.getState().importProject([{ name: 'A.java', content: 'class A {}' }, { name: 'B.java', content: 'class B {}' }]);
  const received = [];
  ApiClient.fetchGraph = async files => { received.push(files); return { methods: [] }; };
  ApiClient.fetchAnalysis = async files => { received.push(files); return { issues: [], metrics: {} }; };
  store.setState({ isBackendOnline: true });
  await store.getState().refreshGraphAndAnalysis();
  assert.equal(received.length, 2);
  assert.ok(received.every(files => files.length === 2));
});

test('interactive console runs all project sources and lets the server find the main class', async () => {
  await store.getState().importProject([
    { name: 'demo/App.java', content: 'package demo; class App { public static void main(String[] a) { Helper.f(); } }' },
    { name: 'demo/Helper.java', content: 'package demo; class Helper { static void f() {} }' },
  ]);
  store.setState({ isBackendOnline: true });
  const previous = ApiClient.runSession;
  try {
    ApiClient.runSession = async (files, mainClass, handlers) => {
      assert.deepEqual(files, store.getState().getProjectFiles());
      assert.equal(files.length, 2);
      assert.equal(mainClass, undefined);
      handlers.onSession('session');
      handlers.onOut('Ready: ');
      handlers.onOut('done\n');
      handlers.onExit({ exitCode: 0, millis: 1, reason: null });
    };
    await store.getState().runCodeInConsole();
    assert.equal(store.getState().isProgramRunning, false);
    assert.equal(store.getState().programSessionId, null);
    assert.ok(store.getState().consoleLogs.includes('[STDOUT] Ready: done'));
  } finally { ApiClient.runSession = previous; }
});

test('preset switching replaces imported sources and resets the verification baseline', async () => {
  await store.getState().importProject([{ name: 'Other.java', content: 'class Other {}' }]);
  await store.getState().setPreset('demo');
  const demo = store.getState().getProjectFiles();
  assert.equal(demo.length, 1);
  assert.equal(demo[0].path, 'Main.java');
  assert.match(demo[0].source, /processOrders/);
  assert.deepEqual(store.getState().baselineFiles, demo);
  await store.getState().setPreset('monolith');
  assert.notEqual(store.getState().getProjectFiles()[0].source, demo[0].source);
  assert.deepEqual(store.getState().baselineFiles, store.getState().getProjectFiles());
  assert.equal(store.getState().verificationReport, null);
});

test('failed optimization preserves project and exposes full compiler diagnostics', async () => {
  const before = store.getState().getProjectFiles();
  store.setState({ isBackendOnline: true, tests: [{ id: 'case', name: 'case', stdin: '', status: 'PENDING', durationMs: 0 }] });
  ApiClient.optimizeCode = async () => ({ files: [{ path: 'Main.java', source: 'broken' }], explanation: 'candidate' });
  ApiClient.decomposeMonolith = async files => ({ files, explanation: 'decompose' });
  ApiClient.streamVerify = async (_req, _log, complete) => complete(report({ allPassed: false, behaviorMatched: false,
    compileOutput: 'ERROR Main.java:3\nfull diagnostic', testResults: [{ name: 'case', status: 'COMPILE_ERROR', durationMs: 0 }] }));
  await store.getState().runOptimization();
  assert.deepEqual(store.getState().getProjectFiles(), before);
  assert.equal(store.getState().isOptimized, false);
  assert.ok(store.getState().consoleLogs.some(l => l.includes('Quality Gate FAILED')));
  assert.ok(store.getState().consoleLogs.some(l => l.includes('full diagnostic')));
  assert.equal(store.getState().isCompiling, false);
});

test('successful optimization installs all decomposed sources and measured speedup', async () => {
  const original = store.getState().getProjectFiles();
  const changed = [{ path: 'Main.java', source: 'class Main {}' }, { path: 'Parser.java', source: 'class Parser {}' }];
  store.setState({ isBackendOnline: true, tests: [{ id: 'case', name: 'case', stdin: '', status: 'PENDING', durationMs: 0 }] });
  ApiClient.optimizeCode = async files => ({ files, explanation: 'candidate' });
  ApiClient.decomposeMonolith = async () => ({ files: changed, explanation: 'split' });
  ApiClient.streamVerify = async (req, _log, complete) => {
    assert.deepEqual(req.original, original); assert.deepEqual(req.modified, changed);
    assert.equal(req.mode, 'EXACT'); complete(report());
  };
  await store.getState().runOptimization();
  assert.deepEqual(store.getState().getProjectFiles(), changed);
  assert.deepEqual(store.getState().baselineFiles, original);
  assert.equal(store.getState().metrics.speedup, '1.3x');
});

test('SSE handles fragmented CRLF events and rejects missing or malformed completion', async () => {
  const send = async chunks => {
    globalThis.fetch = async () => new Response(new ReadableStream({ start(c) {
      chunks.forEach(chunk => c.enqueue(new TextEncoder().encode(chunk))); c.close();
    } }));
    const completed = [], errors = [];
    await realStreamVerify({}, () => {}, r => completed.push(r), e => errors.push(e));
    return { completed, errors };
  };
  const good = await send(['event: complete\r', '\ndata: ' + JSON.stringify(report()) + '\r\n\r\n']);
  assert.equal(good.completed.length, 1); assert.equal(good.errors.length, 0);
  assert.equal((await send(['event: log\ndata: hello\n\n'])).errors.length, 1);
  assert.equal((await send(['event: complete\ndata: broken\n\n'])).errors.length, 1);
});
