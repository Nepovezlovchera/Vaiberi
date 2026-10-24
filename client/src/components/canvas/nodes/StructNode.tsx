import React from 'react';
import { Handle, Position, NodeProps } from '@xyflow/react';
import { ShieldAlert, GitFork, AlertTriangle } from 'lucide-react';

const handleCls = '!w-3 !h-3 !border-2 !border-zinc-950 hover:!scale-125 transition-transform';

const Label: React.FC<{ text: string; className: string; style?: React.CSSProperties }> = ({ text, className, style }) => (
  <div
    className={`absolute px-1.5 py-0.5 rounded border text-[10px] font-bold select-none shadow ${className}`}
    style={style}
  >
    {text}
  </div>
);

const IssueBadge: React.FC<{ title?: unknown }> = ({ title }) => (
  <span
    className="flex items-center gap-1 max-w-[130px] text-[10px] text-amber-300 font-medium bg-amber-950/80 px-1.5 py-0.5 rounded border border-amber-600/40"
    title={String(title || '')}
  >
    <AlertTriangle className="w-3 h-3 shrink-0 text-amber-400" />
    <span className="truncate">{String(title || 'Проблема')}</span>
  </span>
);

const firstLine = (s: unknown) => String(s ?? '').split('\n')[0];

export const TryNode: React.FC<NodeProps> = ({ data, selected }) => (
  <div
    className={`relative min-w-[280px] rounded-lg bg-surface border transition-all duration-200 shadow-lg ${
      selected ? 'border-orange-400 ring-2 ring-orange-500/30' : 'border-orange-500/40 hover:border-orange-500/70'
    }`}
  >
    <Handle type="target" position={Position.Top} className={`${handleCls} !bg-zinc-400`} />

    <div className="flex items-center justify-between gap-2 px-3 py-1.5 bg-orange-950/40 border-b border-orange-500/20 rounded-t-lg">
      <div className="flex items-center gap-2">
        <ShieldAlert className="w-3.5 h-3.5 text-orange-400" />
        <span className="text-xs font-semibold text-orange-300">try / catch / finally</span>
      </div>
      {Boolean(data.hasIssue) && <IssueBadge title={data.issueTitle} />}
    </div>

    <div className="p-3 pb-5 text-xs font-mono text-zinc-200">
      <div className="break-all">{firstLine(data.condition || data.code || data.label)}</div>
    </div>

    <Handle id="body" type="source" position={Position.Right} style={{ top: '35%' }} className={`${handleCls} !bg-blue-400`} />
    <Label
      text="try"
      className="-right-9 bg-blue-950/80 border-blue-500/40 text-blue-300"
      style={{ top: '35%', transform: 'translateY(-50%)' }}
    />
    <Handle id="catch" type="source" position={Position.Right} style={{ top: '70%' }} className={`${handleCls} !bg-rose-500`} />
    <Label
      text="catch"
      className="-right-12 bg-rose-950/80 border-rose-500/40 text-rose-400"
      style={{ top: '70%', transform: 'translateY(-50%)' }}
    />
    <Handle id="finally" type="source" position={Position.Bottom} style={{ left: '25%' }} className={`${handleCls} !bg-purple-500`} />
    <Label
      text="finally"
      className="-bottom-6 bg-purple-950/80 border-purple-500/40 text-purple-300"
      style={{ left: '25%', transform: 'translateX(-50%)' }}
    />
    <Handle id="next" type="source" position={Position.Bottom} style={{ left: '75%' }} className={`${handleCls} !bg-zinc-400`} />
  </div>
);

export const SwitchNode: React.FC<NodeProps> = ({ data, selected }) => (
  <div
    className={`relative min-w-[280px] rounded-lg bg-surface border transition-all duration-200 shadow-lg ${
      selected ? 'border-teal-400 ring-2 ring-teal-500/30' : 'border-teal-500/40 hover:border-teal-500/70'
    }`}
  >
    <Handle type="target" position={Position.Top} className={`${handleCls} !bg-zinc-400`} />

    <div className="flex items-center justify-between gap-2 px-3 py-1.5 bg-teal-950/40 border-b border-teal-500/20 rounded-t-lg">
      <div className="flex items-center gap-2">
        <GitFork className="w-3.5 h-3.5 text-teal-400" />
        <span className="text-xs font-semibold text-teal-300">switch</span>
      </div>
      {Boolean(data.hasIssue) && <IssueBadge title={data.issueTitle} />}
    </div>

    <div className="p-3 text-xs font-mono text-zinc-200">
      <div className="break-all">{firstLine(data.condition || data.code || data.label)}</div>
    </div>

    {/* Несколько рёбер 'body' — по одному на каждый case */}
    <Handle id="body" type="source" position={Position.Right} className={`${handleCls} !bg-teal-400`} />
    <Label text="case" className="-right-11 top-1/2 -translate-y-1/2 bg-teal-950/80 border-teal-500/40 text-teal-300" />
    <Handle id="next" type="source" position={Position.Bottom} className={`${handleCls} !bg-zinc-400`} />
  </div>
);
