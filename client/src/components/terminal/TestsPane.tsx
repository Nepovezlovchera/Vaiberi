import React, { useState, useRef } from 'react';
import { useStudioStore } from '../../store/useStudioStore';
import {
  CheckCircle2,
  XCircle,
  Play,
  Clock,
  ShieldCheck,
  Plus,
  UploadCloud,
  RotateCcw,
  Trash2,
  ChevronDown,
  ChevronRight,
  Terminal,
  Save,
  AlertTriangle,
} from 'lucide-react';

export const TestsPane: React.FC = () => {
  const {
    tests,
    isRunningTests,
    runTests,
    isOptimized,
    addTestCase,
    updateTestCase,
    removeTestCase,
    loadTestFiles,
    resetDefaultTests,
  } = useStudioStore();

  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [showAddForm, setShowAddForm] = useState<boolean>(false);
  const [newTestName, setNewTestName] = useState<string>('');
  const [newTestStdin, setNewTestStdin] = useState<string>('');
  const [isDragOver, setIsDragOver] = useState<boolean>(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const passedCount = tests.filter((t) => t.status === 'PASSED').length;
  const totalCount = tests.length;
  const percent = totalCount > 0 ? Math.round((passedCount / totalCount) * 100) : 0;

  const handleCreateTest = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newTestName.trim() && !newTestStdin.trim()) return;
    addTestCase(newTestName || `testCustom_${tests.length + 1}`, newTestStdin);
    setNewTestName('');
    setNewTestStdin('');
    setShowAddForm(false);
  };

  const MAX_TEST_FILE_SIZE = 5 * 1024 * 1024; // 5 MB limit (Client-side DoS prevention)

  const processFiles = (fileList: FileList | File[]) => {
    const validFiles: File[] = [];
    Array.from(fileList).forEach((file) => {
      if (file.size > MAX_TEST_FILE_SIZE) {
        useStudioStore.getState().addConsoleLog(`[SECURITY WARNING] Файл «${file.name}» пропущен: размер превышает лимит 5 МБ.`);
      } else {
        validFiles.push(file);
      }
    });

    if (validFiles.length === 0) return;

    const readFiles: { name: string; content: string }[] = [];
    let count = 0;

    validFiles.forEach((file) => {
      const reader = new FileReader();
      reader.onload = (event) => {
        const content = event.target?.result as string;
        // Sanitize filename
        const safeName = file.name.replace(/[^a-zA-Z0-9._-]/g, '_');
        readFiles.push({ name: safeName, content: content || '' });
        count++;
        if (count === validFiles.length) {
          loadTestFiles(readFiles);
        }
      };
      reader.readAsText(file);
    });
  };

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files || files.length === 0) return;
    processFiles(files);
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragOver(true);
  };

  const handleDragLeave = () => {
    setIsDragOver(false);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragOver(false);
    const files = e.dataTransfer.files;
    if (!files || files.length === 0) return;
    processFiles(files);
  };

  return (
    <div
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
      className={`w-full h-full bg-[#0c0c0e] flex flex-col p-3 overflow-hidden select-text text-xs relative transition-colors ${
        isDragOver ? 'bg-blue-950/30 ring-2 ring-blue-500 ring-inset' : ''
      }`}
    >
      {/* Hidden file input for .in files */}
      <input
        ref={fileInputRef}
        type="file"
        multiple
        accept=".in,.txt"
        onChange={handleFileUpload}
        className="hidden"
      />

      {/* Quality Gate Header Banner */}
      <div className="flex flex-wrap items-center justify-between gap-2 pb-2.5 border-b border-border mb-2.5">
        <div className="flex items-center gap-2.5 flex-wrap">
          <div className="flex items-center gap-1.5">
            <ShieldCheck className="w-4 h-4 text-emerald-400" />
            <span className="font-semibold text-zinc-200">Quality Gate: Сохранение поведения</span>
          </div>

          <span className="px-2 py-0.5 rounded bg-emerald-950/80 border border-emerald-500/40 text-emerald-300 font-bold text-[11px]">
            {passedCount} из {totalCount} ({percent}%)
          </span>

          {isOptimized && (
            <span className="px-2 py-0.5 rounded bg-blue-950/80 border border-blue-500/40 text-blue-300 font-semibold text-[11px]">
              ⚡ Ускорение 5.2x
            </span>
          )}
        </div>

        {/* Toolbar Buttons */}
        <div className="flex items-center gap-1.5">
          <button
            onClick={() => fileInputRef.current?.click()}
            className="flex items-center gap-1 px-2.5 py-1 bg-zinc-900 hover:bg-zinc-800 text-zinc-300 border border-border rounded text-[11px] font-medium transition"
            title="Загрузить один или несколько файлов .in"
          >
            <UploadCloud className="w-3.5 h-3.5 text-blue-400" />
            <span>Загрузить .in</span>
          </button>

          <button
            onClick={() => setShowAddForm((v) => !v)}
            className="flex items-center gap-1 px-2.5 py-1 bg-zinc-900 hover:bg-zinc-800 text-zinc-300 border border-border rounded text-[11px] font-medium transition"
            title="Добавить тестовый сценарий со своим stdin"
          >
            <Plus className="w-3.5 h-3.5 text-emerald-400" />
            <span>{showAddForm ? 'Скрыть форму' : '+ Тест (stdin)'}</span>
          </button>

          <button
            onClick={resetDefaultTests}
            className="p-1 text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800 rounded border border-border transition"
            title="Сбросить к стандартному набору тестов"
          >
            <RotateCcw className="w-3.5 h-3.5" />
          </button>

          <button
            onClick={runTests}
            disabled={isRunningTests}
            className="flex items-center gap-1.5 px-3 py-1 bg-emerald-600 hover:bg-emerald-500 text-white rounded font-medium text-[11px] transition disabled:opacity-50 shadow-sm"
          >
            <Play className={`w-3.5 h-3.5 ${isRunningTests ? 'animate-spin text-white' : ''}`} />
            <span>{isRunningTests ? 'Прогон...' : 'Запустить тесты'}</span>
          </button>
        </div>
      </div>

      {/* Inline Form: Add Custom Test */}
      {showAddForm && (
        <form
          onSubmit={handleCreateTest}
          className="mb-3 p-3 bg-zinc-900/90 border border-blue-500/40 rounded-lg space-y-2.5 shadow-lg animate-in fade-in"
        >
          <div className="flex items-center justify-between">
            <span className="font-semibold text-zinc-200 flex items-center gap-1.5">
              <Terminal className="w-3.5 h-3.5 text-blue-400" />
              <span>Новый тестовый сценарий</span>
            </span>
            <span className="text-[10px] text-zinc-400">Входные данные передаются в System.in монолита</span>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-4 gap-2">
            <div className="md:col-span-1">
              <label className="block text-[10px] text-zinc-400 mb-1">Имя теста</label>
              <input
                type="text"
                placeholder="testCustomInput"
                value={newTestName}
                onChange={(e) => setNewTestName(e.target.value)}
                className="w-full px-2.5 py-1.5 bg-black border border-border rounded text-xs text-zinc-200 placeholder-zinc-600 focus:outline-none focus:border-blue-500 font-mono"
              />
            </div>

            <div className="md:col-span-3">
              <label className="block text-[10px] text-zinc-400 mb-1">Содержимое System.in (stdin)</label>
              <textarea
                rows={3}
                placeholder={"Пример:\n3\n1 Иван 10.5\n2 Ольга 20.0\n3 Анна 50.0"}
                value={newTestStdin}
                onChange={(e) => setNewTestStdin(e.target.value)}
                className="w-full px-2.5 py-1.5 bg-black border border-border rounded text-xs text-zinc-200 placeholder-zinc-600 focus:outline-none focus:border-blue-500 font-mono resize-y"
              />
            </div>
          </div>

          <div className="flex items-center justify-end gap-2 pt-1 border-t border-border/40">
            <button
              type="button"
              onClick={() => setShowAddForm(false)}
              className="px-2.5 py-1 text-zinc-400 hover:text-zinc-200 text-xs transition"
            >
              Отмена
            </button>
            <button
              type="submit"
              className="px-3 py-1 bg-blue-600 hover:bg-blue-500 text-white rounded font-medium text-xs transition flex items-center gap-1.5"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>Добавить в набор</span>
            </button>
          </div>
        </form>
      )}

      {/* Progress Bar */}
      <div className="w-full h-1.5 bg-zinc-800 rounded-full mb-2.5 overflow-hidden shrink-0">
        <div
          className={`h-full transition-all duration-500 ${
            percent === 100 ? 'bg-emerald-500' : percent > 50 ? 'bg-amber-500' : 'bg-rose-500'
          }`}
          style={{ width: `${percent}%` }}
        />
      </div>

      {/* Tests Table */}
      <div className="flex-1 overflow-y-auto pr-1">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="border-b border-border/80 text-[11px] font-medium text-zinc-400 sticky top-0 bg-[#0c0c0e] z-10">
              <th className="py-1 px-2 w-8"></th>
              <th className="py-1 px-2">Тестовый сценарий</th>
              <th className="py-1 px-2">Статус поведения</th>
              <th className="py-1 px-2 text-right">Время</th>
              <th className="py-1 px-2 w-10 text-right">Действия</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border/30 font-mono text-[11px]">
            {tests.map((test) => {
              const isExpanded = expandedId === test.id;
              const isPassed = test.status === 'PASSED';
              const isRunning = test.status === 'RUNNING';
              const isFailed = test.status === 'FAILED';

              return (
                <React.Fragment key={test.id}>
                  <tr
                    onClick={() => setExpandedId(isExpanded ? null : test.id)}
                    className="hover:bg-zinc-900/60 cursor-pointer transition select-none group"
                  >
                    <td className="py-1.5 px-2 text-zinc-500">
                      {isExpanded ? (
                        <ChevronDown className="w-3.5 h-3.5 text-zinc-300" />
                      ) : (
                        <ChevronRight className="w-3.5 h-3.5 text-zinc-600 group-hover:text-zinc-400" />
                      )}
                    </td>

                    <td className="py-1.5 px-2 text-zinc-200">
                      <div className="flex items-center gap-2">
                        {isPassed && <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400 shrink-0" />}
                        {isFailed && <XCircle className="w-3.5 h-3.5 text-rose-400 shrink-0" />}
                        {isRunning && (
                          <div className="w-3.5 h-3.5 rounded-full border-2 border-amber-400 border-t-transparent animate-spin shrink-0" />
                        )}
                        {!isPassed && !isFailed && !isRunning && (
                          <Clock className="w-3.5 h-3.5 text-zinc-500 shrink-0" />
                        )}
                        <span className="font-sans font-medium text-xs">{test.name}</span>
                        {test.stdin && (
                          <span className="text-[10px] px-1.5 py-0.2 rounded bg-zinc-800 text-zinc-400 font-mono">
                            stdin: {test.stdin.trim().split('\n').length} строк
                          </span>
                        )}
                      </div>
                    </td>

                    <td className="py-1.5 px-2 font-sans">
                      {isPassed && (
                        <span className="text-emerald-400 font-medium text-xs">Успешно (Совпадает ✔)</span>
                      )}
                      {isFailed && (
                        <span className="text-rose-400 font-medium text-xs">Расхождение вывода ❌</span>
                      )}
                      {isRunning && (
                        <span className="text-amber-400 font-medium text-xs">Выполнение...</span>
                      )}
                      {!isPassed && !isFailed && !isRunning && (
                        <span className="text-zinc-500 text-xs">Ожидает запуска</span>
                      )}
                    </td>

                    <td className="py-1.5 px-2 text-right text-zinc-400">
                      {test.durationMs > 0 ? (
                        <span className="inline-flex items-center gap-1">
                          <Clock className="w-3 h-3 text-zinc-500" />
                          {test.durationMs >= 1000
                            ? `${(test.durationMs / 1000).toFixed(2)} сек`
                            : `${test.durationMs} мс`}
                        </span>
                      ) : (
                        <span className="text-zinc-600">—</span>
                      )}
                    </td>

                    <td
                      className="py-1.5 px-2 text-right"
                      onClick={(e) => {
                        e.stopPropagation();
                        removeTestCase(test.id);
                      }}
                    >
                      <button
                        title="Удалить тест"
                        className="p-1 text-zinc-500 hover:text-rose-400 hover:bg-rose-950/40 rounded transition"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </td>
                  </tr>

                  {/* Expanded Stdin Editor Row */}
                  {isExpanded && (
                    <tr className="bg-zinc-950/80">
                      <td colSpan={5} className="p-3">
                        <div className="space-y-2 border-l-2 border-blue-500 pl-3">
                          <div className="flex items-center justify-between text-[11px] text-zinc-400">
                            <span className="font-semibold text-zinc-300">
                              Входные данные (stdin для System.in):
                            </span>
                            <span className="text-[10px] text-zinc-500">
                              Отредактируйте текст и нажмите «Сохранить stdin»
                            </span>
                          </div>

                          <textarea
                            rows={Math.min(8, Math.max(3, (test.stdin || '').split('\n').length + 1))}
                            value={test.stdin || ''}
                            onChange={(e) => updateTestCase(test.id, { stdin: e.target.value })}
                            className="w-full px-2.5 py-1.5 bg-black border border-zinc-800 rounded text-xs text-zinc-200 font-mono focus:outline-none focus:border-blue-500"
                          />

                          {test.errorDetails && (
                            <div className="p-2.5 rounded bg-rose-950/30 border border-rose-600/40 text-rose-300 flex items-start gap-2 text-xs">
                              <AlertTriangle className="w-4 h-4 text-rose-400 shrink-0 mt-0.5" />
                              <div className="font-mono text-[11px] break-all">{test.errorDetails}</div>
                            </div>
                          )}

                          <div className="flex items-center justify-between pt-1">
                            <span className="text-[10px] text-zinc-500">
                              Тест будет отправлен на Quality Gate со всеми строками выше
                            </span>
                            <button
                              type="button"
                              onClick={() => {
                                setExpandedId(null);
                              }}
                              className="flex items-center gap-1 px-2.5 py-0.5 bg-zinc-800 hover:bg-zinc-700 text-zinc-200 rounded text-[11px] transition font-sans"
                            >
                              <Save className="w-3 h-3 text-emerald-400" />
                              <span>Готово</span>
                            </button>
                          </div>
                        </div>
                      </td>
                    </tr>
                  )}
                </React.Fragment>
              );
            })}
          </tbody>
        </table>

        {tests.length === 0 && (
          <div className="p-8 text-center text-zinc-500 space-y-2">
            <UploadCloud className="w-8 h-8 mx-auto text-zinc-600" />
            <div>Нет активных тестов</div>
            <div className="text-[11px]">
              Загрузите файлы <code>.in</code>, перетащите их сюда мышкой или нажмите «+ Тест (stdin)».
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
