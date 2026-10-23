import { create } from 'zustand';
import { Node, Edge } from '@xyflow/react';
import { FileItem, CodeIssue, ComplexityMetrics, TestCaseResult } from '../types/studio';
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

interface StudioState {
  // Файлы
  files: FileItem[];
  activeFileId: string;
  code: string;

  // Граф
  nodes: Node[];
  edges: Edge[];
  selectedNodeId: string | null;

  // Подсветка в редакторе
  activeLineRange: { start: number; end: number } | null;

  // Проблемы и метрики
  issues: CodeIssue[];
  selectedIssueId: string | null;
  metrics: ComplexityMetrics;
  isOptimized: boolean;

  // Табы интерфейса
  bottomTab: 'code' | 'console' | 'tests';
  rightTab: 'issues' | 'recommendations' | 'metrics';

  // Консоль и тесты
  consoleLogs: string[];
  tests: TestCaseResult[];
  isRunningTests: boolean;
  isCompiling: boolean;

  // Экшены
  selectFile: (fileId: string) => void;
  setCode: (code: string) => void;
  setNodes: (nodes: Node[] | ((prev: Node[]) => Node[])) => void;
  setEdges: (edges: Edge[] | ((prev: Edge[]) => Edge[])) => void;
  selectNode: (nodeId: string | null) => void;
  selectIssue: (issueId: string) => void;
  setBottomTab: (tab: 'code' | 'console' | 'tests') => void;
  setRightTab: (tab: 'issues' | 'recommendations' | 'metrics') => void;
  runOptimization: () => void;
  runTests: () => void;
  addConsoleLog: (msg: string) => void;
  addCustomNode: (type: string, position: { x: number; y: number }) => void;
}

export const useStudioStore = create<StudioState>((set, get) => ({
  files: INITIAL_FILES,
  activeFileId: 'file-main',
  code: INITIAL_JAVA_CODE,

  nodes: INITIAL_NODES,
  edges: INITIAL_EDGES,
  selectedNodeId: null,

  activeLineRange: null,

  issues: INITIAL_ISSUES,
  selectedIssueId: null,
  metrics: INITIAL_METRICS,
  isOptimized: false,

  bottomTab: 'code',
  rightTab: 'issues',

  consoleLogs: [
    '[18:24:10] Начата компиляция проекта Main.java...',
    '[18:24:11] Компиляция успешна ✔ (Java 21 bytecode generated)',
    '[18:24:11] Запуск программы в песочнице (-Xmx256m, timeout 3000ms)...',
    '[18:24:12] Обработка данных (1523 элементов)...',
    '[18:24:12] Готово. Время выполнения оригинальной версии: 1.24 сек.',
    '[18:24:12] Детектор дефектов: обнаружен вложенный цикл O(n²) на строках 31-37',
  ],
  tests: INITIAL_TESTS,
  isRunningTests: false,
  isCompiling: false,

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
      set({ activeFileId: fileId, code: file.code });
    }
  },

  setCode: (code: string) => {
    set({ code });
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
      set({
        selectedIssueId: issueId,
        selectedNodeId: issue.nodeId,
        activeLineRange: { start: issue.lineStart, end: issue.lineEnd },
        bottomTab: 'code',
      });
    }
  },

  setBottomTab: (tab) => set({ bottomTab: tab }),
  setRightTab: (tab) => set({ rightTab: tab }),

  addConsoleLog: (msg: string) => {
    set((state) => ({ consoleLogs: [...state.consoleLogs, msg] }));
  },

  runOptimization: () => {
    set({ isCompiling: true, bottomTab: 'console' });
    const now = new Date().toTimeString().split(' ')[0];

    get().addConsoleLog(`[${now}] Запуск автоматической оптимизации алгоритма...`);
    get().addConsoleLog(`[${now}] Преобразование AST: вложенный поиск O(n²) -> предвыборка в HashMap`);
    get().addConsoleLog(`[${now}] Проверка компиляции Java 21...`);

    setTimeout(() => {
      // Обновляем код, граф и метрики
      set((state) => ({
        code: OPTIMIZED_JAVA_CODE,
        isOptimized: true,
        metrics: {
          ...state.metrics,
          current: 'O(n)',
          predicted: 'O(n)',
          speedup: '5.2x (Подтверждено)',
        },
        issues: state.issues.filter((i) => i.id !== 'iss-nested-complexity'),
        nodes: state.nodes.map((n) => {
          if (n.id === 'node-loop-nested') {
            return {
              ...n,
              data: {
                ...n.data,
                label: 'Оптимизированный поиск O(n)',
                code: 'Map<String, Order> map = processedById.get(a.getId())',
                hasIssue: false,
                complexityBadge: 'O(n)',
              },
            };
          }
          return n;
        }),
      }));

      get().addConsoleLog(`[${now}] Компиляция успешна ✔`);
      get().addConsoleLog(`[${now}] Запуск Quality Gate: дифференциальное тестирование поведения...`);

      // Запуск тестов со сверхбыстрыми таймингами
      setTimeout(() => {
        set((state) => ({
          isCompiling: false,
          isRunningTests: false,
          tests: state.tests.map((t) => ({
            ...t,
            durationMs: t.id === 't4' ? 238 : Math.max(2, Math.floor(t.durationMs / 3)),
          })),
        }));
        get().addConsoleLog(`[${now}] 12 из 12 тестов успешно пройдены ✔`);
        get().addConsoleLog(`[${now}] Сравнение stdout/stderr оригинальной и оптимизированной версий...`);
        get().addConsoleLog(`[${now}] Smart Match (Sorted Lines): ПОВЕДЕНИЕ ПОЛНОСТЬЮ СОВПАДАЕТ ✔`);
        get().addConsoleLog(`[${now}] Зафиксировано ускорение: 5.2x (с 1240 мс до 238 мс)`);
        get().addConsoleLog(`[${now}] Артефакт verification_report.json успешно сохранен в корне`);
      }, 700);
    }, 600);
  },

  runTests: () => {
    set({ isRunningTests: true, bottomTab: 'tests' });
    const now = new Date().toTimeString().split(' ')[0];
    get().addConsoleLog(`[${now}] Запущен повторный прогон набора автотестов (Quality Gate)...`);

    setTimeout(() => {
      set({ isRunningTests: false });
      get().addConsoleLog(`[${now}] Все 12 тестов завершены: 100% PASS`);
    }, 800);
  },

  addCustomNode: (type: string, position: { x: number; y: number }) => {
    const id = `node-${Date.now()}`;
    const newNode: Node = {
      id,
      type: type as any,
      position,
      data: {
        label: type === 'condition' ? 'Условие' : type === 'loop' ? 'Цикл' : type === 'var' ? 'Переменная' : 'Операция',
        code: type === 'condition' ? 'if (condition)' : type === 'var' ? 'int x = 0;' : 'doSomething()',
        lineStart: 1,
        lineEnd: 1,
      },
    };
    set((state) => ({ nodes: [...state.nodes, newNode], selectedNodeId: id }));
  },
}));
