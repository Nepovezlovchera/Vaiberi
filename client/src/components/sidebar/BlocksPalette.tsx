import React, { useState } from 'react';
import { Layers, HelpCircle, Repeat, Code2, PlayCircle, CornerDownLeft, Search, GripVertical } from 'lucide-react';

interface PaletteBlock {
  type: string;
  label: string;
  icon: React.ReactNode;
  colorClass: string;
}

const PALETTE_BLOCKS: PaletteBlock[] = [
  {
    type: 'var',
    label: 'Переменная',
    icon: <Layers className="w-3.5 h-3.5 text-sky-400" />,
    colorClass: 'border-sky-500/30 hover:border-sky-400 bg-sky-950/20',
  },
  {
    type: 'condition',
    label: 'Условие',
    icon: <HelpCircle className="w-3.5 h-3.5 text-amber-400" />,
    colorClass: 'border-amber-500/30 hover:border-amber-400 bg-amber-950/20',
  },
  {
    type: 'loop',
    label: 'Цикл',
    icon: <Repeat className="w-3.5 h-3.5 text-blue-400" />,
    colorClass: 'border-blue-500/30 hover:border-blue-400 bg-blue-950/20',
  },
  {
    type: 'method',
    label: 'Метод',
    icon: <Code2 className="w-3.5 h-3.5 text-purple-400" />,
    colorClass: 'border-purple-500/30 hover:border-purple-400 bg-purple-950/20',
  },
  {
    type: 'op',
    label: 'Операция',
    icon: <PlayCircle className="w-3.5 h-3.5 text-indigo-400" />,
    colorClass: 'border-indigo-500/30 hover:border-indigo-400 bg-indigo-950/20',
  },
  {
    type: 'return',
    label: 'Возврат',
    icon: <CornerDownLeft className="w-3.5 h-3.5 text-emerald-400" />,
    colorClass: 'border-emerald-500/30 hover:border-emerald-400 bg-emerald-950/20',
  },
];

export const BlocksPalette: React.FC = () => {
  const [search, setSearch] = useState('');

  const onDragStart = (event: React.DragEvent, nodeType: string) => {
    event.dataTransfer.setData('application/reactflow', nodeType);
    event.dataTransfer.effectAllowed = 'move';
  };

  const filtered = PALETTE_BLOCKS.filter((b) => b.label.toLowerCase().includes(search.toLowerCase()));

  return (
    <div className="flex flex-col h-full p-2 select-none">
      {/* Search Bar */}
      <div className="relative mb-2">
        <Search className="w-3.5 h-3.5 absolute left-2.5 top-1/2 -translate-y-1/2 text-zinc-500" />
        <input
          type="text"
          placeholder="Поиск блоков..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="w-full pl-8 pr-2 py-1 bg-zinc-900 border border-border rounded text-xs text-zinc-200 placeholder-zinc-500 focus:outline-none focus:border-blue-500"
        />
      </div>

      {/* Palette Items */}
      <div className="space-y-1.5 overflow-y-auto pr-1">
        {filtered.map((block) => (
          <div
            key={block.type}
            draggable
            onDragStart={(e) => onDragStart(e, block.type)}
            className={`flex items-center justify-between p-2 rounded-md border text-xs font-medium cursor-grab active:cursor-grabbing transition-all hover:scale-[1.02] shadow-sm ${block.colorClass}`}
          >
            <div className="flex items-center gap-2 text-zinc-200">
              {block.icon}
              <span>{block.label}</span>
            </div>
            <GripVertical className="w-3.5 h-3.5 text-zinc-600" />
          </div>
        ))}
      </div>
    </div>
  );
};
