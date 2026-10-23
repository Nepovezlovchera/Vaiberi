import React from 'react';
import { useStudioStore } from '../../store/useStudioStore';
import { MonacoEditorPane } from '../editor/MonacoEditorPane';
import { ConsolePane } from './ConsolePane';
import { TestsPane } from './TestsPane';
import { Code, Terminal, CheckCircle } from 'lucide-react';

export const BottomTabsPane: React.FC = () => {
  const { bottomTab, setBottomTab, tests } = useStudioStore();
  const passedCount = tests.filter((t) => t.status === 'PASSED').length;

  return (
    <div className="w-full h-full flex flex-col bg-surface border-t border-border overflow-hidden">
      {/* Tabs Header */}
      <div className="flex items-center px-2 bg-surface-elevated border-b border-border h-9 shrink-0 gap-1">
        <button
          onClick={() => setBottomTab('code')}
          className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-t transition border-b-2 ${
            bottomTab === 'code'
              ? 'text-white border-blue-500 bg-surface'
              : 'text-zinc-400 border-transparent hover:text-zinc-200 hover:bg-zinc-800/40'
          }`}
        >
          <Code className="w-3.5 h-3.5 text-blue-400" />
          <span>Код Java</span>
        </button>

        <button
          onClick={() => setBottomTab('console')}
          className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-t transition border-b-2 ${
            bottomTab === 'console'
              ? 'text-white border-blue-500 bg-surface'
              : 'text-zinc-400 border-transparent hover:text-zinc-200 hover:bg-zinc-800/40'
          }`}
        >
          <Terminal className="w-3.5 h-3.5 text-amber-400" />
          <span>Консоль</span>
        </button>

        <button
          onClick={() => setBottomTab('tests')}
          className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-t transition border-b-2 ${
            bottomTab === 'tests'
              ? 'text-white border-blue-500 bg-surface'
              : 'text-zinc-400 border-transparent hover:text-zinc-200 hover:bg-zinc-800/40'
          }`}
        >
          <CheckCircle className="w-3.5 h-3.5 text-emerald-400" />
          <span>Результат тестов ({passedCount}/{tests.length})</span>
        </button>
      </div>

      {/* Tabs Content */}
      <div className="flex-1 w-full h-[calc(100%-36px)] overflow-hidden">
        {bottomTab === 'code' && <MonacoEditorPane />}
        {bottomTab === 'console' && <ConsolePane />}
        {bottomTab === 'tests' && <TestsPane />}
      </div>
    </div>
  );
};
