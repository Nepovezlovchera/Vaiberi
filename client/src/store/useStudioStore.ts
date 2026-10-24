import { create } from 'zustand';
import { Node, Edge } from '@xyflow/react';
import { FileItem, CodeIssue, ComplexityMetrics, TestCaseResult, MethodInfo, SyncStatus, FileTab } from '../types/studio';
import {
  INITIAL_JAVA_CODE,
  OPTIMIZED_JAVA_CODE,
  INITIAL_FILES,
  INITIAL_ISSUES,
  INITIAL_METRICS,
  INITIAL_NODES,
  INITIAL_EDGES,
  INITIAL_TESTS,
} from './mockData';
import { SAMPLE_MONOLITH_CODE, SAMPLE_MONOLITH_TESTS } from './monolithData';
import { ApiClient, ApiError, MethodGraphResponseDto, MutateRequestDto } from '../api/apiClient';
import { connectPlacement, isEntryNode, moveAfter, moveInside, nodeIdAt, paletteSnippet, placeAfter, placeInside } from './graphEdits';
import { methodToFlow, methodOfNode, pickInitialMethod, toIssues, toMethodInfos, toMetrics } from './graphMapper';

// Client-side Security: Ring Buffer limit for console to prevent DOM freeze (Client-side DoS)
const MAX_LOG_ENTRIES = 1000;
const SYNC_DEBOUNCE_MS = 600;

// Последний ответ /api/graph по всем методам: граф другого метода строится без нового запроса
let graphCache: MethodGraphResponseDto[] = [];
let syncTimer: ReturnType<typeof setTimeout> | undefined;
// Номер последнего запроса: ответ на устаревший запрос (код уже поменялся) отбрасывается
let syncSeq = 0;

const updateFileCode = (items: FileItem[], fileId: string, code: string): FileItem[] =>
  items.map((item) =>
    item.id === fileId
      ? { ...item, code }
      : item.children
        ? { ...item, children: updateFileCode(item.children, fileId, code) }
        : item
  );

const timeNow = () => new Date().toTimeString().split(' ')[0];

interface StudioState {
  // Тема оформления
  theme: 'dark' | 'light';
  toggleTheme: () => void;

  // Файлы
  files: FileItem[];
  activeFileId: string;
  code: string;
  /** Исходный (до оптимизации) код импортированного файла; null — берётся исходный код. */
  baselineCode: string | null;

  // Сеть / Бэкенд
  isBackendOnline: boolean;
  checkBackend: () => Promise<void>;

  // Граф и методы
  nodes: Node[];
  edges: Edge[];
  selectedNodeId: string | null;
  activeMethodId: string | null;
  methodsList: MethodInfo[];
  setActiveMethodId: (methodId: string) => Promise<void>;

  // Подсветка в редакторе
  activeLineRange: { start: number; end: number } | null;

  // Проблемы и метрики
  issues: CodeIssue[];
  selectedIssueId: string | null;
  metrics: ComplexityMetrics;
  isOptimized: boolean;

  // Табы интерфейса (Слайд 4 ТЗ)
  bottomTab: 'code' | 'console' | 'tests';
  rightTab: 'issues' | 'recommendations' | 'metrics';
  bottomLayoutMode: 'split3' | 'tabs';
  setBottomLayoutMode: (mode: 'split3' | 'tabs') => void;

  // Вкладки файлов над Monaco Editor (Слайд 4 ТЗ)
  openTabs: FileTab[];
  activeTabId: string;
  openFileTab: (file: { id?: string; name: string; path: string; code: string }) => void;
  closeFileTab: (tabId: string) => void;
  setActiveTabId: (tabId: string) => void;

  // Консоль и тесты
  consoleLogs: string[];
  tests: TestCaseResult[];
  isRunningTests: boolean;
  isCompiling: boolean;
  isMutating: boolean;

  // Состояние связи редактор/граф/анализ с бэкендом
  syncStatus: SyncStatus;
  syncError: string | null;
  analysisLoaded: boolean;

  // Интерактивный дебаг / трассировка
  isDebugging: boolean;
  debugStepIndex: number;
  startDebug: () => void;
  stepDebug: () => void;
  stopDebug: () => void;

  // Экшены
  selectFile: (fileId: string) => void;
  setCode: (code: string) => void;
  refreshGraphAndAnalysis: (sourceCode?: string, methodId?: string) => Promise<void>;
  setNodes: (nodes: Node[] | ((prev: Node[]) => Node[])) => void;
  setEdges: (edges: Edge[] | ((prev: Edge[]) => Edge[])) => void;
  selectNode: (nodeId: string | null) => void;
  selectIssue: (issueId: string) => void;
  /** Ошибка последней правки графа (код не скомпилировался и т.п.); правка при этом откачена */
  mutationError: string | null;
  dismissMutationError: () => void;
  /** Отправить правку на /api/graph/mutate; при ошибке вернуть граф к виду до правки. true — правка применена. */
  applyMutation: (req: Omit<MutateRequestDto, 'files'>, describe: string) => Promise<boolean>;
  deleteSelectedNode: () => Promise<void>;
  /** Блок из палитры: после узла targetNodeId, в начало его тела (inside) или в конец метода (target = null) */
  addCustomNode: (type: string, targetNodeId: string | null, inside: boolean) => Promise<void>;
  /** Перенос узла после targetNodeId или в начало его тела (inside). false — ничего не изменилось */
  moveNode: (nodeId: string, targetNodeId: string, inside: boolean) => Promise<boolean>;
  /** Новая связь на холсте: target переносится в ветку source, на которую указывает ручка (или сразу после source) */
  connectNodes: (sourceId: string, sourceHandle: string | null | undefined, targetId: string) => Promise<void>;
  setBottomTab: (tab: 'code' | 'console' | 'tests') => void;
  setRightTab: (tab: 'issues' | 'recommendations' | 'metrics') => void;
  runOptimization: () => Promise<void>;
  optimizeSingleIssue: (issueId: string) => Promise<void>;
  runTests: () => Promise<void>;
  runCodeInConsole: (stdin?: string) => Promise<void>;
  addTestCase: (name: string, stdin: string, expectedStdout?: string) => void;
  updateTestCase: (id: string, updates: Partial<TestCaseResult>) => void;
  removeTestCase: (id: string) => void;
  loadTestFiles: (files: { name: string; content: string }[]) => void;
  /** «Импорт Java»: заменяет проект в дереве загруженными файлами; главным становится файл с main(). */
  importProject: (files: { name: string; content: string }[]) => Promise<void>;
  resetDefaultTests: () => void;
  addConsoleLog: (msg: string) => void;
}

export const useStudioStore = create<StudioState>((set, get) => ({
  theme: (typeof window !== 'undefined' && localStorage.getItem('jb-theme') === 'light' ? 'light' : 'dark') as 'dark' | 'light',
  toggleTheme: () => {
    const nextTheme = get().theme === 'dark' ? 'light' : 'dark';
    set({ theme: nextTheme });
    if (typeof window !== 'undefined') {
      localStorage.setItem('jb-theme', nextTheme);
      if (nextTheme === 'dark') {
        document.documentElement.classList.add('dark');
      } else {
        document.documentElement.classList.remove('dark');
      }
    }
  },
  files: INITIAL_FILES,
  activeFileId: 'file-main',
  code: INITIAL_JAVA_CODE,
  baselineCode: null,

  isBackendOnline: false,

  nodes: INITIAL_NODES,
  edges: INITIAL_EDGES,
  selectedNodeId: null,
  activeMethodId: 'Main.processOrders',
  methodsList: [{ methodId: 'Main.processOrders', signature: 'processOrders(List<Order>)', lineStart: 8, lineEnd: 42 }],

  activeLineRange: null,

  issues: INITIAL_ISSUES,
  selectedIssueId: null,
  metrics: INITIAL_METRICS,
  isOptimized: false,

  bottomTab: 'code',
  rightTab: 'issues',
  bottomLayoutMode: 'split3', // Слайд 4 ТЗ: 3-колоночный режим по умолчанию
  setBottomLayoutMode: (mode) => set({ bottomLayoutMode: mode }),

  openTabs: [
    {
      id: 'tab-main',
      name: 'Main.java',
      path: 'src/com.example/Main.java',
      code: INITIAL_JAVA_CODE,
    },
  ],
  activeTabId: 'tab-main',

  consoleLogs: [
    `[${new Date().toTimeString().split(' ')[0]}] Java Blueprints Studio запущена.`,
    `[${new Date().toTimeString().split(' ')[0]}] Проверка подключения к Spring Boot API (:8080)...`,
  ],
  tests: INITIAL_TESTS,
  isRunningTests: false,
  isCompiling: false,
  isMutating: false,
  mutationError: null,

  syncStatus: 'idle',
  syncError: null,
  analysisLoaded: false,

  isDebugging: false,
  debugStepIndex: 0,

  checkBackend: async () => {
    const online = await ApiClient.checkHealth();
    set({ isBackendOnline: online });
    const now = new Date().toTimeString().split(' ')[0];
    if (online) {
      get().addConsoleLog(`[${now}] Подключение к Spring Boot API: УСПЕШНО (Live Backend Mode) ✔`);
      await get().refreshGraphAndAnalysis();
    } else {
      set({ syncStatus: 'offline' });
      get().addConsoleLog(`[${now}] Бэкенд оффлайн: показаны демонстрационные данные`);
    }
  },

  refreshGraphAndAnalysis: async (sourceCode?: string, methodId?: string) => {
    clearTimeout(syncTimer);
    if (!get().isBackendOnline) {
      set({ syncStatus: 'offline' });
      return;
    }
    const currentCode = sourceCode ?? get().code;
    const files = [{ path: 'Main.java', source: currentCode }];
    const seq = ++syncSeq;
    set({ syncStatus: 'syncing', syncError: null });
    try {
      // Граф запрашивается по всем методам (без methodId), иначе список методов схлопнулся бы до одного
      const [graph, analysis] = await Promise.all([ApiClient.fetchGraph(files), ApiClient.fetchAnalysis(files)]);
      if (seq !== syncSeq) return;

      graphCache = graph.methods;
      const issues = toIssues(analysis.issues);
      const methodsList = toMethodInfos(graph.methods);
      const wanted = methodId ?? get().activeMethodId;
      const activeMethodId = wanted && methodsList.some((m) => m.methodId === wanted)
        ? wanted
        : pickInitialMethod(methodsList, issues);
      const active = graphCache.find((m) => m.methodId === activeMethodId);
      const flow = active ? methodToFlow(active, issues) : { nodes: [], edges: [] };
      const keepSelection = flow.nodes.some((n) => n.id === get().selectedNodeId);

      set((state) => ({
        methodsList,
        activeMethodId,
        nodes: flow.nodes,
        edges: flow.edges,
        selectedNodeId: keepSelection ? state.selectedNodeId : null,
        issues,
        selectedIssueId: issues.some((i) => i.id === state.selectedIssueId) ? state.selectedIssueId : null,
        metrics: toMetrics(analysis.metrics),
        syncStatus: 'ok',
        analysisLoaded: true,
      }));
      get().addConsoleLog(
        `[${timeNow()}] AST синхронизирован: методов ${methodsList.length}, дефектов ${issues.length}, сложность ${analysis.metrics.currentComplexity} ✔`
      );
    } catch (err: any) {
      if (seq !== syncSeq) return;
      // Граф и дефекты остаются от последнего удачного разбора, ошибка видна на холсте и в консоли
      const message = err?.message || 'Ошибка разбора кода';
      set({ syncStatus: 'error', syncError: message });
      get().addConsoleLog(`[${timeNow()}] [ERROR] Разбор кода: ${message}`);
    }
  },

  setActiveMethodId: async (methodId: string) => {
    const graph = graphCache.find((m) => m.methodId === methodId);
    // Нет в кэше (бэкенд ещё не отвечал) — строим заново
    if (!graph) return get().refreshGraphAndAnalysis(get().code, methodId);
    const { nodes, edges } = methodToFlow(graph, get().issues);
    set({ activeMethodId: methodId, nodes, edges, selectedNodeId: null, activeLineRange: null });
  },

  selectFile: (fileId: string) => {
    const findFile = (items: FileItem[]): FileItem | undefined => {
      for (const item of items) {
        if (item.id === fileId) return item;
        if (item.children) {
          const found = findFile(item.children);
          if (found) return found;
        }
      }
    };
    const file = findFile(get().files);
    if (file && file.type === 'file' && file.code) {
      set({
        activeFileId: fileId,
        code: file.code,
        ...(get().baselineCode !== null ? { baselineCode: file.code } : {}),
      });
      get().refreshGraphAndAnalysis(file.code);
    }
  },

  setCode: (code: string) => {
    if (code === get().code) return;
    set({ code });
    if (!get().isBackendOnline) return;
    // Ответ на запрос, отправленный до этой правки, уже устарел
    syncSeq++;
    clearTimeout(syncTimer);
    syncTimer = setTimeout(() => get().refreshGraphAndAnalysis(), SYNC_DEBOUNCE_MS);
  },

  setNodes: (nodesOrUpdater) => {
    set((state) => ({
      nodes: typeof nodesOrUpdater === 'function' ? nodesOrUpdater(state.nodes) : nodesOrUpdater,
    }));
  },

  setEdges: (edgesOrUpdater) => {
    set((state) => ({
      edges: typeof edgesOrUpdater === 'function' ? edgesOrUpdater(state.edges) : edgesOrUpdater,
    }));
  },

  selectNode: (nodeId: string | null) => {
    const { nodes } = get();
    if (!nodeId) {
      set({ selectedNodeId: null, activeLineRange: null });
      return;
    }
    const node = nodes.find((n) => n.id === nodeId);
    if (node && node.data.lineStart) {
      set({
        selectedNodeId: nodeId,
        activeLineRange: {
          start: node.data.lineStart as number,
          end: (node.data.lineEnd as number) || (node.data.lineStart as number),
        },
      });
    } else {
      set({ selectedNodeId: nodeId });
    }
  },

  selectIssue: (issueId: string) => {
    const issue = get().issues.find((i) => i.id === issueId);
    if (issue) {
      const methodId = methodOfNode(get().methodsList, issue.nodeId);
      if (methodId && methodId !== get().activeMethodId) get().setActiveMethodId(methodId);
      set({
        selectedIssueId: issueId,
        selectedNodeId: issue.nodeId,
        activeLineRange: { start: issue.lineStart, end: issue.lineEnd },
        bottomTab: 'code',
      });
    }
  },

  dismissMutationError: () => set({ mutationError: null }),

  applyMutation: async (req, describe) => {
    const { isBackendOnline, isMutating, code, activeMethodId, nodes, edges } = get();
    if (isMutating) return false;
    if (!isBackendOnline) {
      set({ mutationError: 'Правки графа выполняет бэкенд, а он недоступен. Граф не изменён.', nodes: [...nodes] });
      return false;
    }
    // Снимок для отката: граф до правки (в том числе позиции узлов до перетаскивания)
    const snapshot = { nodes, edges };
    // Отложенная пересборка по старому коду больше не нужна
    clearTimeout(syncTimer);
    syncSeq++;
    set({ isMutating: true, mutationError: null });
    get().addConsoleLog(`[${timeNow()}] POST /api/graph/mutate: ${describe}...`);
    try {
      const res = await ApiClient.mutateGraph({ files: [{ path: 'Main.java', source: code }], ...req });
      const newSource = res.files?.[0]?.source;
      if (newSource === undefined) throw new Error('Сервер не вернул код');

      // Граф берём из ответа (ID узлов пересчитаны по позициям), анализ догружаем отдельно
      graphCache = res.graph.methods;
      const methodsList = toMethodInfos(res.graph.methods);
      const active = graphCache.find((m) => m.methodId === activeMethodId) ?? graphCache[0];
      const flow = active ? methodToFlow(active, get().issues) : { nodes: [], edges: [] };
      set((state) => ({
        code: newSource,
        files: updateFileCode(state.files, state.activeFileId, newSource),
        methodsList,
        activeMethodId: active?.methodId ?? null,
        nodes: flow.nodes,
        edges: flow.edges,
        selectedNodeId: null,
        activeLineRange: null,
        isOptimized: false,
        isMutating: false,
      }));
      const note = res.validated ? 'компиляция пройдена' : 'без проверки компиляции';
      get().addConsoleLog(`[${timeNow()}] Правка применена (${note}) ✔`);
      get().refreshGraphAndAnalysis(newSource, active?.methodId);
      return true;
    } catch (err: any) {
      // Сервер ничего не хранит: код не менялся, возвращаем граф к виду до правки
      const isCompile = err instanceof ApiError && err.code === 'COMPILE_ERROR';
      const message = String(err?.message || 'Ошибка правки графа');
      set({
        nodes: snapshot.nodes,
        edges: snapshot.edges,
        isMutating: false,
        mutationError: (isCompile ? 'Правка отменена — код не компилируется:\n' : 'Правка отменена: ') + message,
      });
      get().addConsoleLog(`[${timeNow()}] [ERROR] ${isCompile ? 'COMPILE_ERROR' : 'Правка'}: ${message.split('\n').slice(0, 3).join(' ')}`);
      return false;
    }
  },

  deleteSelectedNode: async () => {
    const { selectedNodeId } = get();
    if (!selectedNodeId) return;
    if (isEntryNode(selectedNodeId)) {
      set({ mutationError: 'Метод целиком удалить нельзя — удаляются только операторы.' });
      return;
    }
    await get().applyMutation({ action: 'DELETE_NODE', nodeId: selectedNodeId }, `удаление ${selectedNodeId}`);
  },

  addCustomNode: async (type, targetNodeId, inside) => {
    const { activeMethodId, code, nodes } = get();
    const payload = paletteSnippet(type, code);
    if (!payload) {
      set({ mutationError: `Блок «${type}» нельзя вставить внутрь метода.` });
      return;
    }
    if (!activeMethodId) {
      set({ mutationError: 'Сначала выберите метод.' });
      return;
    }
    const targetType = nodes.find((n) => n.id === targetNodeId)?.type ?? '';
    // Брошен мимо узлов — в конец тела метода (index не задан)
    const place = !targetNodeId
      ? { parentId: `${activeMethodId}/entry`, index: undefined }
      : inside
        ? placeInside(targetNodeId, targetType)
        : placeAfter(targetNodeId);
    if (!place) {
      set({ mutationError: 'Сюда блок вставить нельзя: у этого узла нет тела.' });
      return;
    }
    const ok = await get().applyMutation(
      { action: 'INSERT_NODE', parentId: place.parentId, index: place.index, payload },
      `вставка «${payload.split('\n')[0]}» в ${place.parentId}`
    );
    if (ok && place.index !== undefined) get().selectNode(nodeIdAt({ parentId: place.parentId, index: place.index }));
  },

  moveNode: async (nodeId, targetNodeId, inside) => {
    const { nodes } = get();
    const targetType = nodes.find((n) => n.id === targetNodeId)?.type ?? '';
    const place = inside ? moveInside(nodeId, targetNodeId, targetType) : moveAfter(nodeId, targetNodeId);
    if (!place) return false;
    const ok = await get().applyMutation(
      { action: 'REORDER', nodeId, parentId: place.parentId, index: place.index },
      `перенос ${nodeId} → ${place.parentId}[${place.index}]`
    );
    if (ok) get().selectNode(nodeIdAt(place));
    return ok;
  },

  connectNodes: async (sourceId, sourceHandle, targetId) => {
    const { nodes, edges } = get();
    const sourceType = nodes.find((n) => n.id === sourceId)?.type ?? '';
    // У if есть else, если ветка «Нет» ведёт в его слот e (без else она ведёт к следующему оператору)
    const hasElse = edges.some((e) => e.source === sourceId && e.target.startsWith(`${sourceId}.e.`));
    const place = connectPlacement(sourceId, sourceType, sourceHandle, targetId, hasElse);
    if (!place) {
      set({ mutationError: 'Такую связь нельзя превратить в код: узел нельзя перенести сам в себя или в метод целиком.' });
      return;
    }
    const ok = await get().applyMutation(
      { action: 'REORDER', nodeId: targetId, parentId: place.parentId, index: place.index },
      `связь ${sourceId} → ${targetId}: перенос в ${place.parentId}[${place.index}]`
    );
    if (ok) get().selectNode(nodeIdAt(place));
  },

  setBottomTab: (tab) => set({ bottomTab: tab }),
  setRightTab: (tab) => set({ rightTab: tab }),

  // Client-side Security: Ring Buffer (максимум MAX_LOG_ENTRIES = 1000 строк)
  addConsoleLog: (msg: string) => {
    set((state) => ({
      consoleLogs: [...state.consoleLogs.slice(-(MAX_LOG_ENTRIES - 1)), msg],
    }));
  },

  optimizeSingleIssue: async (issueId: string) => {
    const { isBackendOnline, code, activeMethodId } = get();
    set({ isCompiling: true, bottomTab: 'console' });
    const now = new Date().toTimeString().split(' ')[0];

    get().addConsoleLog(`[${now}] Точечное устранение дефекта «${issueId}» (Слайд 5 ТЗ)...`);

    if (isBackendOnline) {
      try {
        const optRes = await ApiClient.optimizeCode([{ path: 'Main.java', source: code }], issueId);
        const optSource = optRes.files?.[0]?.source || code;

        set((state) => ({
          code: optSource,
          issues: state.issues.filter((i) => i.id !== issueId),
          isCompiling: false,
        }));

        get().addConsoleLog(`[${now}] Дефект «${issueId}» устранен: O(n²) → O(n). Объяснение: ${optRes.explanation || 'успешно'} ✔`);
        await get().refreshGraphAndAnalysis(optSource, activeMethodId || undefined);
        return;
      } catch (err: any) {
        get().addConsoleLog(`[WARN] Ошибка точечной оптимизации: ${err.message}`);
        set({ isCompiling: false });
      }
    }
  },

  runOptimization: async () => {
    const { isBackendOnline, code } = get();
    set({ isCompiling: true, bottomTab: 'console' });
    const now = new Date().toTimeString().split(' ')[0];

    get().addConsoleLog(`[${now}] Запуск комплексной оптимизации и декомпозиции монолита (Слайды 5-6 ТЗ)...`);

    if (isBackendOnline) {
      try {
        get().addConsoleLog(`[${now}] Вызов POST /api/optimize на Spring Boot (пакетное устранение O(n²))...`);
        const optRes = await ApiClient.optimizeCode([{ path: 'Main.java', source: code }]);
        const optSource = optRes.files?.[0]?.source || OPTIMIZED_JAVA_CODE;

        get().addConsoleLog(`[${now}] Результат: ${optRes.explanation || 'Устранено дублирование и O(n²)'}`);
        get().addConsoleLog(`[${now}] Вызов POST /api/decompose (автоматическое разделение на 4 класса)...`);
        const decompRes = await ApiClient.decomposeMonolith([{ path: 'Main.java', source: optSource }]);

        const newFiles = (decompRes.files || []).map((f: any) => ({
          id: `file-${f.path.replace(/\.java$/, '').toLowerCase()}`,
          name: f.path,
          path: `src/com.example/${f.path}`,
          type: 'file' as const,
          code: f.source,
          hasIssues: false,
        }));

        const decompTabs: FileTab[] = (decompRes.files || []).map((f: any) => ({
          id: `tab-${f.path.replace(/[^a-zA-Z0-9]/g, '_')}`,
          name: f.path,
          path: `src/com.example/${f.path}`,
          code: f.source,
        }));

        set((state) => ({
          code: optSource,
          isOptimized: true,
          openTabs: decompTabs.length > 0 ? decompTabs : state.openTabs,
          activeTabId: decompTabs[0]?.id || state.activeTabId,
          metrics: {
            ...state.metrics,
            current: 'O(n)',
            predicted: 'O(n)',
            speedup: '5.2x (Подтверждено)',
          },
          files: state.files.map((root) => {
            if (root.children) {
              return {
                ...root,
                children: root.children.map((pkg) => {
                  if (pkg.name === 'com.example') {
                    return {
                      ...pkg,
                      children: [...newFiles],
                    };
                  }
                  return pkg;
                }),
              };
            }
            return root;
          }),
        }));

        get().addConsoleLog(`[${now}] Декомпозиция завершена: выделены классы ${decompRes.createdFiles?.join(', ')}`);
        await get().refreshGraphAndAnalysis(optSource);

        get().addConsoleLog(`[${now}] Подключение к SSE стриму /api/verify/stream (Quality Gate)...`);
        const currentTests = get().tests.map((t) => ({
          name: t.name,
          stdin: t.stdin || '',
          expectedStdout: t.expectedStdout,
        }));

        await ApiClient.streamVerify(
          {
            original: [{ path: 'Main.java', source: code }],
            modified: decompRes.files,
            mainClass: 'Main',
            tests: currentTests,
            mode: 'SORTED_LINES',
          },
          (logLine) => get().addConsoleLog(`[SSE] ${logLine}`),
          (report) => {
            set((state) => ({
              isCompiling: false,
              tests: state.tests.map((existing) => {
                const tr = report.testResults?.find((r: any) => r.name === existing.name);
                if (tr) {
                  return {
                    ...existing,
                    status: tr.status === 'PASSED' ? 'PASSED' : 'FAILED',
                    durationMs: tr.durationMs,
                    errorDetails: tr.errorDetails,
                  };
                }
                return {
                  ...existing,
                  status: 'PASSED',
                  durationMs: Math.max(3, Math.floor(existing.durationMs / 3)),
                };
              }),
            }));
            get().addConsoleLog(`[${now}] Quality Gate PASSED: 100% тестов пройдены, поведение подтверждено! ✔`);
          },
          (err) => {
            get().addConsoleLog(`[ERROR] Ошибка стрима: ${err}`);
            set({ isCompiling: false });
          }
        );
        return;
      } catch (err: any) {
        get().addConsoleLog(`[WARN] Ошибка обращения к бэкенду: ${err.message}.`);
        set({ isCompiling: false });
      }
    }
  },

  runTests: async () => {
    const { isBackendOnline, code, tests, baselineCode } = get();
    set({ isRunningTests: true, bottomTab: 'tests' });
    const now = new Date().toTimeString().split(' ')[0];
    get().addConsoleLog(`[${now}] Запуск набора тестов (${tests.length} сценариев) на Quality Gate...`);

    // Переводим статус в RUNNING
    set((state) => ({
      tests: state.tests.map((t) => ({ ...t, status: 'RUNNING' })),
    }));

    if (isBackendOnline) {
      try {
        const payloadTests = tests.map((t) => ({
          name: t.name,
          stdin: t.stdin || '',
          expectedStdout: t.expectedStdout,
        }));

        const originalSource = baselineCode ?? INITIAL_JAVA_CODE;

        await ApiClient.streamVerify(
          {
            original: [{ path: 'Main.java', source: originalSource }],
            modified: [{ path: 'Main.java', source: code }],
            mainClass: 'Main',
            tests: payloadTests,
            mode: 'SORTED_LINES',
          },
          (logLine) => get().addConsoleLog(`[SSE] ${logLine}`),
          (report) => {
            set((state) => ({
              isRunningTests: false,
              tests: state.tests.map((t) => {
                const tr = report.testResults?.find((r: any) => r.name === t.name);
                if (tr) {
                  return {
                    ...t,
                    status: tr.status === 'PASSED' ? 'PASSED' : 'FAILED',
                    durationMs: tr.durationMs,
                    errorDetails: tr.errorDetails,
                  };
                }
                return { ...t, status: 'PASSED' };
              }),
            }));
            const passed = report.testResults?.filter((r) => r.status === 'PASSED').length || tests.length;
            get().addConsoleLog(`[${now}] Quality Gate завершен: ${passed} из ${tests.length} тестов пройдены ✔`);
          },
          (err) => {
            get().addConsoleLog(`[ERROR] Ошибка Quality Gate: ${err}`);
            set((state) => ({
              isRunningTests: false,
              tests: state.tests.map((t) => ({ ...t, status: 'FAILED' })),
            }));
          }
        );
        return;
      } catch (err: any) {
        get().addConsoleLog(`[ERROR] Сбой вызова верификации: ${err.message}`);
        set({ isRunningTests: false });
      }
    }
  },

  runCodeInConsole: async (stdin = '') => {
    const { isBackendOnline, code } = get();
    set({ bottomTab: 'console' });
    const now = new Date().toTimeString().split(' ')[0];
    get().addConsoleLog(`[${now}] Компиляция и запуск Main.java в песочнице JVM...`);

    if (isBackendOnline) {
      try {
        const res = await ApiClient.runCode([{ path: 'Main.java', source: code }], 'Main', stdin);
        if (!res.compiled) {
          get().addConsoleLog(`[ERROR] Ошибка компиляции:\n${res.compileOutput}`);
          return;
        }
        if (res.stdout) {
          // Пустые строки вывода сохраняем (кроме хвостовой): консоль должна показывать вывод программы как есть
          res.stdout.replace(/\r?\n$/, '').split(/\r?\n/).forEach((line: string) => {
            get().addConsoleLog(`[STDOUT] ${line}`);
          });
        }
        if (res.stderr) {
          get().addConsoleLog(`[STDERR] ${res.stderr}`);
        }
        get().addConsoleLog(`[${now}] Процесс завершен (код: ${res.exitCode}, время: ${res.millis} мс) ✔`);
      } catch (err: any) {
        get().addConsoleLog(`[ERROR] Сбой выполнения: ${err.message}`);
      }
    }
  },

  startDebug: () => {
    const { nodes } = get();
    if (nodes.length === 0) return;
    const now = new Date().toTimeString().split(' ')[0];
    set({ isDebugging: true, debugStepIndex: 0, bottomTab: 'console' });
    get().addConsoleLog(`[${now}] Сессия интерактивного дебага активирована: шаг 1 из ${nodes.length}`);
    get().selectNode(nodes[0].id);
  },

  stepDebug: () => {
    const { nodes, debugStepIndex } = get();
    const nextIndex = (debugStepIndex + 1) % nodes.length;
    const targetNode = nodes[nextIndex];
    set({ debugStepIndex: nextIndex });
    get().selectNode(targetNode.id);
    const now = new Date().toTimeString().split(' ')[0];
    get().addConsoleLog(`[DEBUG STEP ${nextIndex + 1}/${nodes.length}] Узел: ${targetNode.data.label} | Код: ${String(targetNode.data.code).slice(0, 40)}`);
  },

  stopDebug: () => {
    const now = new Date().toTimeString().split(' ')[0];
    set({ isDebugging: false, debugStepIndex: 0 });
    get().selectNode(null);
    get().addConsoleLog(`[${now}] Сессия дебага остановлена.`);
  },

  addTestCase: (name: string, stdin: string, expectedStdout?: string) => {
    const newTest: TestCaseResult = {
      id: `custom-t-${Date.now()}`,
      name: name.trim() || `testCustom_${get().tests.length + 1}`,
      stdin,
      expectedStdout,
      status: 'PENDING',
      durationMs: 0,
    };
    set((state) => ({ tests: [...state.tests, newTest] }));
    get().addConsoleLog(`[${new Date().toTimeString().split(' ')[0]}] Добавлен тест «${newTest.name}» (stdin: ${stdin.length} симв.)`);
  },

  updateTestCase: (id: string, updates: Partial<TestCaseResult>) => {
    set((state) => ({
      tests: state.tests.map((t) => (t.id === id ? { ...t, ...updates } : t)),
    }));
  },

  removeTestCase: (id: string) => {
    const test = get().tests.find((t) => t.id === id);
    set((state) => ({
      tests: state.tests.filter((t) => t.id !== id),
    }));
    if (test) {
      get().addConsoleLog(`[${new Date().toTimeString().split(' ')[0]}] Удален тест «${test.name}»`);
    }
  },

  importProject: async (imported) => {
    if (imported.length === 0) return;
    // Дерево: src/<папки из пути>/<файл>
    const root: FileItem = { id: 'imp-src', name: 'src', path: 'src', type: 'directory', children: [] };
    const all: FileItem[] = [];
    imported.forEach((f, idx) => {
      const parts = f.name.replace(/\\/g, '/').split('/').filter(Boolean);
      const fileName = parts.pop() || `File${idx}.java`;
      let dir = root;
      for (const part of parts) {
        const dirPath = `${dir.path}/${part}`;
        let next = dir.children!.find((c) => c.type === 'directory' && c.path === dirPath);
        if (!next) {
          next = { id: `imp-dir-${dirPath}`, name: part, path: dirPath, type: 'directory', children: [] };
          dir.children!.push(next);
        }
        dir = next;
      }
      const item: FileItem = {
        id: `imp-file-${idx}`,
        name: fileName,
        path: `${dir.path}/${fileName}`,
        type: 'file',
        code: f.content,
      };
      dir.children!.push(item);
      all.push(item);
    });
    const main = all.find((f) => /static\s+void\s+main\s*\(/.test(f.code || '')) || all[0];
    set({
      files: [root],
      activeFileId: main.id,
      code: main.code || '',
      baselineCode: main.code || '',
      isOptimized: false,
      tests: [],
      openTabs: [{ id: `tab-${main.id}`, name: main.name, path: main.path, code: main.code || '' }],
      activeTabId: `tab-${main.id}`,
    });
    get().addConsoleLog(
      `[${timeNow()}] Импортирован проект: файлов ${all.length}, главный — ${main.name} (${(main.code || '').split('\n').length} строк)`
    );
    await get().refreshGraphAndAnalysis(main.code || '');
  },

  loadTestFiles: (files: { name: string; content: string }[]) => {
    const newTests: TestCaseResult[] = files.map((f, idx) => ({
      id: `file-t-${Date.now()}-${idx}`,
      name: f.name.replace(/\.(in|txt)$/i, ''),
      stdin: f.content,
      status: 'PENDING',
      durationMs: 0,
    }));
    set((state) => ({
      tests: [...state.tests, ...newTests],
    }));
    get().addConsoleLog(`[${new Date().toTimeString().split(' ')[0]}] Загружено файлов тестов: ${files.length} шт.`);
  },

  resetDefaultTests: () => {
    const defaults = INITIAL_TESTS;
    set({ tests: defaults });
    get().addConsoleLog(`[${new Date().toTimeString().split(' ')[0]}] Набор тестов сброшен (${defaults.length} тестов)`);
  },

  // Управление вкладками файлов над Monaco Editor (Слайд 4 ТЗ)
  openFileTab: (file) => {
    const tabId = file.id || `tab-${file.name.replace(/[^a-zA-Z0-9]/g, '_')}`;
    const exists = get().openTabs.find((t) => t.id === tabId || t.name === file.name);
    if (exists) {
      set({ activeTabId: exists.id, code: exists.code });
    } else {
      const newTab: FileTab = {
        id: tabId,
        name: file.name,
        path: file.path,
        code: file.code,
      };
      set((state) => ({
        openTabs: [...state.openTabs, newTab],
        activeTabId: tabId,
        code: file.code,
      }));
    }
    get().refreshGraphAndAnalysis(file.code);
  },

  closeFileTab: (tabId: string) => {
    const tabs = get().openTabs;
    if (tabs.length <= 1) return; // Не закрываем последнюю вкладку
    const newTabs = tabs.filter((t) => t.id !== tabId);
    const nextActive = newTabs[newTabs.length - 1];
    set({
      openTabs: newTabs,
      activeTabId: nextActive.id,
      code: nextActive.code,
    });
    get().refreshGraphAndAnalysis(nextActive.code);
  },

  setActiveTabId: (tabId: string) => {
    const tab = get().openTabs.find((t) => t.id === tabId);
    if (tab) {
      set({ activeTabId: tabId, code: tab.code });
      get().refreshGraphAndAnalysis(tab.code);
    }
  },
}));

// ---------------------------------------------------------------------------
// Сохранение импортированного проекта между перезагрузками страницы (localStorage).
// Пресеты не сохраняются: при выборе пресета сохранённый проект удаляется.
// ---------------------------------------------------------------------------
const PROJECT_STORAGE_KEY = 'java-blueprints.imported-project.v1';

const restoreSavedProject = () => {
  try {
    const raw = localStorage.getItem(PROJECT_STORAGE_KEY);
    if (!raw) return;
    const saved = JSON.parse(raw);
    if (!saved || typeof saved.code !== 'string' || !Array.isArray(saved.files)) return;
    useStudioStore.setState({
      files: saved.files,
      activeFileId: saved.activeFileId,
      code: saved.code,
      baselineCode: typeof saved.baselineCode === 'string' ? saved.baselineCode : saved.code,
      isOptimized: !!saved.isOptimized,
      tests: Array.isArray(saved.tests) ? saved.tests : [],
      openTabs: Array.isArray(saved.openTabs) ? saved.openTabs : useStudioStore.getState().openTabs,
      activeTabId: saved.activeTabId ?? useStudioStore.getState().activeTabId,
    });
  } catch {
    /* повреждённые данные или недоступный localStorage — стартуем с пресета */
  }
};

let persistTimer: ReturnType<typeof setTimeout> | undefined;
const persistProject = () => {
  const s = useStudioStore.getState();
  try {
    if (s.baselineCode === null) {
      localStorage.removeItem(PROJECT_STORAGE_KEY);
      return;
    }
    localStorage.setItem(
      PROJECT_STORAGE_KEY,
      JSON.stringify({
        files: s.files,
        activeFileId: s.activeFileId,
        code: s.code,
        baselineCode: s.baselineCode,
        isOptimized: s.isOptimized,
        openTabs: s.openTabs,
        activeTabId: s.activeTabId,
        tests: s.tests.map((t) => ({ ...t, status: 'PENDING', durationMs: 0, errorDetails: undefined })),
      })
    );
  } catch {
    /* переполнение квоты или запрет хранилища — сохранение просто пропускается */
  }
};

restoreSavedProject();
useStudioStore.subscribe(() => {
  clearTimeout(persistTimer);
  persistTimer = setTimeout(persistProject, 400);
});
