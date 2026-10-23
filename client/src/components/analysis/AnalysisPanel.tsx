import React from 'react';
import { useStudioStore } from '../../store/useStudioStore';
import { AlertTriangle, AlertCircle, TrendingUp, Sparkles, Activity, FileText, CheckCircle2 } from 'lucide-react';

export const AnalysisPanel: React.FC = () => {
  const {
    issues,
    metrics,
    selectedIssueId,
    selectIssue,
    rightTab,
    setRightTab,
    isOptimized,
    runOptimization,
    isCompiling,
  } = useStudioStore();

  return (
    <div className="w-80 h-full bg-surface border-l border-border flex flex-col overflow-hidden select-none">
      {/* Header Tabs */}
      <div className="flex items-center bg-surface-elevated border-b border-border h-9 shrink-0 px-2 gap-1">
        <button
          onClick={() => setRightTab('issues')}
          className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-t transition border-b-2 ${
            rightTab === 'issues'
              ? 'text-white border-blue-500 bg-surface'
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
              ? 'text-white border-blue-500 bg-surface'
              : 'text-zinc-400 border-transparent hover:text-zinc-200'
          }`}
        >
          <Sparkles className="w-3.5 h-3.5 text-amber-400" />
          <span>Советы (4)</span>
        </button>

        <button
          onClick={() => setRightTab('metrics')}
          className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-t transition border-b-2 ${
            rightTab === 'metrics'
              ? 'text-white border-blue-500 bg-surface'
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
                      ? 'border-blue-500 bg-blue-950/20 shadow-md ring-1 ring-blue-500/30'
                      : 'border-border bg-surface-elevated/40 hover:border-zinc-600 hover:bg-surface-elevated/80'
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
                            : 'bg-amber-950/80 text-amber-400 border border-amber-600/40'
                        }`}
                      >
                        {isHigh ? 'Высокая' : 'Средняя'}
                      </span>
                      <span className="text-[10px] text-zinc-500 font-mono">{issue.fileName}</span>
                    </div>
                  </div>

                  <p className="text-[11px] text-zinc-400 leading-relaxed mb-2">{issue.description}</p>

                  <div className="pt-2 border-t border-border/40 text-[10px] text-zinc-400 flex items-start gap-1">
                    <span className="text-zinc-500 font-semibold">Решение:</span>
                    <span className="text-blue-300">{issue.recommendation}</span>
                  </div>
                </div>
              );
            })}

            {issues.length === 0 && (
              <div className="p-6 text-center text-zinc-400 space-y-2">
                <CheckCircle2 className="w-8 h-8 text-emerald-400 mx-auto" />
                <div className="font-semibold text-zinc-200">Все дефекты устранены</div>
                <div className="text-[11px]">Код оптимизирован до O(n), тесты Quality Gate пройдены.</div>
              </div>
            )}
          </div>
        )}

        {rightTab === 'recommendations' && (
          <div className="space-y-2.5 text-xs">
            <div className="p-3 rounded-lg border border-border bg-surface-elevated/40">
              <div className="font-semibold text-zinc-200 mb-1 flex items-center gap-1.5">
                <Sparkles className="w-3.5 h-3.5 text-amber-400" />
                <span>Индексация в HashMap</span>
              </div>
              <p className="text-[11px] text-zinc-400 leading-relaxed">
                Замените квадратичный проход по orders и processed на предвыборку <code>processedById = Map.of(...)</code>.
              </p>
            </div>
            <div className="p-3 rounded-lg border border-border bg-surface-elevated/40">
              <div className="font-semibold text-zinc-200 mb-1 flex items-center gap-1.5">
                <FileText className="w-3.5 h-3.5 text-blue-400" />
                <span>Декомпозиция на 4 класса</span>
              </div>
              <p className="text-[11px] text-zinc-400 leading-relaxed">
                Вынесите логику в Parser.java, Analyzer.java и Optimizer.java согласно Слайду 6 ТЗ.
              </p>
            </div>
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
            </div>
          </div>
        )}
      </div>

      {/* Bottom Complexity & Performance Widget (Слайд 4 ТЗ) */}
      <div className="p-3 bg-surface-elevated/80 border-t border-border space-y-2.5">
        <div className="flex items-center justify-between text-xs font-semibold text-zinc-300">
          <span className="flex items-center gap-1.5">
            <TrendingUp className="w-3.5 h-3.5 text-blue-400" />
            <span>Сложность алгоритма:</span>
          </span>
        </div>

        <div className="flex items-center justify-between px-3 py-2 bg-zinc-900 border border-border rounded-lg text-xs font-mono">
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
          <span className="text-[11px] text-zinc-300">Оценка ускорения:</span>
          <span className="text-xs font-bold text-emerald-400 font-mono">{metrics.speedup}</span>
        </div>

        {/* 1-Click Optimize Button */}
        {!isOptimized && (
          <button
            onClick={runOptimization}
            disabled={isCompiling}
            className="w-full py-2 bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-500 hover:to-indigo-500 text-white rounded-lg font-semibold text-xs transition shadow-lg shadow-blue-900/30 flex items-center justify-center gap-2"
          >
            <Sparkles className="w-3.5 h-3.5" />
            <span>{isCompiling ? 'Оптимизация...' : 'Оптимизировать O(n²) → O(n)'}</span>
          </button>
        )}
      </div>
    </div>
  );
};
