import React, { useEffect, useRef } from 'react';
import { useStudioStore } from '../../store/useStudioStore';
import { Terminal, Trash2, Square } from 'lucide-react';

export const ConsolePane: React.FC = () => {
  const {
    consoleLogs,
    isCompiling,
    runCodeInConsole,
    isProgramRunning,
    programSessionId,
    sendConsoleInput,
    sendConsoleEof,
    stopProgram,
  } = useStudioStore();
  const bottomRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const [line, setLine] = React.useState('');
  const waitingForInput = isProgramRunning && !!programSessionId;

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [consoleLogs]);

  // Программа стартовала — ставим курсор в строку ввода
  useEffect(() => {
    if (waitingForInput) inputRef.current?.focus();
  }, [waitingForInput]);

  const submit = () => {
    sendConsoleInput(line);
    setLine('');
  };

  const handleClear = () => {
    useStudioStore.setState({ consoleLogs: [] });
  };

  return (
    <div className="w-full h-full bg-surface font-mono text-xs flex flex-col p-3 overflow-hidden select-text text-zinc-200">
      <div className="flex items-center justify-between pb-2 border-b border-border text-zinc-400 mb-2 gap-2">
        <div className="flex items-center gap-2">
          <Terminal className="w-3.5 h-3.5 text-[#f37022]" />
          <span className="font-semibold text-zinc-200">Терминал JVM</span>
          <span
            className="px-1.5 py-0.5 rounded text-[10px] font-mono text-zinc-400 bg-surface-elevated border border-border"
            title="Защита от Client-side DoS: Ring Buffer (максимум 1000 строк)"
          >
            {consoleLogs.length}/1000
          </span>
          {isCompiling && (
            <span className="flex items-center gap-1.5 text-amber-500 dark:text-amber-400 text-[11px] animate-pulse">
              <span className="w-2 h-2 rounded-full bg-amber-400 animate-ping" />
              Компиляция...
            </span>
          )}
        </div>

        <div className="flex items-center gap-1.5">
          {isProgramRunning ? (
            <button
              onClick={stopProgram}
              disabled={!programSessionId}
              className="px-2.5 py-1 bg-rose-700 hover:bg-rose-600 disabled:opacity-50 text-white rounded text-[11px] font-semibold transition flex items-center gap-1 shadow"
              title="Остановить программу"
            >
              <Square className="w-3 h-3" /> Стоп
            </button>
          ) : (
            <button
              onClick={() => runCodeInConsole()}
              className="px-2.5 py-1 bg-[#f37022] hover:bg-[#ff6500] text-white rounded text-[11px] font-semibold transition flex items-center gap-1 shadow"
              title="Скомпилировать и запустить Main.java; ввод — прямо здесь, когда программа его ждёт"
            >
              Запустить
            </button>
          )}
          <button
            onClick={handleClear}
            className="p-1 hover:bg-surface-elevated rounded text-zinc-500 hover:text-zinc-300 transition"
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
                log.startsWith('[STDIN]')
                  ? 'text-cyan-300'
                  : isError
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

      {isProgramRunning && (
        <div className="mt-2 flex items-center gap-2 border-t border-border/60 pt-2">
          <span className="text-emerald-400">›</span>
          <input
            ref={inputRef}
            value={line}
            disabled={!programSessionId}
            onChange={(e) => setLine(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault();
                submit();
              } else if (e.key === 'd' && e.ctrlKey) {
                e.preventDefault();
                sendConsoleEof();
              }
            }}
            placeholder={programSessionId ? 'Введите текст и нажмите Enter (Ctrl+D — конец ввода)' : 'Компиляция…'}
            className="flex-1 px-2 py-1 bg-zinc-900 border border-border rounded text-[12px] text-zinc-100 placeholder-zinc-500 font-mono focus:outline-none focus:border-emerald-500 disabled:opacity-60"
            autoComplete="off"
            spellCheck={false}
          />
          <button
            onClick={submit}
            disabled={!programSessionId}
            className="px-2 py-1 bg-emerald-700 hover:bg-emerald-600 disabled:opacity-50 text-white rounded text-[11px] font-semibold transition"
          >
            Ввод
          </button>
          <button
            onClick={sendConsoleEof}
            disabled={!programSessionId}
            className="px-2 py-1 bg-zinc-800 hover:bg-zinc-700 disabled:opacity-50 text-zinc-200 rounded text-[11px] transition"
            title="Закрыть поток ввода (Ctrl+D)"
          >
            EOF
          </button>
        </div>
      )}
    </div>
  );
};
