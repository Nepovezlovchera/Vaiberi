import React from 'react';
import { useStudioStore } from '../../store/useStudioStore';
import { FileCode, X, Plus } from 'lucide-react';

export const EditorTabs: React.FC = () => {
  const { openTabs, activeTabId, setActiveTabId, closeFileTab, openFileTab } = useStudioStore();

  const handleCreateScratch = () => {
    const nextIdx = openTabs.length + 1;
    openFileTab({
      id: `tab-scratch-${Date.now()}`,
      name: `NewClass${nextIdx}.java`,
      path: `src/com.example/NewClass${nextIdx}.java`,
      code: `package com.example;\n\npublic class NewClass${nextIdx} {\n    public static void main(String[] args) {\n        System.out.println("Hello from NewClass${nextIdx}");\n    }\n}\n`,
    });
  };

  return (
    <div className="flex items-center bg-surface-elevated border-b border-border h-8 shrink-0 px-1 overflow-x-auto select-none no-scrollbar">
      <div className="flex items-center gap-0.5">
        {openTabs.map((tab) => {
          const isActive = tab.id === activeTabId;
          return (
            <div
              key={tab.id}
              onClick={() => setActiveTabId(tab.id)}
              className={`group flex items-center gap-1.5 px-3 py-1 text-xs cursor-pointer rounded-t transition border-t-2 ${
                isActive
                  ? 'bg-surface text-zinc-100 border-[#f37022] font-semibold shadow-sm'
                  : 'bg-transparent text-zinc-400 border-transparent hover:bg-surface/50 hover:text-zinc-200'
              }`}
            >
              <FileCode className={`w-3.5 h-3.5 ${isActive ? 'text-[#f37022]' : 'text-zinc-500'}`} />
              <span className="truncate max-w-[120px] font-mono text-[11px]">{tab.name}</span>
              {openTabs.length > 1 && (
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    closeFileTab(tab.id);
                  }}
                  className="opacity-0 group-hover:opacity-100 p-0.5 hover:bg-zinc-700/80 rounded transition text-zinc-400 hover:text-zinc-200 ml-0.5"
                  title="Закрыть вкладку"
                >
                  <X className="w-3 h-3" />
                </button>
              )}
            </div>
          );
        })}
      </div>

      <button
        onClick={handleCreateScratch}
        className="p-1 ml-1 text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800/60 rounded transition"
        title="Создать новый Java класс (Слайд 4 ТЗ)"
      >
        <Plus className="w-3.5 h-3.5" />
      </button>
    </div>
  );
};
