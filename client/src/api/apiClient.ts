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
  mainClass: string;
  tests: TestCaseDto[];
  mode: 'EXACT' | 'SORTED_LINES' | 'MASKED';
}

const API_BASE = '/api';

export const ApiClient = {
  /** Проверка доступности бэкенда */
  async checkHealth(): Promise<boolean> {
    try {
      const res = await fetch(`${API_BASE}/analysis`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ files: [] }),
      });
      return res.status === 200 || res.status === 422 || res.status === 400;
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
  async mutateGraph(
    files: ProjectFileDto[],
    action: 'UPDATE_CODE' | 'DELETE_NODE' | 'INSERT_NODE' | 'REORDER',
    nodeId: string,
    payload?: string
  ) {
    const res = await fetch(`${API_BASE}/graph/mutate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ files, action, nodeId, payload, validate: true }),
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({ message: res.statusText }));
      throw new Error(err.message || 'Ошибка мутации AST');
    }
    return res.json();
  },

  /** Запуск авто-оптимизации */
  async optimizeCode(files: ProjectFileDto[], issueId?: string) {
    const res = await fetch(`${API_BASE}/optimize`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ files, issueId: issueId || 'iss-nested-complexity', allowLlm: true }),
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({ message: res.statusText }));
      throw new Error(err.message || 'Ошибка оптимизации кода');
    }
    return res.json();
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
