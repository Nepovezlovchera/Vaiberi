import React, { useState } from 'react';
import { FileExplorer } from './FileExplorer';
import { BlocksPalette } from './BlocksPalette';
import { FolderTree, Component } from 'lucide-react';

export const LeftSidebar: React.FC = () => {
  const [activeSection, setActiveSection] = useState<'both' | 'files' | 'blocks'>('both');

  return (
    <div className="w-full lg:w-64 lg:shrink-0 h-full bg-surface border-r border-border flex flex-col overflow-hidden select-none">
      {/* Top Section: Проект */}
      <div className="flex flex-col flex-1 min-h-[40%] border-b border-border overflow-hidden">
        <div className="flex items-center justify-between px-3 py-2 bg-surface-elevated/70 border-b border-border text-xs font-semibold text-zinc-300">
          <div className="flex items-center gap-2">
            <FolderTree className="w-3.5 h-3.5 text-[#f37022]" />
            <span>Проект</span>
          </div>
          <span className="text-[10px] text-zinc-500 font-mono">src/com.example</span>
        </div>
        <div className="flex-1 overflow-y-auto">
          <FileExplorer />
        </div>
      </div>

      {/* Bottom Section: Блоки */}
      <div className="flex flex-col flex-1 min-h-[40%] overflow-hidden">
        <div className="flex items-center justify-between px-3 py-2 bg-surface-elevated/70 border-b border-border text-xs font-semibold text-zinc-300">
          <div className="flex items-center gap-2">
            <Component className="w-3.5 h-3.5 text-[#77239b] dark:text-[#a855f7]" />
            <span>Блоки палитры</span>
          </div>
          <span className="text-[10px] text-zinc-500">Drag & Drop</span>
        </div>
        <div className="flex-1 overflow-y-auto">
          <BlocksPalette />
        </div>
      </div>
    </div>
  );
};
