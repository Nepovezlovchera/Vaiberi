import React, { useEffect, useRef } from 'react';
import { useStudioStore } from '../../store/useStudioStore';
import {
  UploadCloud,
  Download,
  Sparkles,
  Play,
  Bug,
  Moon,
  Workflow,
  ShieldCheck,
  RefreshCw,
} from 'lucide-react';

export const TopBar: React.FC = () => {
  const {
    runOptimization,
    runTests,
    isCompiling,
    isRunningTests,
    isOptimized,
    setCode,
    addConsoleLog,
    isBackendOnline,
    checkBackend,
  } = useStudioStore();
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    checkBackend();
  }, [checkBackend]);

  const handleImportClick = () => {
    fileInputRef.current?.click();
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      const reader = new FileReader();
      reader.onload = (event) => {
        const content = event.target?.result as string;
        if (content) {
          setCode(content);
          addConsoleLog(`[${new Date().toTimeString().split(' ')[0]}] Загружен файл ${file.name} (${content.split('\n').length} строк)`);
        }
      };
      reader.readAsText(file);
    }
  };

  const handleExport = () => {
    const { code } = useStudioStore.getState();
    const blob = new Blob([code], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = isOptimized ? 'MainOptimized.java' : 'Main.java';
    link.click();
    URL.revokeObjectURL(url);
  };

  return (
    <header className="h-12 w-full bg-surface border-b border-border flex items-center justify-between px-3 shrink-0 select-none">
      {/* Hidden File Input */}
      <input ref={fileInputRef} type="file" accept=".java" onChange={handleFileChange} className="hidden" />

      {/* Brand / Logo */}
      <div className="flex items-center gap-3">
        <div className="flex items-center gap-2">
          <div className="w-7 h-7 rounded-lg bg-blue-600/20 border border-blue-500/40 flex items-center justify-center">
            <Workflow className="w-4 h-4 text-blue-400" />
          </div>
          <div>
            <span className="font-bold text-sm text-zinc-100">Визуальная среда программирования</span>
            <span className="ml-2 text-[10px] text-blue-400 font-mono font-medium px-1.5 py-0.5 rounded bg-blue-950/60 border border-blue-600/30">
              Java Blueprints
            </span>
          </div>
        </div>

        <div className="h-4 w-px bg-border mx-1" />

        {/* Live Backend / Demo Mode Status Badge */}
        <div
          onClick={checkBackend}
          className="cursor-pointer transition-transform hover:scale-105"
          title="Нажмите для повторной проверки связи с бэкендом"
        >
          {isBackendOnline ? (
            <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-emerald-950/80 text-emerald-400 border border-emerald-500/40 flex items-center gap-1.5 shadow-sm">
              <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
              Live Backend API (:8080)
            </span>
          ) : (
            <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-blue-950/80 text-blue-300 border border-blue-500/40 flex items-center gap-1.5 shadow-sm">
              <span className="w-2 h-2 rounded-full bg-blue-400" />
              Demo Sandbox Mode
            </span>
          )}
        </div>

        <div className="flex items-center gap-1.5 text-xs text-zinc-400 ml-1">
          <ShieldCheck className="w-3.5 h-3.5 text-emerald-400" />
          <span>Quality Gate</span>
        </div>
      </div>

      {/* Action Buttons (Слайд 4 ТЗ) */}
      <div className="flex items-center gap-2">
        <button
          onClick={handleImportClick}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium text-zinc-300 hover:text-white bg-zinc-800/80 hover:bg-zinc-700/80 border border-border transition"
        >
          <UploadCloud className="w-3.5 h-3.5 text-blue-400" />
          <span>Импорт Java</span>
        </button>

        <button
          onClick={handleExport}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium text-zinc-300 hover:text-white bg-zinc-800/80 hover:bg-zinc-700/80 border border-border transition"
        >
          <Download className="w-3.5 h-3.5 text-zinc-400" />
          <span>Экспорт Java</span>
        </button>

        <div className="h-4 w-px bg-border mx-1" />

        <button
          onClick={runOptimization}
          disabled={isCompiling || isOptimized}
          className={`flex items-center gap-1.5 px-3.5 py-1.5 rounded-md text-xs font-semibold transition shadow-md ${
            isOptimized
              ? 'bg-emerald-950/80 text-emerald-300 border border-emerald-500/40 cursor-default'
              : 'bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-500 hover:to-indigo-500 text-white border border-blue-400/30'
          }`}
        >
          <Sparkles className="w-3.5 h-3.5 text-amber-300" />
          <span>{isCompiling ? 'Оптимизация...' : isOptimized ? 'Оптимизировано ✔' : 'Оптимизировать'}</span>
        </button>

        <button
          onClick={runTests}
          disabled={isRunningTests}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium text-emerald-300 bg-emerald-950/60 hover:bg-emerald-900/60 border border-emerald-600/40 transition"
        >
          <Play className="w-3.5 h-3.5 text-emerald-400" />
          <span>{isRunningTests ? 'Запуск...' : 'Запуск'}</span>
        </button>

        <button
          onClick={() => alert('Интерактивный дебаг активирован: точки останова согласованы со строками AST.')}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium text-zinc-300 hover:text-white bg-zinc-800/80 hover:bg-zinc-700/80 border border-border transition"
        >
          <Bug className="w-3.5 h-3.5 text-amber-400" />
          <span>Дебаг</span>
        </button>

        <div className="h-4 w-px bg-border mx-1" />

        {/* Refresh Backend Status & Theme */}
        <button
          onClick={checkBackend}
          className="p-1.5 rounded-md text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800 transition"
          title="Обновить связь с бэкендом"
        >
          <RefreshCw className="w-3.5 h-3.5" />
        </button>

        <button
          className="p-1.5 rounded-md text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800 transition"
          title="Темная тема активна"
        >
          <Moon className="w-4 h-4 text-zinc-400" />
        </button>
      </div>
    </header>
  );
};
