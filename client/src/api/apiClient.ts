import { VerificationReport } from '../types/studio';

export interface ProjectFileDto {
  path: string;
  source: string;
}

export interface NodeDataDto {
  label: string;
  code: string;
  lineStart: number;
  lineEnd: number;
  columnStart?: number;
  columnEnd?: number;
  hasIssue: boolean;
  issueId?: string;
  issueIds?: string[];
  loopType?: string;
  condition?: string;
  header?: string;
}

export interface GraphNodeDto {
  id: string;
  parentId?: string;
  type: string;
  data: NodeDataDto;
}

export interface GraphEdgeDto {
  id: string;
  source: string;
  target: string;
  type: string;
  label?: string;
}

export interface MethodGraphResponseDto {
  className: string;
  methodId: string;
  methodSignature: string;
  fileName: string;
  lineStart: number;
  lineEnd: number;
  nodes: GraphNodeDto[];
  edges: GraphEdgeDto[];
}

export interface ComplexityMetricDto {
  currentComplexity: string;
  predictedComplexity: string;
  expectedSpeedup?: string;
  issuesCount: number;
  cyclomaticComplexity: number;
  linesOfCode: number;
  methodCount: number;
}

export interface GraphResponseDto {
  methods: MethodGraphResponseDto[];
  metrics: ComplexityMetricDto;
}

export interface CodeIssueDto {
  id: string;
  nodeId: string;
  type: string;
  title: string;
  severity: 'HIGH' | 'MEDIUM' | 'LOW';
  lineStart: number;
  lineEnd: number;
  fileName: string;
  description: string;
  risk: string;
  recommendation: string;
  suggestedCode?: string;
  complexityBefore?: string;
  complexityAfter?: string;
}

export interface AnalysisReportDto {
  issues: CodeIssueDto[];
  metrics: ComplexityMetricDto;
}

export interface TestCaseDto {
  name: string;
  stdin: string;
  expectedStdout?: string | null;
}

export interface VerifyRequestDto {
  original: ProjectFileDto[];
  modified: ProjectFileDto[];
  mainClass?: string;
  tests: TestCaseDto[];
  mode: 'EXACT' | 'SORTED_LINES' | 'MASKED';
}

export type MutateAction = 'UPDATE_CODE' | 'DELETE_NODE' | 'INSERT_NODE' | 'REORDER';

export interface MutateRequestDto {
  files: ProjectFileDto[];
  action: MutateAction;
  nodeId?: string;
  /** Контейнер для INSERT_NODE / REORDER: `<methodId>/entry` или `<methodId>/s<путь>:<слот>` */
  parentId?: string;
  /** Позиция в контейнере, с 0 */
  index?: number;
  payload?: string;
  template?: string;
  validate?: boolean;
}

export interface MutateResponseDto {
  files: ProjectFileDto[];
  graph: GraphResponseDto;
  validated: boolean;
  compileOutput: string;
}

/** Ошибка API с кодом сервера: COMPILE_ERROR, BAD_REQUEST, PARSE_ERROR... */
export class ApiError extends Error {
  constructor(message: string, public code?: string, public status?: number) {
    super(message);
  }
}

const API_BASE = '/api';

export const ApiClient = {
  /** Проверка доступности бэкенда */
  async checkHealth(): Promise<boolean> {
    try {
      const res = await fetch(`${API_BASE}/health`, {
        method: 'GET',
        headers: { Accept: 'application/json' },
      }).catch(() => null);
      if (res && res.ok) return true;

      const fallbackRes = await fetch(`${API_BASE}/analysis`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ files: [] }),
      });
      return fallbackRes.status === 200 || fallbackRes.status === 422 || fallbackRes.status === 400;
    } catch {
      return false;
    }
  },

  /** Запрос построения графа */
  async fetchGraph(files: ProjectFileDto[], methodId?: string): Promise<GraphResponseDto> {
    const res = await fetch(`${API_BASE}/graph`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ files, methodId }),
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({ message: res.statusText }));
      throw new Error(err.message || 'Ошибка парсинга графа');
    }
    return res.json();
  },

  /** Запрос статического анализа */
  async fetchAnalysis(files: ProjectFileDto[]): Promise<AnalysisReportDto> {
    const res = await fetch(`${API_BASE}/analysis`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ files }),
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({ message: res.statusText }));
      throw new Error(err.message || 'Ошибка статического анализа');
    }
    return res.json();
  },

  /** Мутация графа */
  /**
   * Правка графа. Сервер ничего не хранит: при ошибке (400 COMPILE_ERROR / BAD_REQUEST) исходный код не изменён.
   * validate по умолчанию true — результат проверяется компиляцией.
   */
  async mutateGraph(req: MutateRequestDto): Promise<MutateResponseDto> {
    const res = await fetch(`${API_BASE}/graph/mutate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ validate: true, ...req }),
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({ message: res.statusText }));
      throw new ApiError(err.message || 'Ошибка мутации AST', err.code, res.status);
    }
    return res.json();
  },

  /** Запуск авто-оптимизации */
  async optimizeCode(files: ProjectFileDto[], issueId?: string) {
    const res = await fetch(`${API_BASE}/optimize`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ files, issueId: issueId || null, allowLlm: false }),
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({ message: res.statusText }));
      throw new Error(err.message || 'Ошибка оптимизации кода');
    }
    return res.json();
  },

  /** Прямой запуск скомпилированного кода в консоли песочницы */
  async runCode(files: ProjectFileDto[], mainClass = 'Main', stdin = '') {
    const res = await fetch(`${API_BASE}/run`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ files, mainClass, stdin }),
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({ message: res.statusText }));
      throw new Error(err.message || 'Ошибка выполнения в песочнице');
    }
    return res.json();
  },

  /**
   * Интерактивный запуск: вывод приходит по SSE по мере появления, ввод подаётся отдельными запросами (sendStdin).
   * Промис завершается, когда поток закрыт.
   */
  async runSession(
    files: ProjectFileDto[],
    mainClass: string,
    h: {
      onSession: (id: string) => void;
      onOut: (text: string) => void;
      onErr: (text: string) => void;
      onCompileError: (output: string) => void;
      onExit: (info: { exitCode: number; millis: number; reason: string | null }) => void;
      onError: (message: string) => void;
    },
    signal?: AbortSignal
  ) {
    try {
      const response = await fetch(`${API_BASE}/run/session`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
        body: JSON.stringify({ files, mainClass }),
        signal,
      });
      if (!response.ok || !response.body) {
        const err = await response.json().catch(() => ({ message: `HTTP ${response.status}` }));
        throw new Error(err.message || `HTTP ${response.status}`);
      }
      const reader = response.body.getReader();
      const decoder = new TextDecoder('utf-8');
      let buffer = '';
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const parts = buffer.split(/\r?\n\r?\n/);
        buffer = parts.pop() || '';
        for (const part of parts) {
          let eventName = 'message';
          let dataStr = '';
          for (const line of part.split(/\r?\n/)) {
            if (line.startsWith('event:')) eventName = line.substring(6).trim();
            else if (line.startsWith('data:')) dataStr += line.substring(5).replace(/^ /, '');
          }
          if (eventName === 'session') h.onSession(dataStr.trim());
          else if (eventName === 'out') h.onOut(JSON.parse(dataStr));
          else if (eventName === 'err') h.onErr(JSON.parse(dataStr));
          else if (eventName === 'compile-error') h.onCompileError(JSON.parse(dataStr));
          else if (eventName === 'exit') h.onExit(JSON.parse(dataStr));
          else if (eventName === 'error') h.onError(JSON.parse(dataStr));
        }
      }
    } catch (err: any) {
      if (err?.name !== 'AbortError') h.onError(err?.message || 'Сбой интерактивного запуска');
    }
  },

  /** Ввод в запущенную программу; eof=true закрывает stdin (Ctrl+D). */
  async sendStdin(sessionId: string, text: string, eof = false) {
    const res = await fetch(`${API_BASE}/run/session/${encodeURIComponent(sessionId)}/stdin`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text, eof }),
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({ message: res.statusText }));
      throw new Error(err.message || 'Не удалось передать ввод');
    }
  },

  async stopSession(sessionId: string) {
    await fetch(`${API_BASE}/run/session/${encodeURIComponent(sessionId)}`, { method: 'DELETE' }).catch(() => undefined);
  },

  /** Запуск авто-декомпозиции на 4 класса */
  async decomposeMonolith(files: ProjectFileDto[]) {
    const res = await fetch(`${API_BASE}/decompose`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ files, extractClasses: true, splitMethods: true }),
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({ message: res.statusText }));
      throw new Error(err.message || 'Ошибка декомпозиции');
    }
    return res.json();
  },

  /** Потоковая верификация через SSE */
  async streamVerify(
    req: VerifyRequestDto,
    onLog: (line: string) => void,
    onComplete: (report: VerificationReport) => void,
    onError: (err: string) => void
  ) {
    try {
      const response = await fetch(`${API_BASE}/verify/stream`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
        },
        body: JSON.stringify(req),
      });

      if (!response.ok || !response.body) {
        throw new Error(`Ошибка подключения к SSE стриму: HTTP ${response.status}`);
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder('utf-8');
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const parts = buffer.split('\n\n');
        buffer = parts.pop() || '';

        for (const part of parts) {
          const lines = part.split('\n');
          let eventName = 'message';
          let dataStr = '';

          for (const line of lines) {
            if (line.startsWith('event:')) {
              eventName = line.substring(6).trim();
            } else if (line.startsWith('data:')) {
              dataStr += line.substring(5).trim();
            }
          }

          if (eventName === 'log') {
            onLog(dataStr);
          } else if (eventName === 'complete') {
            try {
              const report = JSON.parse(dataStr);
              onComplete(report);
            } catch {
              onLog(`[SUCCESS] Отчет получен: ${dataStr}`);
            }
          } else if (eventName === 'error') {
            onError(dataStr);
          }
        }
      }
    } catch (err: any) {
      onError(err.message || 'Сбой потока верификации');
    }
  },
};
