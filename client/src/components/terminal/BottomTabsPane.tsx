import React from 'react';
import { useStudioStore } from '../../store/useStudioStore';
import { MonacoEditorPane } from '../editor/MonacoEditorPane';
import { ConsolePane } from './ConsolePane';
import { TestsPane } from './TestsPane';
import { Code, Terminal, CheckCircle, Columns3, LayoutList } from 'lucide-react';

export const BottomTabsPane: React.FC = () => {
  const { bottomTab, setBottomTab, tests, bottomLayoutMode, setBottomLayoutMode } = useStudioStore();
  const passedCount = tests.filter((t) => t.status === 'PASSED').length;

  return (
    <div className="w-full h-full flex flex-col bg-surface border-t border-border overflow-hidden">
      {/* Header bar with Tab switchers and 3-Column Split Mode switch (Слайд 4 ТЗ) */}
      <div className="flex items-center justify-between px-2 bg-surface-elevated border-b border-border h-9 shrink-0">
        <div className="flex items-center gap-1">
          <button
            onClick={() => {
              setBottomLayoutMode('tabs');
              setBottomTab('code');
            }}
            className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-t transition border-b-2 ${
              bottomLayoutMode === 'tabs' && bottomTab === 'code'
                ? 'text-zinc-100 border-[#f37022] bg-surface font-semibold'
                : 'text-zinc-400 border-transparent hover:text-zinc-200 hover:bg-surface/50'
            }`}
          >
            <Code className="w-3.5 h-3.5 text-[#f37022]" />
            <span>Код Java</span>
          </button>

          <button
            onClick={() => {
              setBottomLayoutMode('tabs');
              setBottomTab('console');
            }}
            className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-t transition border-b-2 ${
              bottomLayoutMode === 'tabs' && bottomTab === 'console'
                ? 'text-zinc-100 border-[#f37022] bg-surface font-semibold'
                : 'text-zinc-400 border-transparent hover:text-zinc-200 hover:bg-surface/50'
            }`}
          >
            <Terminal className="w-3.5 h-3.5 text-amber-400" />
            <span>Консоль</span>
          </button>

          <button
            onClick={() => {
              setBottomLayoutMode('tabs');
              setBottomTab('tests');
            }}
            className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-t transition border-b-2 ${
              bottomLayoutMode === 'tabs' && bottomTab === 'tests'
                ? 'text-zinc-100 border-[#f37022] bg-surface font-semibold'
                : 'text-zinc-400 border-transparent hover:text-zinc-200 hover:bg-surface/50'
            }`}
          >
            <CheckCircle className="w-3.5 h-3.5 text-emerald-400" />
            <span>Результат тестов ({passedCount}/{tests.length})</span>
          </button>
        </div>

        {/* View Mode Toggle: 3 Columns (Slide 4) vs Single Tab */}
        <div className="flex items-center bg-surface border border-border rounded p-0.5 text-[11px]">
          <button
            onClick={() => setBottomLayoutMode('split3')}
            className={`flex items-center gap-1.5 px-2.5 py-1 rounded transition font-medium ${
              bottomLayoutMode === 'split3'
                ? 'bg-[#f37022] text-white shadow-sm font-semibold'
                : 'text-zinc-400 hover:text-zinc-200'
            }`}
            title="Отобразить одновременно Код, Консоль и Тесты (точная копия Слайда 4 ТЗ)"
          >
            <Columns3 className="w-3.5 h-3.5" />
            <span>3 колонки (Слайд 4)</span>
          </button>

          <button
            onClick={() => setBottomLayoutMode('tabs')}
            className={`flex items-center gap-1.5 px-2.5 py-1 rounded transition font-medium ${
              bottomLayoutMode === 'tabs'
                ? 'bg-[#f37022] text-white shadow-sm font-semibold'
                : 'text-zinc-400 hover:text-zinc-200'
            }`}
            title="Компактный режим вкладок на весь экран"
          >
            <LayoutList className="w-3.5 h-3.5" />
            <span>Вкладки</span>
          </button>
        </div>
      </div>

      {/* Content Area */}
      <div className="flex-1 w-full h-[calc(100%-36px)] overflow-hidden">
        {bottomLayoutMode === 'split3' ? (
          <div className="w-full h-full flex divide-x divide-border overflow-hidden">
            {/* Column 1: Monaco Code Editor with Tabs (40%) */}
            <div className="w-[42%] h-full overflow-hidden flex flex-col">
              <MonacoEditorPane />
            </div>

            {/* Column 2: Sandbox JVM Terminal (28%) */}
            <div className="w-[28%] min-w-0 h-full overflow-hidden flex flex-col">
              <ConsolePane />
            </div>

            {/* Column 3: Quality Gate Test Suite (30%) */}
            <div className="w-[30%] min-w-0 h-full overflow-hidden flex flex-col">
              <TestsPane />
            </div>
          </div>
        ) : (
          <div className="w-full h-full overflow-hidden">
            {bottomTab === 'code' && <MonacoEditorPane />}
            {bottomTab === 'console' && <ConsolePane />}
            {bottomTab === 'tests' && <TestsPane />}
          </div>
        )}
      </div>
    </div>
  );
};
