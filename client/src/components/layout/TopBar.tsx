import React, { useEffect, useRef } from 'react';
import JSZip from 'jszip';
import { useStudioStore } from '../../store/useStudioStore';
import {
  UploadCloud,
  Download,
  Sparkles,
  Play,
  Bug,
  Moon,
  Sun,
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
    theme,
    toggleTheme,
  } = useStudioStore();
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    checkBackend();
  }, [checkBackend]);

  const handleImportClick = () => {
    fileInputRef.current?.click();
  };

  const MAX_JAVA_FILE_SIZE = 1_000_000;

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const list = Array.from(e.target.files || []).filter((f) => f.name.endsWith('.java'));
    const input = e.target;
    const tooBig = list.find((f) => f.size > MAX_JAVA_FILE_SIZE);
    if (tooBig) {
      addConsoleLog(`[ERROR] Файл «${tooBig.name}» превышает лимит 1 МБ.`);
      alert(`Файл «${tooBig.name}» превышает допустимый размер (максимум 1 МБ).`);
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

  const handleExport = async () => {
    const { files, tests } = useStudioStore.getState();
    const allFiles = files.flatMap(function flatten(item): { path: string; source: string }[] {
      if (item.type === 'file') return [{ path: item.path.replace(/^src\//, ''), source: item.code || '' }];
      return (item.children || []).flatMap(flatten);
    });
    const archive = new JSZip();
    allFiles.forEach((file) => archive.file(file.path, file.source));
    archive.file('tests.json', JSON.stringify(tests.map(({ id, name, stdin, expectedStdout }) =>
      ({ id, name, stdin, expectedStdout })), null, 2));
    archive.file('README.txt', 'Java Blueprints export\nCompile: javac -encoding UTF-8 -d classes <all .java paths>\nRun: java -cp classes <full main class name>\n');
    const blob = await archive.generateAsync({ type: 'blob' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'java-blueprints-project.zip';
    link.click();
    URL.revokeObjectURL(url);
  };

  return (
    <header className="h-12 w-full bg-surface border-b border-border flex items-center justify-between px-3 shrink-0 select-none gap-2 overflow-x-auto no-scrollbar">
      {/* Hidden File Input */}
      <input ref={fileInputRef} type="file" accept=".java" multiple onChange={handleFileChange} className="hidden" />

      {/* Brand / Logo */}
      <div className="flex items-center gap-2.5 shrink-0 min-w-0">
        <div className="flex items-center gap-2 shrink-0">
          <div className="w-7 h-7 rounded-lg bg-blue-600/20 border border-blue-500/40 flex items-center justify-center shrink-0">
            <Workflow className="w-4 h-4 text-blue-400" />
          </div>
          <div className="flex items-center">
            <span className="font-bold text-sm text-zinc-100 hidden 2xl:inline whitespace-nowrap">
              Визуальная среда программирования
            </span>
            <span className="2xl:ml-2 text-[10px] text-blue-400 font-mono font-medium px-1.5 py-0.5 rounded bg-blue-950/60 border border-blue-600/30 whitespace-nowrap">
              Java Blueprints
            </span>
          </div>
        </div>

        <div className="h-4 w-px bg-border mx-0.5 shrink-0" />

        {/* Live Backend / Demo Mode Status Badge */}
        <div
          onClick={checkBackend}
          className="cursor-pointer transition-transform hover:scale-105 shrink-0"
          title="Нажмите для повторной проверки связи с бэкендом"
        >
          {isBackendOnline ? (
            <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-emerald-950/80 text-emerald-400 border border-emerald-500/40 flex items-center gap-1.5 shadow-sm whitespace-nowrap">
              <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse shrink-0" />
              <span className="hidden sm:inline">Live Backend API (:8080)</span>
              <span className="sm:hidden">:8080</span>
            </span>
          ) : (
            <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-blue-950/80 text-blue-300 border border-blue-500/40 flex items-center gap-1.5 shadow-sm whitespace-nowrap">
              <span className="w-2 h-2 rounded-full bg-blue-400 shrink-0" />
              <span>Sandbox Mode</span>
            </span>
          )}
        </div>

        <div className="flex items-center gap-1.5 text-xs text-zinc-400 shrink-0" title="Quality Gate активен">
          <ShieldCheck className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
          <span className="hidden xl:inline whitespace-nowrap">Quality Gate</span>
        </div>
      </div>

      {/* Action Buttons */}
      <div className="flex items-center gap-1.5 shrink-0">
        <button
          onClick={handleImportClick}
          className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-md text-xs font-medium text-zinc-300 hover:text-white bg-zinc-800/80 hover:bg-zinc-700/80 border border-border transition shrink-0 whitespace-nowrap"
          title="Импорт Java файла"
        >
          <UploadCloud className="w-3.5 h-3.5 text-blue-400 shrink-0" />
          <span className="hidden xl:inline">Импорт Java</span>
        </button>

        <button
          onClick={handleExport}
          className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-md text-xs font-medium text-zinc-300 hover:text-white bg-zinc-800/80 hover:bg-zinc-700/80 border border-border transition shrink-0 whitespace-nowrap"
          title="Экспорт всего проекта и тестов в ZIP"
        >
          <Download className="w-3.5 h-3.5 text-zinc-400 shrink-0" />
          <span className="hidden xl:inline">Экспорт Java</span>
        </button>

        <div className="h-4 w-px bg-border mx-0.5 shrink-0" />

        <button
          onClick={runOptimization}
          disabled={isCompiling || isOptimized}
          className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-semibold transition shadow-md shrink-0 whitespace-nowrap ${
            isOptimized
              ? 'bg-emerald-950/80 text-emerald-300 border border-emerald-500/40 cursor-default'
              : 'bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-500 hover:to-indigo-500 text-white border border-blue-400/30'
          }`}
        >
          <Sparkles className="w-3.5 h-3.5 text-amber-300 shrink-0" />
          <span>{isCompiling ? 'Оптимизация...' : isOptimized ? 'Оптимизировано ✔' : 'Оптимизировать'}</span>
        </button>

        <button
          onClick={runTests}
          disabled={isRunningTests}
          className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-md text-xs font-medium text-emerald-300 bg-emerald-950/60 hover:bg-emerald-900/60 border border-emerald-600/40 transition shrink-0 whitespace-nowrap"
        >
          <Play className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
          <span>{isRunningTests ? 'Запуск...' : 'Запуск'}</span>
        </button>

        {/* 2. Позиционирование кнопки «🐞 Дебаг» (Слайд 4, Слайд 8) */}
        <button
          onClick={isDebugging ? stopDebug : startDebug}
          className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-md text-xs font-medium transition border shrink-0 whitespace-nowrap ${
            isDebugging
              ? 'bg-amber-500 text-black border-amber-400 font-bold shadow-lg shadow-amber-500/20'
              : 'text-zinc-300 hover:text-white bg-zinc-800/80 hover:bg-zinc-700/80 border-border'
          }`}
          title="Обход узлов графа без исполнения Java"
        >
          <Bug className={`w-3.5 h-3.5 shrink-0 ${isDebugging ? 'text-black' : 'text-amber-400'}`} />
          <span>{isDebugging ? 'Стоп обход' : 'Обход графа'}</span>
        </button>

        <div className="h-4 w-px bg-border mx-0.5 shrink-0" />

        {/* Refresh Backend Status */}
        <button
          onClick={checkBackend}
          className="p-1.5 rounded-md text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800 transition shrink-0"
          title="Обновить связь с бэкендом"
        >
          <RefreshCw className="w-3.5 h-3.5 shrink-0" />
        </button>

        {/* 4. Кнопка смены темы (справа сверху) */}
        <button
          onClick={toggleTheme}
          className="p-1.5 rounded-md text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800 transition shrink-0"
          title={theme === 'dark' ? 'Переключить на светлую тему' : 'Переключить на тёмную тему'}
        >
          {theme === 'dark' ? (
            <Moon className="w-4 h-4 text-blue-300" />
          ) : (
            <Sun className="w-4 h-4 text-amber-400" />
          )}
        </button>
      </div>
    </header>
  );
};
