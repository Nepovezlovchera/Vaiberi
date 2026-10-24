import { Node, Edge } from '@xyflow/react';
import {
  MethodGraphResponseDto,
  GraphEdgeDto,
  CodeIssueDto,
  ComplexityMetricDto,
} from '../api/apiClient';
import { CodeIssue, ComplexityMetrics, MethodInfo } from '../types/studio';
import { getLayoutedElements } from '../components/canvas/layout';

const BIG_O_TYPES = new Set(['NESTED_LOOP', 'COSTLY_CALL_IN_LOOP']);

const JUMP_TYPES = new Set(['return', 'throw', 'break', 'continue']);
const UNREACHABLE_STYLE = { stroke: '#52525b', strokeWidth: 1.5, strokeDasharray: '4 4' };

const EDGE_STYLE: Record<string, { stroke: string; strokeWidth: number; strokeDasharray?: string }> = {
  true: { stroke: '#10b981', strokeWidth: 2 },
  false: { stroke: '#ef4444', strokeWidth: 2 },
  body: { stroke: '#3b82f6', strokeWidth: 2 },
  catch: { stroke: '#f59e0b', strokeWidth: 2 },
  finally: { stroke: '#a855f7', strokeWidth: 2 },
  back: { stroke: '#3b82f6', strokeWidth: 1.5, strokeDasharray: '6 4' },
};

/** Первая содержательная строка кода (для узлов, у которых бэкенд не дал label, например блок `{ ... }` в arrow-case). */
function firstCodeLine(code: string): string | undefined {
  const line = code
    .split(/\r?\n/)
    .map((l) => l.trim())
    .find((l) => l && l !== '{' && l !== '}' && !l.startsWith('//'));
  if (!line) return undefined;
  return line.length > 120 ? line.slice(0, 117) + '...' : line;
}

export function toMethodInfos(methods: MethodGraphResponseDto[]): MethodInfo[] {
  return methods.map((m) => ({
    methodId: m.methodId,
    // У методов без модификатора доступа сигнатура приходит с ведущим пробелом
    signature: m.methodSignature.trim(),
    lineStart: m.lineStart,
    lineEnd: m.lineEnd,
  }));
}

export function toIssues(dtos: CodeIssueDto[]): CodeIssue[] {
  return dtos.map((d) => ({
    id: d.id,
    nodeId: d.nodeId,
    title: d.title,
    severity: d.severity,
    lineStart: d.lineStart,
    lineEnd: d.lineEnd,
    fileName: d.fileName,
    description: d.description,
    risk: d.risk,
    recommendation: d.recommendation,
    suggestedCode: d.suggestedCode ?? undefined,
    type: d.type,
    complexityBefore: d.complexityBefore ?? undefined,
    complexityAfter: d.complexityAfter ?? undefined,
  }));
}

/** Ускорение известно только после замера Quality Gate, поэтому без expectedSpeedup показываем прочерк. */
export function toMetrics(dto: ComplexityMetricDto): ComplexityMetrics {
  return {
    current: dto.currentComplexity,
    predicted: dto.predictedComplexity,
    speedup: dto.expectedSpeedup ?? '—',
    cyclomatic: dto.cyclomaticComplexity,
    linesOfCode: dto.linesOfCode,
    methodsCount: dto.methodCount,
  };
}

/** Метод, которому принадлежит узел (ID узла имеет вид `<methodId>/entry` или `<methodId>/s<путь>`). */
export function methodOfNode(methods: MethodInfo[], nodeId: string): string | undefined {
  return methods.find((m) => nodeId.startsWith(m.methodId + '/'))?.methodId;
}

/**
 * Метод, который показать первым: с самой тяжёлой Big-O проблемой, иначе с любой проблемой,
 * иначе main, иначе первый.
 */
export function pickInitialMethod(methods: MethodInfo[], issues: CodeIssue[]): string | null {
  if (methods.length === 0) return null;
  const byIssue = (pred: (i: CodeIssue) => boolean) => {
    const hit = issues.find(pred);
    return hit ? methodOfNode(methods, hit.nodeId) : undefined;
  };
  return (
    byIssue((i) => i.severity === 'HIGH' && BIG_O_TYPES.has(i.type ?? '')) ??
    byIssue((i) => BIG_O_TYPES.has(i.type ?? '')) ??
    byIssue(() => true) ??
    methods.find((m) => m.methodId.endsWith('.main'))?.methodId ??
    methods[0].methodId
  );
}

function sourceHandle(sourceType: string, edge: GraphEdgeDto, conditionHasElse: boolean): string | undefined {
  switch (sourceType) {
    case 'condition':
      if (edge.type === 'true' || edge.type === 'false') return edge.type;
      // if без else: продолжение потока и есть ветка «Нет»
      return conditionHasElse ? 'next' : 'false';
    case 'loop':
      // Без явного id ReactFlow взял бы первый source-handle узла, а у цикла это «body»
      return edge.type === 'body' ? 'body' : 'next';
    case 'try':
    case 'switch':
      // back («Повтор») идёт из последнего оператора тела цикла: выходим той же ручкой, что и next
      return edge.type === 'next' || edge.type === 'back' ? 'next' : edge.type;
    default:
      return undefined;
  }
}

/** Граф одного метода от бэкенда → узлы и рёбра ReactFlow с раскладкой Dagre. */
export function methodToFlow(method: MethodGraphResponseDto, issues: CodeIssue[]): { nodes: Node[]; edges: Edge[] } {
  const issueById = new Map(issues.map((i) => [i.id, i]));
  const typeById = new Map(method.nodes.map((n) => [n.id, n.type]));
  const withElse = new Set(method.edges.filter((e) => e.type === 'false').map((e) => e.source));

  const nodes: Node[] = method.nodes.map((n) => {
    const ids = n.data.issueIds?.length ? n.data.issueIds : n.data.issueId ? [n.data.issueId] : [];
    const nodeIssues = ids.map((id) => issueById.get(id)).filter((i): i is CodeIssue => Boolean(i));
    const bigO = nodeIssues.find((i) => BIG_O_TYPES.has(i.type ?? ''));
    return {
      id: n.id,
      type: n.type,
      position: { x: 0, y: 0 },
      data: {
        ...n.data,
        // label — первая строка оператора без комментариев; header у составных — заголовок без тела
        text: ((n.type === 'loop' ? n.data.header : undefined) || n.data.label)?.trim() || firstCodeLine(n.data.code),
        issueIds: ids,
        issueId: ids[0],
        hasIssue: n.data.hasIssue || nodeIssues.length > 0,
        issueTitle: nodeIssues[0]?.title,
        complexityBadge: n.type === 'loop' && bigO ? bigO.complexityBefore : undefined,
      },
    };
  });

  const edges: Edge[] = method.edges.map((e) => {
    const srcType = typeById.get(e.source) ?? '';
    const handle = sourceHandle(srcType, e, withElse.has(e.source));
    const isImplicitFalse = srcType === 'condition' && handle === 'false' && e.type === 'next';
    const kind = isImplicitFalse ? 'false' : e.type;
    // После return/throw/break/continue поток не идёт: следующий оператор блока недостижим
    const unreachable = e.type === 'next' && JUMP_TYPES.has(srcType);
    // Бэкенд подписывает ветки switch текстом case («case 1», «default»); служебное «body» заменяем
    const bodyLabel = srcType === 'switch' ? 'case' : srcType === 'try' ? 'try' : undefined;
    const serverLabel = e.label && e.label !== 'body' ? e.label : undefined;
    const label = isImplicitFalse
      ? 'Нет'
      : unreachable
        ? 'недостижимо'
        : serverLabel ?? (kind === 'body' ? bodyLabel : undefined);
    return {
      id: e.id,
      source: e.source,
      target: e.target,
      sourceHandle: handle,
      type: 'smoothstep',
      label,
      animated: kind === 'true',
      style: unreachable ? UNREACHABLE_STYLE : EDGE_STYLE[kind],
      data: { kind },
    };
  });

  return getLayoutedElements(nodes, edges);
}
