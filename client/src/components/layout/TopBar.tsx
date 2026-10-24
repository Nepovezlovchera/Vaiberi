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
    importProject,
    addConsoleLog,
    isBackendOnline,
    checkBackend,
    isDebugging,
    startDebug,
    stopDebug,
    activePreset,
    setPreset,
    isAutoDemoRunning,
    runAutoDemo,
  } = useStudioStore();
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    checkBackend();
  }, [checkBackend]);

  const handleImportClick = () => {
    fileInputRef.current?.click();
  };

  const MAX_JAVA_FILE_SIZE = 2 * 1024 * 1024; // 2 MB limit (Client-side DoS prevention)

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const list = Array.from(e.target.files || []).filter((f) => f.name.endsWith('.java'));
    const input = e.target;
    const tooBig = list.find((f) => f.size > MAX_JAVA_FILE_SIZE);
    if (tooBig) {
      addConsoleLog(`[SECURITY WARNING] Файл «${tooBig.name}» превышает лимит 2 МБ.`);
      alert(`Файл «${tooBig.name}» превышает допустимый размер (максимум 2 МБ).`);
    } else if (list.length > 0) {
      const loaded = await Promise.all(
        list.map(
          (f) =>
            new Promise<{ name: string; content: string }>((resolve) => {
              const reader = new FileReader();
              reader.onload = () => resolve({ name: (f as File & { webkitRelativePath?: string }).webkitRelativePath || f.name, content: String(reader.result || '') });
              reader.readAsText(f);
            })
        )
      );
      await importProject(loaded.filter((f) => f.content));
    }
    input.value = '';
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
      <input ref={fileInputRef} type="file" accept=".java" multiple onChange={handleFileChange} className="hidden" />

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
      {/* Action Buttons (Слайд 4 ТЗ) */}
      <div className="flex items-center gap-2">
        {/* Monolith / Demo Preset Switcher (Слайды 2, 4 ТЗ) */}
        <div className="flex items-center bg-zinc-900 border border-border rounded-md p-0.5 text-[11px] mr-1">
          <button
            onClick={() => setPreset('demo')}
            className={`px-2 py-1 rounded transition font-medium ${
              activePreset === 'demo'
                ? 'bg-blue-600 text-white shadow'
                : 'text-zinc-400 hover:text-zinc-200'
            }`}
            title="Метод processOrders (Слайд 4 ТЗ)"
          >
            Демо (Слайд 4)
          </button>
          <button
            onClick={() => setPreset('monolith')}
            className={`px-2 py-1 rounded transition font-medium ${
              activePreset === 'monolith'
                ? 'bg-blue-600 text-white shadow'
                : 'text-zinc-400 hover:text-zinc-200'
            }`}
            title="Полный монолит интернет-магазина 500 строк (Слайды 2, 6 ТЗ)"
          >
            Монолит (500 строк)
          </button>
        </div>

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

        {/* Happy Path Auto Demo Button (Слайд 10 ТЗ) */}
        <button
          onClick={runAutoDemo}
          disabled={isAutoDemoRunning}
          className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-semibold transition border shadow-md ${
            isAutoDemoRunning
              ? 'bg-purple-600 text-white animate-pulse border-purple-400'
              : 'bg-gradient-to-r from-purple-600/90 to-pink-600/90 hover:from-purple-500 hover:to-pink-500 text-white border-purple-400/40 hover:scale-105 active:scale-95'
          }`}
          title="Автономный показ полного пути за 60 секунд (Слайд 10 ТЗ)"
        >
          <Sparkles className="w-3.5 h-3.5 text-amber-200" />
          <span>{isAutoDemoRunning ? 'Демо ТЗ...' : '▶ Демо ТЗ (60 сек)'}</span>
        </button>

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
          onClick={isDebugging ? stopDebug : startDebug}
          className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium transition border ${
            isDebugging
              ? 'bg-amber-500 text-black border-amber-400 font-bold shadow-lg shadow-amber-500/20'
              : 'text-zinc-300 hover:text-white bg-zinc-800/80 hover:bg-zinc-700/80 border-border'
          }`}
          title="Интерактивный пошаговый дебаг узлов CFG графа (Слайд 4 ТЗ)"
        >
          <Bug className={`w-3.5 h-3.5 ${isDebugging ? 'text-black' : 'text-amber-400'}`} />
          <span>{isDebugging ? 'Стоп дебаг' : 'Дебаг'}</span>
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
