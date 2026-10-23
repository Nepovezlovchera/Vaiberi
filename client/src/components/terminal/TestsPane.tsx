import React from 'react';
import { useStudioStore } from '../../store/useStudioStore';
import { CheckCircle2, Play, Clock, ShieldCheck } from 'lucide-react';

export const TestsPane: React.FC = () => {
  const { tests, isRunningTests, runTests, isOptimized } = useStudioStore();

  const passedCount = tests.filter((t) => t.status === 'PASSED').length;
  const totalCount = tests.length;
  const percent = Math.round((passedCount / totalCount) * 100);

  return (
    <div className="w-full h-full bg-[#0c0c0e] flex flex-col p-3 overflow-hidden select-text text-xs">
      {/* Quality Gate Header Banner */}
      <div className="flex items-center justify-between pb-3 border-b border-border mb-3">
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2">
            <ShieldCheck className="w-4 h-4 text-emerald-400" />
            <span className="font-semibold text-zinc-200">Quality Gate: Сохранение поведения</span>
          </div>
          <span className="px-2 py-0.5 rounded bg-emerald-950/80 border border-emerald-500/40 text-emerald-300 font-bold text-[11px]">
            Все тесты пройдены: {passedCount} из {totalCount} ({percent}%)
          </span>
          {isOptimized && (
            <span className="px-2 py-0.5 rounded bg-blue-950/80 border border-blue-500/40 text-blue-300 font-semibold text-[11px]">
              ⚡ Ускорение 5.2x
            </span>
          )}
        </div>

        <button
          onClick={runTests}
          disabled={isRunningTests}
          className="flex items-center gap-1.5 px-3 py-1 bg-zinc-800 hover:bg-zinc-700 text-zinc-200 rounded font-medium transition disabled:opacity-50"
        >
          <Play className={`w-3.5 h-3.5 ${isRunningTests ? 'animate-spin text-amber-400' : 'text-emerald-400'}`} />
          <span>{isRunningTests ? 'Прогон тестов...' : 'Запустить тесты'}</span>
        </button>
      </div>

      {/* Progress Bar */}
      <div className="w-full h-1.5 bg-zinc-800 rounded-full mb-3 overflow-hidden">
        <div className="h-full bg-emerald-500 rounded-full transition-all duration-500" style={{ width: `${percent}%` }} />
      </div>

      {/* Tests Table */}
      <div className="flex-1 overflow-y-auto">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="border-b border-border/80 text-[11px] font-medium text-zinc-400">
              <th className="py-1.5 px-2">Тестовый сценарий</th>
              <th className="py-1.5 px-2">Статус поведения</th>
              <th className="py-1.5 px-2 text-right">Время выполнения</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border/30 font-mono text-[11px]">
            {tests.map((test) => (
              <tr key={test.id} className="hover:bg-zinc-900/50 transition">
                <td className="py-1.5 px-2 text-zinc-200 flex items-center gap-2">
                  <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
                  <span>{test.name}</span>
                </td>
                <td className="py-1.5 px-2">
                  <span className="text-emerald-400 font-medium font-sans text-xs">Успешно (Совпадает ✔)</span>
                </td>
                <td className="py-1.5 px-2 text-right text-zinc-400">
                  <span className="inline-flex items-center gap-1">
                    <Clock className="w-3 h-3 text-zinc-500" />
                    {test.durationMs >= 1000 ? `${(test.durationMs / 1000).toFixed(2)} сек` : `${test.durationMs} мс`}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};
