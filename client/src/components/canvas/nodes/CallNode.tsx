import React from 'react';
import { Handle, Position, NodeProps } from '@xyflow/react';
import { PhoneCall, AlertTriangle } from 'lucide-react';

export const CallNode: React.FC<NodeProps> = ({ data, selected }) => {
  return (
    <div
      className={`relative min-w-[260px] rounded-lg bg-surface border transition-all duration-200 shadow-md ${
        selected ? 'border-indigo-400 ring-2 ring-indigo-500/30' : 'border-indigo-500/30 hover:border-indigo-500/60'
      }`}
    >
      <Handle
        type="target"
        position={Position.Top}
        className="!bg-zinc-400 !w-3 !h-3 !border-2 !border-zinc-950 hover:!scale-125 transition-transform"
      />

      <div className="flex items-center justify-between gap-2 px-3 py-1 bg-indigo-950/30 border-b border-indigo-500/20 rounded-t-lg">
        <div className="flex items-center gap-1.5">
          <PhoneCall className="w-3.5 h-3.5 text-indigo-400" />
          <span className="text-[11px] font-medium text-indigo-300">Метод (внешний вызов)</span>
        </div>
        {Boolean(data.hasIssue) && (
          <span
            className="flex items-center gap-1 max-w-[130px] text-[10px] text-rose-300 font-medium bg-rose-950/80 px-1.5 py-0.5 rounded border border-rose-600/40"
            title={String(data.issueTitle || '')}
          >
            <AlertTriangle className="w-3 h-3 shrink-0 text-rose-400" />
            <span className="truncate">{String(data.issueTitle || 'Проблема')}</span>
          </span>
        )}
      </div>

      <div className="p-2.5 text-xs font-mono text-zinc-200">
        <div className="break-all">{String(data.text || data.code || data.label)}</div>
      </div>

      <Handle
        type="source"
        position={Position.Bottom}
        className="!bg-indigo-500 !w-3 !h-3 !border-2 !border-zinc-950 hover:!scale-125 transition-transform"
      />
    </div>
  );
};
