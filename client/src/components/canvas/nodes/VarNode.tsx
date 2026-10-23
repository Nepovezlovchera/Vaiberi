import React from 'react';
import { Handle, Position, NodeProps } from '@xyflow/react';
import { Layers } from 'lucide-react';

export const VarNode: React.FC<NodeProps> = ({ data, selected }) => {
  return (
    <div
      className={`relative min-w-[260px] rounded-lg bg-surface border transition-all duration-200 shadow-md ${
        selected ? 'border-sky-400 ring-2 ring-sky-500/30' : 'border-sky-500/30 hover:border-sky-500/60'
      }`}
    >
      <Handle
        type="target"
        position={Position.Top}
        className="!bg-zinc-400 !w-3 !h-3 !border-2 !border-zinc-950 hover:!scale-125 transition-transform"
      />

      <div className="flex items-center gap-1.5 px-3 py-1 bg-sky-950/30 border-b border-sky-500/20 rounded-t-lg">
        <Layers className="w-3.5 h-3.5 text-sky-400" />
        <span className="text-[11px] font-medium text-sky-300">Переменная</span>
      </div>

      <div className="p-2.5 text-xs font-mono text-zinc-200">
        <div className="break-all">{String(data.code || data.label)}</div>
      </div>

      <Handle
        type="source"
        position={Position.Bottom}
        className="!bg-sky-500 !w-3 !h-3 !border-2 !border-zinc-950 hover:!scale-125 transition-transform"
      />
    </div>
  );
};
