import React from 'react';
import { Handle, Position, NodeProps } from '@xyflow/react';
import { HelpCircle } from 'lucide-react';

export const ConditionNode: React.FC<NodeProps> = ({ data, selected }) => {
  return (
    <div
      className={`relative min-w-[280px] rounded-lg bg-surface border transition-all duration-200 shadow-lg ${
        selected ? 'border-amber-400 ring-2 ring-amber-400/30' : 'border-amber-500/40 hover:border-amber-500/70'
      }`}
    >
      <Handle
        type="target"
        position={Position.Top}
        className="!bg-zinc-400 !w-3 !h-3 !border-2 !border-zinc-950 hover:!scale-125 transition-transform"
      />

      <div className="flex items-center justify-between px-3 py-1.5 bg-amber-950/40 border-b border-amber-500/20 rounded-t-lg">
        <div className="flex items-center gap-2">
          <HelpCircle className="w-3.5 h-3.5 text-amber-400" />
          <span className="text-xs font-semibold text-amber-300">Условие</span>
        </div>
      </div>

      <div className="p-3 text-xs font-mono text-zinc-200">
        <div className="break-all">{String(data.condition || data.code)}</div>
      </div>

      {/* True Branch (Да) */}
      <Handle
        id="true"
        type="source"
        position={Position.Right}
        className="!bg-emerald-500 !w-3.5 !h-3.5 !border-2 !border-zinc-950 hover:!scale-125 transition-transform"
      />
      <div className="absolute -right-8 top-1/2 -translate-y-1/2 px-1.5 py-0.5 rounded bg-emerald-950/80 border border-emerald-500/40 text-[10px] font-bold text-emerald-400 select-none shadow">
        Да
      </div>

      {/* False Branch (Нет) */}
      <Handle
        id="false"
        type="source"
        position={Position.Bottom}
        className="!bg-rose-500 !w-3.5 !h-3.5 !border-2 !border-zinc-950 hover:!scale-125 transition-transform"
      />
      <div className="absolute -bottom-6 left-1/2 -translate-x-1/2 px-1.5 py-0.5 rounded bg-rose-950/80 border border-rose-500/40 text-[10px] font-bold text-rose-400 select-none shadow">
        Нет
      </div>
    </div>
  );
};
