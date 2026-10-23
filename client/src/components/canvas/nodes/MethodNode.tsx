import React from 'react';
import { Handle, Position, NodeProps } from '@xyflow/react';
import { Code2, AlertTriangle } from 'lucide-react';

export const MethodNode: React.FC<NodeProps> = ({ data, selected }) => {
  return (
    <div
      className={`relative min-w-[280px] rounded-lg bg-surface border transition-all duration-200 shadow-lg ${
        selected ? 'border-purple-400 ring-2 ring-purple-500/30' : 'border-purple-500/40 hover:border-purple-500/70'
      } ${data.hasIssue ? 'border-amber-500/80' : ''}`}
    >
      <div className="flex items-center justify-between px-3 py-1.5 bg-purple-950/40 border-b border-purple-500/20 rounded-t-lg">
        <div className="flex items-center gap-2">
          <Code2 className="w-3.5 h-3.5 text-purple-400" />
          <span className="text-xs font-semibold text-purple-300">Метод</span>
        </div>
        {Boolean(data.hasIssue) && (
          <span className="flex items-center gap-1 text-[10px] text-amber-300 font-medium bg-amber-950/80 px-1.5 py-0.5 rounded border border-amber-600/40">
            <AlertTriangle className="w-3 h-3 text-amber-400" />
            Длинный метод
          </span>
        )}
      </div>

      <div className="p-3 text-xs font-mono text-zinc-200">
        <div className="break-all">{String(data.code || data.label)}</div>
      </div>

      <Handle
        type="source"
        position={Position.Bottom}
        className="!bg-purple-500 !w-3 !h-3 !border-2 !border-zinc-950 hover:!scale-125 transition-transform"
      />
    </div>
  );
};
