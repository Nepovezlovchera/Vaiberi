import React from 'react';
import { Handle, Position, NodeProps } from '@xyflow/react';
import { CornerDownRight, SkipForward, Ban } from 'lucide-react';

const META: Record<string, { label: string; Icon: typeof Ban; text: string; border: string; selected: string; bg: string }> = {
  break: {
    label: 'break',
    Icon: CornerDownRight,
    text: 'text-orange-300',
    border: 'border-orange-500/30 hover:border-orange-500/60',
    selected: 'border-orange-400 ring-2 ring-orange-500/30',
    bg: 'bg-orange-950/30',
  },
  continue: {
    label: 'continue',
    Icon: SkipForward,
    text: 'text-cyan-300',
    border: 'border-cyan-500/30 hover:border-cyan-500/60',
    selected: 'border-cyan-400 ring-2 ring-cyan-500/30',
    bg: 'bg-cyan-950/30',
  },
  throw: {
    label: 'throw',
    Icon: Ban,
    text: 'text-rose-300',
    border: 'border-rose-500/30 hover:border-rose-500/60',
    selected: 'border-rose-400 ring-2 ring-rose-500/30',
    bg: 'bg-rose-950/30',
  },
};

export const JumpNode: React.FC<NodeProps> = ({ data, selected, type }) => {
  const m = META[type] ?? META.break;
  return (
    <div
      className={`relative min-w-[220px] rounded-lg bg-surface border transition-all duration-200 shadow-md ${
        selected ? m.selected : m.border
      }`}
    >
      <Handle
        type="target"
        position={Position.Top}
        className="!bg-zinc-400 !w-3 !h-3 !border-2 !border-zinc-950 hover:!scale-125 transition-transform"
      />

      <div className={`flex items-center gap-1.5 px-3 py-2 ${m.bg} rounded-lg`}>
        <m.Icon className={`w-3.5 h-3.5 shrink-0 ${m.text}`} />
        <span className={`text-[11px] font-mono font-medium ${m.text} truncate`}>
          {String(data.text || data.code || data.label || m.label)}
        </span>
      </div>

      {/* Терминальный узел, но бэкенд может вернуть исходящее ребро — оставляем выход */}
      <Handle
        type="source"
        position={Position.Bottom}
        className="!bg-zinc-500 !w-2.5 !h-2.5 !border-2 !border-zinc-950"
      />
    </div>
  );
};
