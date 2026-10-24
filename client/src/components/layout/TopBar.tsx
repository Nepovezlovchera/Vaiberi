import React, { useEffect, useRef } from 'react';
import { useStudioStore } from '../../store/useStudioStore';
import { SAMPLE_MONOLITH_CODE, SAMPLE_MONOLITH_TESTS } from '../../store/monolithData';
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
    runCodeInConsole,
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

  const MAX_JAVA_FILE_SIZE = 2 * 1024 * 1024; // 2 MB limit (Client-side DoS prevention)

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const list = Array.from(e.target.files || []).filter((f) => /\.(java|json)$/i.test(f.name));
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
      try {
        const sources = loaded.flatMap(f => {
          if (!f.name.endsWith('.json')) return [f];
          const project = JSON.parse(f.content);
          if (!Array.isArray(project.files) || !project.files.every((file: any) =>
            typeof file.path === 'string' && file.path.endsWith('.java') && typeof file.source === 'string')) {
            throw new Error('Некорректный файл проекта');
          }
          return project.files.map((file: any) => ({ name: file.path, content: file.source }));
        });
        await importProject(sources);
      } catch (error: any) { addConsoleLog(`[ERROR] Импорт: ${error.message}`); }
    }
    input.value = '';
  };

  const handleDemo = async () => {
    await importProject([{ name: 'Main.java', content: SAMPLE_MONOLITH_CODE }]);
    useStudioStore.setState({ tests: SAMPLE_MONOLITH_TESTS.map(t => ({ ...t, status: 'PENDING' })) });
    await runOptimization();
  };

  const handleExport = () => {
    const files = useStudioStore.getState().getProjectFiles();
    const multiple = files.length > 1;
    const content = multiple ? JSON.stringify({ files }, null, 2) : files[0].source;
    const blob = new Blob([content], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = multiple ? 'java-project.json' : files[0].path.split('/').pop()!;
    link.click();
    URL.revokeObjectURL(url);
  };

  return (
    <header className="h-12 w-full bg-surface border-b border-border flex items-center justify-between px-3 shrink-0 select-none gap-2 overflow-x-auto no-scrollbar">
      {/* Hidden File Input */}
      <input ref={fileInputRef} type="file" accept=".java,.json" multiple onChange={handleFileChange} className="hidden" />
      <button onClick={handleDemo} disabled={!isBackendOnline || isCompiling || isRunningTests}
        className="px-2 py-1 text-xs rounded bg-blue-600 text-white disabled:opacity-50 whitespace-nowrap">
        Демо ТЗ
      </button>

      {/* Brand / Logo */}
      <div className="flex items-center gap-2.5 shrink-0 min-w-0">
        <div className="flex items-center gap-2 shrink-0">
          <div className="w-7 h-7 rounded-lg bg-[#f37022] flex items-center justify-center shrink-0 shadow-sm shadow-[#f37022]/30">
            <Workflow className="w-4 h-4 text-white" />
          </div>
          <div className="flex items-center">
            <span className="font-bold text-sm text-zinc-100 hidden 2xl:inline whitespace-nowrap">
              Визуальная среда программирования
            </span>
            <span className="2xl:ml-2 text-[10px] text-[#f37022] dark:text-[#ff7b29] font-mono font-semibold px-2 py-0.5 rounded bg-[#f37022]/10 dark:bg-[#f37022]/20 border border-[#f37022]/30 whitespace-nowrap">
              Java Blueprints
            </span>
          </div>
        </div>

        <div className="h-4 w-px bg-border mx-0.5 shrink-0" />

        {/* Live Backend Indicator (Green if online, Red if offline) */}
        <div
          onClick={checkBackend}
          className="cursor-pointer transition-transform hover:scale-110 shrink-0 flex items-center p-1"
          title={isBackendOnline ? 'Бэкенд онлайн (:8080). Нажмите для повторной проверки' : 'Бэкенд оффлайн. Нажмите для повторной проверки связи'}
        >
          {isBackendOnline ? (
            <span className="relative flex h-2.5 w-2.5">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75" />
              <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-emerald-500 shadow-sm shadow-emerald-500/50" />
            </span>
          ) : (
            <span className="relative flex h-2.5 w-2.5">
              <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-rose-500 shadow-sm shadow-rose-500/50" />
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
          <UploadCloud className="w-3.5 h-3.5 text-[#f37022] shrink-0" />
          <span className="hidden xl:inline">Импорт Java</span>
        </button>

        <button
          onClick={handleExport}
          className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-md text-xs font-medium text-zinc-300 hover:text-white bg-zinc-800/80 hover:bg-zinc-700/80 border border-border transition shrink-0 whitespace-nowrap"
          title="Экспорт Java файла"
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
              : 'bg-[#f37022] hover:bg-[#ff6500] active:bg-[#c5621b] text-white border border-[#f37022] shadow-sm shadow-[#f37022]/20'
          }`}
        >
          <Sparkles className="w-3.5 h-3.5 text-white shrink-0" />
          <span>{isCompiling ? 'Оптимизация...' : isOptimized ? 'Оптимизировано ✔' : 'Оптимизировать'}</span>
        </button>

        <button
          onClick={() => runCodeInConsole()}
          title="Скомпилировать и запустить программу; вывод — в терминале JVM (ввод — прямо в терминале, когда программа его ждёт)"
          className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-md text-xs font-medium text-emerald-300 bg-emerald-950/60 hover:bg-emerald-900/60 border border-emerald-600/40 transition shrink-0 whitespace-nowrap"
        >
          <Play className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
          <span>Запуск</span>
        </button>

        {/* 2. Позиционирование кнопки «🐞 Дебаг» (Слайд 4, Слайд 8) */}
        <button
          onClick={isDebugging ? stopDebug : startDebug}
          className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-md text-xs font-medium transition border shrink-0 whitespace-nowrap ${
            isDebugging
              ? 'bg-amber-500 text-black border-amber-400 font-bold shadow-lg shadow-amber-500/20'
              : 'text-zinc-300 hover:text-white bg-zinc-800/80 hover:bg-zinc-700/80 border-border'
          }`}
          title="Визуальный трассировщик потока управления (Visual CFG Tracer)"
        >
          <Bug className={`w-3.5 h-3.5 shrink-0 ${isDebugging ? 'text-black' : 'text-amber-400'}`} />
          <span>{isDebugging ? 'Стоп трассировка' : 'Дебаг'}</span>
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
            <Moon className="w-4 h-4 text-[#c4a4e8]" />
          ) : (
            <Sun className="w-4 h-4 text-[#f37022]" />
          )}
        </button>
      </div>
    </header>
  );
};
