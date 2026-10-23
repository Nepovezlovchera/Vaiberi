import React, { useEffect, useRef } from 'react';
import { useStudioStore } from '../../store/useStudioStore';
import { Terminal, Trash2 } from 'lucide-react';

export const ConsolePane: React.FC = () => {
  const { consoleLogs, isCompiling } = useStudioStore();
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [consoleLogs]);

  return (
    <div className="w-full h-full bg-[#0c0c0e] font-mono text-xs flex flex-col p-3 overflow-hidden select-text">
      <div className="flex items-center justify-between pb-2 border-b border-border/60 text-zinc-400 mb-2">
        <div className="flex items-center gap-2">
          <Terminal className="w-3.5 h-3.5 text-blue-400" />
          <span className="font-semibold text-zinc-300">Терминал компиляции и вывода песочницы</span>
          {isCompiling && (
            <span className="flex items-center gap-1.5 text-amber-400 text-[11px] animate-pulse">
              <span className="w-2 h-2 rounded-full bg-amber-400 animate-ping" />
              Компиляция...
            </span>
          )}
        </div>
      </div>

      <div className="flex-1 overflow-y-auto space-y-1 pr-2">
        {consoleLogs.map((log, index) => {
          const isError = log.includes('ERROR') || log.includes('FAIL');
          const isSuccess = log.includes('✔') || log.includes('СОВПАДАЕТ') || log.includes('успешно');
          const isWarning = log.includes('Вложенность O(n²)') || log.includes('обнаружен');

          return (
            <div
              key={index}
              className={`leading-relaxed ${
                isError
                  ? 'text-rose-400'
                  : isSuccess
                  ? 'text-emerald-400'
                  : isWarning
                  ? 'text-amber-300'
                  : 'text-zinc-300'
              }`}
            >
              {log}
            </div>
          );
        })}
        <div ref={bottomRef} />
      </div>
    </div>
  );
};
