import React from 'react';
import { Handle, Position, NodeProps } from '@xyflow/react';
import { CornerDownLeft } from 'lucide-react';

export const ReturnNode: React.FC<NodeProps> = ({ data, selected }) => {
  return (
    <div
      className={`relative min-w-[220px] rounded-lg bg-surface border transition-all duration-200 shadow-md ${
        selected ? 'border-emerald-400 ring-2 ring-emerald-500/30' : 'border-emerald-500/40 hover:border-emerald-500/70'
      }`}
    >
      <Handle
        type="target"
        position={Position.Top}
        className="!bg-emerald-400 !w-3 !h-3 !border-2 !border-zinc-950 hover:!scale-125 transition-transform"
      />

      <div className="flex items-center gap-1.5 px-3 py-1 bg-emerald-950/40 border-b border-emerald-500/20 rounded-t-lg">
        <CornerDownLeft className="w-3.5 h-3.5 text-emerald-400" />
        <span className="text-[11px] font-semibold text-emerald-300">Возврат</span>
      </div>

      <div className="p-2.5 text-xs font-mono text-zinc-100 font-semibold">
        <div className="break-all">{String(data.text || data.code || data.label)}</div>
      </div>

      {/* Только для ребра к недостижимому коду после return (бэкенд связывает операторы блока подряд) */}
      <Handle
        type="source"
        position={Position.Bottom}
        className="!bg-zinc-600 !w-2 !h-2 !border-zinc-950"
      />
    </div>
  );
};
