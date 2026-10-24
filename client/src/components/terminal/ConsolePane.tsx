import React, { useEffect, useRef } from 'react';
import { useStudioStore } from '../../store/useStudioStore';
import { Terminal, Trash2 } from 'lucide-react';

export const ConsolePane: React.FC = () => {
  const { consoleLogs, isCompiling, runCodeInConsole } = useStudioStore();
  const bottomRef = useRef<HTMLDivElement>(null);
  const [consoleStdin, setConsoleStdin] = React.useState('');

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [consoleLogs]);

  const handleRun = () => {
    // Программа читает stdin построчно: последняя строка должна заканчиваться переводом строки
    runCodeInConsole(consoleStdin && !consoleStdin.endsWith('\n') ? consoleStdin + '\n' : consoleStdin);
  };

  const handleClear = () => {
    useStudioStore.setState({ consoleLogs: [] });
  };

  return (
    <div className="w-full h-full bg-[#0c0c0e] font-mono text-xs flex flex-col p-3 overflow-hidden select-text">
      <div className="flex items-center justify-between pb-2 border-b border-border/60 text-zinc-400 mb-2 gap-2">
        <div className="flex items-center gap-2">
          <Terminal className="w-3.5 h-3.5 text-blue-400" />
          <span className="font-semibold text-zinc-300">Терминал JVM</span>
          <span
            className="px-1.5 py-0.5 rounded text-[10px] font-mono text-zinc-400 bg-zinc-900 border border-zinc-800"
            title="Защита от Client-side DoS: Ring Buffer (максимум 1000 строк)"
          >
            {consoleLogs.length}/1000
          </span>
          {isCompiling && (
            <span className="flex items-center gap-1.5 text-amber-400 text-[11px] animate-pulse">
              <span className="w-2 h-2 rounded-full bg-amber-400 animate-ping" />
              Компиляция...
            </span>
          )}
        </div>

        {/* Console Quick Run with custom Stdin */}
        <div className="flex items-center gap-1.5">
          <textarea
            rows={2}
            placeholder="Ввод stdin (несколько строк; Ctrl+Enter — запуск)"
            value={consoleStdin}
            onChange={(e) => setConsoleStdin(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
                e.preventDefault();
                handleRun();
              }
            }}
            className="w-44 sm:w-56 md:w-72 px-2 py-0.5 bg-zinc-900 border border-border rounded text-[11px] text-zinc-200 placeholder-zinc-500 font-mono focus:outline-none focus:border-blue-500 resize-y max-h-32"
          />
          <button
            onClick={handleRun}
            className="px-2.5 py-1 bg-blue-600 hover:bg-blue-500 text-white rounded text-[11px] font-semibold transition flex items-center gap-1 shadow"
            title="Скомпилировать и запустить Main.java в песочнице с этим stdin"
          >
            Запустить
          </button>
          <button
            onClick={handleClear}
            className="p-1 hover:bg-zinc-800 rounded text-zinc-500 hover:text-zinc-300 transition"
            title="Очистить консоль"
          >
            <Trash2 className="w-3.5 h-3.5" />
          </button>
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
              className={`leading-relaxed whitespace-pre-wrap break-words ${
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
