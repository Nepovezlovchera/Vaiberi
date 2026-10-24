import React, { useState } from 'react';
import { flattenFiles, sourcePath } from '../../store/projectFiles';
import { useStudioStore } from '../../store/useStudioStore';
import {
  AlertTriangle,
  AlertCircle,
  TrendingUp,
  Sparkles,
  Activity,
  FileText,
  CheckCircle2,
  GitCompare,
  ChevronDown,
  ChevronUp,
} from 'lucide-react';

const SEVERITY_LABEL = { HIGH: 'Высокая', MEDIUM: 'Средняя', LOW: 'Низкая' } as const;

export const AnalysisPanel: React.FC = () => {
  const [expandedDiffs, setExpandedDiffs] = useState<Record<string, boolean>>({});

  const toggleDiff = (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    setExpandedDiffs((prev) => ({ ...prev, [id]: !prev[id] }));
  };

  const {
    files,
    issues,
    metrics,
    selectedIssueId,
    selectIssue,
    rightTab,
    setRightTab,
    isOptimized,
    runOptimization,
    optimizeSingleIssue,
    isCompiling,
    analysisLoaded,
    syncStatus,
    syncError,
  } = useStudioStore();

  // Уникальные рекомендации: заголовок первой проблемы, использующей рекомендацию
  const recommendations = React.useMemo(() => {
    const map = new Map<string, (typeof issues)[number]>();
    issues.forEach((i) => {
      if (i.recommendation && !map.has(i.recommendation)) map.set(i.recommendation, i);
    });
    return Array.from(map.entries());
  }, [issues]);

  return (
    <div className="w-80 h-full bg-surface border-l border-border flex flex-col overflow-hidden select-none">
      {/* Header Tabs */}
      <div className="flex items-center bg-surface-elevated border-b border-border h-9 shrink-0 px-2 gap-1">
        <button
          onClick={() => setRightTab('issues')}
          className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-t transition border-b-2 ${
            rightTab === 'issues'
              ? 'text-zinc-100 border-[#f37022] bg-surface font-semibold'
              : 'text-zinc-400 border-transparent hover:text-zinc-200'
          }`}
        >
          <AlertCircle className="w-3.5 h-3.5 text-rose-400" />
          <span>Проблемы ({issues.length})</span>
        </button>

        <button
          onClick={() => setRightTab('recommendations')}
          className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-t transition border-b-2 ${
            rightTab === 'recommendations'
              ? 'text-zinc-100 border-[#f37022] bg-surface font-semibold'
              : 'text-zinc-400 border-transparent hover:text-zinc-200'
          }`}
        >
          <Sparkles className="w-3.5 h-3.5 text-amber-400" />
          <span>Советы ({recommendations.length})</span>
        </button>

        <button
          onClick={() => setRightTab('metrics')}
          className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-t transition border-b-2 ${
            rightTab === 'metrics'
              ? 'text-zinc-100 border-[#f37022] bg-surface font-semibold'
              : 'text-zinc-400 border-transparent hover:text-zinc-200'
          }`}
        >
          <Activity className="w-3.5 h-3.5 text-emerald-400" />
          <span>Метрики</span>
        </button>
      </div>

      {/* Content Area */}
      <div className="flex-1 overflow-y-auto p-3 space-y-3">
        {rightTab === 'issues' && (
          <div className="space-y-2.5">
            {issues.map((issue) => {
              const isSelected = selectedIssueId === issue.id;
              const isHigh = issue.severity === 'HIGH';

              return (
                <div
                  key={issue.id}
                  onClick={() => selectIssue(issue.id)}
                  className={`p-3 rounded-lg border text-xs cursor-pointer transition-all ${
                    isSelected
                      ? 'border-[#f37022] bg-[#f37022]/10 shadow-md ring-1 ring-[#f37022]/30'
                      : 'border-border bg-surface-elevated/40 hover:border-zinc-500 hover:bg-surface-elevated/80'
                  }`}
                >
                  <div className="flex items-center justify-between mb-1.5">
                    <div className="flex items-center gap-1.5 font-semibold text-zinc-100">
                      {isHigh ? (
                        <AlertTriangle className="w-3.5 h-3.5 text-rose-400 shrink-0" />
                      ) : (
                        <AlertCircle className="w-3.5 h-3.5 text-amber-400 shrink-0" />
                      )}
                      <span>{issue.title}</span>
                    </div>

                    <div className="flex items-center gap-1.5">
                      <span
                        className={`px-1.5 py-0.5 rounded text-[10px] font-bold ${
                          isHigh
                            ? 'bg-rose-950/80 text-rose-400 border border-rose-600/40'
                            : issue.severity === 'LOW'
                              ? 'bg-zinc-800/80 text-zinc-300 border border-zinc-600/40'
                              : 'bg-amber-950/80 text-amber-400 border border-amber-600/40'
                        }`}
                      >
                        {SEVERITY_LABEL[issue.severity] ?? issue.severity}
                      </span>
                    </div>
                  </div>

                  <div className="flex items-center gap-2 mb-1.5 text-[10px] text-zinc-500 font-mono">
                    <span>
                      {issue.fileName}:{issue.lineStart}
                    </span>
                    {issue.complexityBefore && (
                      <span className="px-1.5 py-0.5 rounded bg-zinc-900 border border-border text-zinc-300">
                        {issue.complexityBefore}
                        {issue.complexityAfter ? ` → ${issue.complexityAfter}` : ''}
                      </span>
                    )}
                  </div>

                  <p className="text-[11px] text-zinc-400 leading-relaxed mb-2">{issue.description}</p>

                  <div className="pt-2 border-t border-border/40 text-[10px] text-zinc-400 flex items-start gap-1 mb-2">
                    <span className="text-zinc-500 font-semibold shrink-0">Решение:</span>
                    <span className="text-[#f37022] dark:text-[#ff7b29] font-medium">{issue.recommendation}</span>
                  </div>

                  {/* Diff Preview Accordion (Слайд 5 ТЗ) */}
                  <div className="pt-1.5 border-t border-border/30 mb-2">
                    <button
                      onClick={(e) => toggleDiff(issue.id, e)}
                      className="flex items-center justify-between w-full text-[10px] text-zinc-400 hover:text-zinc-200 transition py-0.5"
                    >
                      <span className="flex items-center gap-1 font-medium text-[#f37022] dark:text-[#ff7b29]">
                        <GitCompare className="w-3 h-3" />
                        <span>Сравнить код (Diff Preview)</span>
                      </span>
                      {expandedDiffs[issue.id] ? (
                        <ChevronUp className="w-3 h-3 text-zinc-500" />
                      ) : (
                        <ChevronDown className="w-3 h-3 text-zinc-500" />
                      )}
                    </button>

                    {expandedDiffs[issue.id] && (
                      <div className="mt-1.5 p-2 bg-surface rounded border border-border space-y-1.5">
                        <div>
                          <div className="text-[9px] font-sans font-semibold text-rose-400 uppercase tracking-wider mb-0.5">
                            Было:
                          </div>
                          <pre className="p-1.5 bg-rose-950/20 border border-rose-900/40 rounded text-rose-300 font-mono text-[10px] whitespace-pre-wrap leading-tight">
                            {(() => {
                              const source = flattenFiles(files).find(f => sourcePath(f.path) === issue.fileName)?.code;
                              const method = useStudioStore.getState().methodsList.find(m => issue.nodeId.startsWith(m.methodId + '/'));
                              return source?.split(/\r?\n/).slice((method?.lineStart ?? issue.lineStart) - 1, method?.lineEnd ?? issue.lineEnd).join('\n') ?? 'Исходный фрагмент недоступен';
                            })()}
                          </pre>
                        </div>

                        <div>
                          <div className="text-[9px] font-sans font-semibold text-emerald-400 uppercase tracking-wider mb-0.5">
                            Предложение (до проверки тестами):
                          </div>
                          <pre className="p-1.5 bg-emerald-950/20 border border-emerald-900/40 rounded text-emerald-300 font-mono text-[10px] whitespace-pre-wrap leading-tight">
                            {issue.suggestedCode || 'Для этого дефекта готовый вариант кода не предложен. См. рекомендацию выше.'}
                          </pre>
                        </div>

                        <div className="pt-1 border-t border-zinc-800/60 flex items-center justify-between text-[10px]">
                          <span className="text-amber-400 font-medium">Оценка: {issue.complexityBefore ?? '—'} → {issue.complexityAfter ?? '—'}</span>
                          <span className="text-emerald-400 font-medium">Требуется Quality Gate</span>
                        </div>
                      </div>
                    )}
                  </div>

                  <div className="pt-1.5 border-t border-border/30 flex items-center justify-between">
                    <span className="text-[10px] text-zinc-500 font-mono">Строки {issue.lineStart}–{issue.lineEnd}</span>
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        optimizeSingleIssue(issue.id);
                      }}
                      disabled={isCompiling}
                      className="px-2 py-1 rounded bg-[#f37022]/15 hover:bg-[#f37022]/25 border border-[#f37022]/50 text-[10px] font-semibold text-[#f37022] dark:text-[#ff7b29] transition flex items-center gap-1 shadow hover:scale-105 active:scale-95"
                      title="Применить точечное исправление по Слайду 5 ТЗ"
                    >
                      <Sparkles className="w-3 h-3 text-amber-300" />
                      <span>Исправить дефект</span>
                    </button>
                  </div>
                </div>
              );
            })}

            {syncStatus === 'offline' && (
              <div className="p-2 rounded border border-amber-600/40 bg-amber-950/30 text-[11px] text-amber-300">
                Бэкенд недоступен — показаны демонстрационные данные
              </div>
            )}

            {syncStatus === 'error' && (
              <div className="p-2 rounded border border-rose-600/40 bg-rose-950/30 text-[11px] text-rose-300 break-words">
                {syncError}
              </div>
            )}

            {!analysisLoaded && syncStatus === 'syncing' && (
              <div className="p-6 text-center text-xs text-zinc-400">Идёт анализ…</div>
            )}

            {analysisLoaded && issues.length === 0 && (
              <div className="p-6 text-center text-xs text-zinc-400">Дефектов не найдено</div>
            )}
          </div>
        )}

        {rightTab === 'recommendations' && (
          <div className="space-y-2.5 text-xs">
            {recommendations.map(([rec, issue]) => (
              <div key={rec} className="p-3 rounded-lg border border-border bg-surface-elevated/40">
                <div className="font-semibold text-zinc-200 mb-1 flex items-center gap-1.5">
                  <Sparkles className="w-3.5 h-3.5 text-amber-400 shrink-0" />
                  <span>{issue.title}</span>
                </div>
                <p className="text-[11px] text-blue-300 leading-relaxed">{rec}</p>
                {issue.risk && <p className="mt-1 text-[11px] text-zinc-400 leading-relaxed">{issue.risk}</p>}
                {issue.suggestedCode && (
                  <pre className="mt-2 p-2 max-h-40 overflow-auto rounded bg-zinc-900 border border-border text-[10px] font-mono text-zinc-300">
                    {issue.suggestedCode}
                  </pre>
                )}
              </div>
            ))}
            {recommendations.length === 0 && <div className="p-6 text-center text-zinc-400">Нет рекомендаций</div>}
          </div>
        )}

        {rightTab === 'metrics' && (
          <div className="space-y-3 text-xs">
            <div className="grid grid-cols-2 gap-2">
              <div className="p-2.5 rounded-lg border border-border bg-surface-elevated/50">
                <div className="text-[10px] text-zinc-500 mb-1">Строк кода (LOC)</div>
                <div className="text-base font-bold text-zinc-200 font-mono">{metrics.linesOfCode}</div>
              </div>
              <div className="p-2.5 rounded-lg border border-border bg-surface-elevated/50">
                <div className="text-[10px] text-zinc-500 mb-1">Цикломатическая сложн.</div>
                <div className="text-base font-bold text-amber-400 font-mono">{metrics.cyclomatic}</div>
              </div>
              <div className="p-2.5 rounded-lg border border-border bg-surface-elevated/50">
                <div className="text-[10px] text-zinc-500 mb-1">Методов</div>
                <div className="text-base font-bold text-zinc-200 font-mono">{metrics.methodsCount}</div>
              </div>
              <div className="p-2.5 rounded-lg border border-border bg-surface-elevated/50">
                <div className="text-[10px] text-zinc-500 mb-1">Проблем</div>
                <div className="text-base font-bold text-rose-400 font-mono">{issues.length}</div>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Bottom Complexity & Performance Widget (Слайд 4 ТЗ) */}
      <div className="p-3 bg-surface-elevated/80 border-t border-border space-y-2.5">
        <div className="flex items-center justify-between text-xs font-semibold text-zinc-300">
          <span className="flex items-center gap-1.5">
            <TrendingUp className="w-3.5 h-3.5 text-[#f37022]" />
            <span>Сложность алгоритма:</span>
          </span>
        </div>

        <div className="flex items-center justify-between px-3 py-2 bg-surface border border-border rounded-lg text-xs font-mono">
          <span className="text-zinc-400">
            Текущая: <span className={`font-bold ${isOptimized ? 'text-emerald-400' : 'text-rose-400'}`}>{metrics.current}</span>
          </span>
          <span className="text-zinc-500">→</span>
          <span className="text-zinc-400">
            После: <span className="font-bold text-emerald-400">{metrics.predicted}</span>
          </span>
        </div>

        {/* Speedup Badge */}
        <div className="flex items-center justify-between px-3 py-2 bg-emerald-950/30 border border-emerald-500/30 rounded-lg">
          <span className="text-[11px] text-zinc-300">Ускорение (замер Quality Gate)</span>
          <span className="text-xs font-bold text-emerald-400 font-mono">{metrics.speedup}</span>
        </div>

        {/* 1-Click Optimize Button */}
        {!isOptimized && (
          <button
            onClick={runOptimization}
            disabled={isCompiling}
            className="w-full py-2 bg-[#f37022] hover:bg-[#ff6500] active:bg-[#c5621b] text-white rounded-lg font-semibold text-xs transition shadow-md shadow-[#f37022]/20 flex items-center justify-center gap-2"
          >
            <Sparkles className="w-3.5 h-3.5" />
            <span>{isCompiling ? 'Оптимизация...' : 'Оптимизировать и проверить'}</span>
          </button>
        )}
      </div>
    </div>
  );
};
