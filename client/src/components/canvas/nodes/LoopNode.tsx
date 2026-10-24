import React from 'react';
import { Handle, Position, NodeProps } from '@xyflow/react';
import { Repeat, AlertCircle, Sparkles } from 'lucide-react';

export const LoopNode: React.FC<NodeProps> = ({ data, selected }) => {
  return (
    <div
      className={`relative min-w-[300px] rounded-lg bg-surface border transition-all duration-200 shadow-lg ${
        selected ? 'border-blue-400 ring-2 ring-blue-500/30' : 'border-blue-500/40 hover:border-blue-500/70'
      } ${data.hasIssue ? 'border-rose-500/80 shadow-rose-950/40' : ''}`}
    >
      <Handle
        type="target"
        position={Position.Top}
        className="!bg-zinc-400 !w-3 !h-3 !border-2 !border-zinc-950 hover:!scale-125 transition-transform"
      />

      <div className="flex items-center justify-between px-3 py-1.5 bg-blue-950/40 border-b border-blue-500/20 rounded-t-lg">
        <div className="flex items-center gap-2">
          <Repeat className="w-3.5 h-3.5 text-blue-400" />
          <span className="text-xs font-semibold text-blue-300">{String(data.label || 'Цикл')}</span>
          {Boolean(data.loopType) && (
            <span className="text-[10px] font-mono text-blue-200 bg-blue-900/50 px-1.5 py-0.5 rounded border border-blue-500/30">
              {String(data.loopType)}
            </span>
          )}
        </div>
        {Boolean(data.hasIssue) ? (
          <span className="flex items-center gap-1 text-[10px] text-rose-300 font-bold bg-rose-950/80 px-2 py-0.5 rounded border border-rose-600/40 animate-pulse">
            <AlertCircle className="w-3 h-3 text-rose-400" />
            {String(data.complexityBadge ?? data.issueTitle ?? 'Проблема')}
          </span>
        ) : Boolean(data.complexityBadge === 'O(n)') ? (
          <span className="flex items-center gap-1 text-[10px] text-emerald-300 font-bold bg-emerald-950/80 px-2 py-0.5 rounded border border-emerald-600/40">
            <Sparkles className="w-3 h-3 text-emerald-400" />
            Оптимизировано O(n)
          </span>
        ) : null}
      </div>

      <div className="p-3 text-xs font-mono text-zinc-200">
        <div className="break-all">{String(data.text || data.code)}</div>
      </div>

      {/* Body handle */}
      <Handle
        id="body"
        type="source"
        position={Position.Right}
        className="!bg-blue-400 !w-3.5 !h-3.5 !border-2 !border-zinc-950 hover:!scale-125 transition-transform"
      />
      <div className="absolute -right-12 top-1/2 -translate-y-1/2 px-1.5 py-0.5 rounded bg-blue-950/80 border border-blue-500/40 text-[10px] font-bold text-blue-300 select-none shadow">
        Тело
      </div>

      {/* Exit handle */}
      <Handle
        id="next"
        type="source"
        position={Position.Bottom}
        className="!bg-blue-500 !w-3 !h-3 !border-2 !border-zinc-950 hover:!scale-125 transition-transform"
      />
    </div>
  );
};
