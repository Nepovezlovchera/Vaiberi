export type NodeType =
  | 'method'     // Заголовок метода
  | 'var'        // Объявление / присваивание переменной
  | 'condition'  // Условие if/else (ромб с Да/Нет)
  | 'loop'       // Цикл (for, foreach, while) с телом
  | 'op'         // Выражение / операция
  | 'call'       // Вызов внешнего сервиса/метода
  | 'return'     // Возврат значения (терминал)
  | 'try'        // try / catch / finally
  | 'switch'     // switch с ветками
  | 'break'      // break
  | 'continue'   // continue
  | 'throw'      // throw
  | 'raw';       // Произвольный / необработанный блок

export type EdgeType = 'next' | 'true' | 'false' | 'body' | 'catch' | 'finally';

export interface GraphNodeData {
  label?: string;
  code: string;
  /** Однострочный текст для отображения на узле (у живого графа задаёт graphMapper; code — исходник целиком, с телом и комментариями) */
  text?: string;
  lineStart: number;
  lineEnd: number;
  hasIssue?: boolean;
  issueId?: string;
  /** Все проблемы узла (id из CodeIssue) */
  issueIds?: string[];
  /** Заголовок первой проблемы узла (для значка на узле), задаёт graphMapper */
  issueTitle?: string;
  condition?: string;
  loopType?: 'for' | 'foreach' | 'while' | 'do';
  complexityBadge?: string;
  [key: string]: unknown;
}

export interface FileItem {
  id: string;
  name: string;
  path: string;
  type: 'file' | 'directory';
  children?: FileItem[];
  code?: string;
  hasIssues?: boolean;
}

export type IssueSeverity = 'HIGH' | 'MEDIUM' | 'LOW';

export interface CodeIssue {
  id: string;
  nodeId: string;
  title: string;
  severity: IssueSeverity;
  lineStart: number;
  lineEnd: number;
  fileName: string;
  description: string;
  risk: string;
  recommendation: string;
  suggestedCode?: string;
  /** NESTED_LOOP | COSTLY_CALL_IN_LOOP | LONG_METHOD | HIGH_CYCLOMATIC | DUPLICATION (приходит от бэкенда) */
  type?: string;
  complexityBefore?: string;
  complexityAfter?: string;
}

/** Состояние связи редактор/граф/анализ с бэкендом */
export type SyncStatus = 'idle' | 'syncing' | 'ok' | 'error' | 'offline';

export interface ComplexityMetrics {
  current: string;
  predicted: string;
  speedup: string;
  cyclomatic: number;
  linesOfCode: number;
  methodsCount: number;
}

export interface TestCaseResult {
  id: string;
  name: string;
  stdin: string;
  expectedStdout?: string;
  status: 'PASSED' | 'FAILED' | 'RUNNING' | 'PENDING';
  durationMs: number;
  expectedOutput?: string;
  actualOutput?: string;
  errorDetails?: string;
}

export type TestCaseItem = TestCaseResult;

export interface MethodInfo {
  methodId: string;
  signature: string;
  lineStart: number;
  lineEnd: number;
}

export interface VerificationReport {
  allPassed: boolean;
  behaviorMatched: boolean;
  originalTimeMs: number;
  optimizedTimeMs: number;
  speedup: string;
  complexityBefore: string;
  testResults: TestCaseResult[];
  passed?: number;
  total?: number;
}

export interface FileTab {
  id: string;
  name: string;
  path: string;
  code: string;
}

