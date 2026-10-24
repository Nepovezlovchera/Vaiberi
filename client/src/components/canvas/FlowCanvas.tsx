import React, { useCallback, useMemo, useEffect } from 'react';
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  applyNodeChanges,
  applyEdgeChanges,
  NodeChange,
  EdgeChange,
  BackgroundVariant,
  useReactFlow,
  ReactFlowProvider,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';

import { useStudioStore } from '../../store/useStudioStore';
import { MethodNode } from './nodes/MethodNode';
import { ConditionNode } from './nodes/ConditionNode';
import { LoopNode } from './nodes/LoopNode';
import { VarNode } from './nodes/VarNode';
import { CallNode } from './nodes/CallNode';
import { ReturnNode } from './nodes/ReturnNode';
import { getLayoutedElements } from './layout';
import { LayoutGrid, ZoomIn, ZoomOut, Maximize2 } from 'lucide-react';

const FlowCanvasInternal: React.FC = () => {
  const {
    nodes,
    edges,
    setNodes,
    setEdges,
    selectNode,
    selectedNodeId,
    addCustomNode,
    deleteSelectedNode,
    methodsList,
    activeMethodId,
    setActiveMethodId,
    isDebugging,
    debugStepIndex,
    stepDebug,
    stopDebug,
    isMutating,
  } = useStudioStore();
  const { fitView, zoomIn, zoomOut } = useReactFlow();

  const nodeTypes = useMemo(
    () => ({
      method: MethodNode,
      condition: ConditionNode,
      loop: LoopNode,
      var: VarNode,
      call: CallNode,
      return: ReturnNode,
      op: VarNode,
      raw: VarNode,
    }),
    []
  );

  // Клавиатурное удаление выбранного узла (Delete / Backspace)
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.key === 'Delete' || e.key === 'Backspace') && selectedNodeId) {
        // Проверяем, что фокус не в инпуте редактора
        const target = e.target as HTMLElement;
        if (target.tagName !== 'INPUT' && target.tagName !== 'TEXTAREA' && !target.closest('.monaco-editor')) {
          e.preventDefault();
          deleteSelectedNode();
        }
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [selectedNodeId, deleteSelectedNode]);

  const onNodesChange = useCallback(
    (changes: NodeChange[]) => {
      setNodes((nds) => applyNodeChanges(changes, nds));
    },
    [setNodes]
  );

  const onEdgesChange = useCallback(
    (changes: EdgeChange[]) => {
      setEdges((eds) => applyEdgeChanges(changes, eds));
    },
    [setEdges]
  );

  const onNodeClick = useCallback(
    (_: React.MouseEvent, node: { id: string }) => {
      selectNode(node.id);
    },
    [selectNode]
  );

  const onPaneClick = useCallback(() => {
    selectNode(null);
  }, [selectNode]);

  const handleAutoLayout = useCallback(() => {
    const layouted = getLayoutedElements(nodes, edges);
    setNodes([...layouted.nodes]);
    setEdges([...layouted.edges]);
    setTimeout(() => fitView({ padding: 0.2 }), 50);
  }, [nodes, edges, setNodes, setEdges, fitView]);

  const onDragOver = useCallback((event: React.DragEvent) => {
    event.preventDefault();
    event.dataTransfer.dropEffect = 'move';
  }, []);

  const onDrop = useCallback(
    (event: React.DragEvent) => {
      event.preventDefault();
      const type = event.dataTransfer.getData('application/reactflow');
      if (!type) return;

      const bounds = event.currentTarget.getBoundingClientRect();
      const position = {
        x: event.clientX - bounds.left - 100,
        y: event.clientY - bounds.top - 30,
      };

      addCustomNode(type, position);
    },
    [addCustomNode]
  );

  // Добавляем selected флаг к активной ноде
  const enhancedNodes = useMemo(() => {
    return nodes.map((node) => ({
      ...node,
      selected: node.id === selectedNodeId,
    }));
  }, [nodes, selectedNodeId]);

  return (
    <div className="relative w-full h-full bg-[#0a0a0c] overflow-hidden" onDragOver={onDragOver} onDrop={onDrop}>
      {/* Method Dropdown Selector (Слайды 3, 4 ТЗ) */}
      {methodsList.length > 0 && (
        <div className="absolute top-3 left-4 z-10 flex items-center gap-2 bg-surface/90 backdrop-blur border border-border px-2.5 py-1.5 rounded-lg shadow-xl">
          <span className="text-[11px] font-semibold text-zinc-400">Метод:</span>
          <select
            value={activeMethodId || ''}
            onChange={(e) => setActiveMethodId(e.target.value)}
            className="bg-zinc-900 border border-border text-xs text-blue-400 font-semibold rounded px-2 py-0.5 focus:outline-none focus:border-blue-500 font-mono max-w-[280px] truncate"
          >
            {methodsList.map((m) => (
              <option key={m.methodId} value={m.methodId}>
                {m.signature} (строки {m.lineStart}-{m.lineEnd})
              </option>
            ))}
          </select>
        </div>
      )}

      {/* Debug Step Overlay (Слайд 4 ТЗ) */}
      {isDebugging && (
        <div className="absolute top-14 left-4 z-10 flex items-center gap-2 bg-amber-950/90 backdrop-blur border border-amber-500/50 px-3 py-1.5 rounded-lg shadow-2xl">
          <span className="w-2 h-2 rounded-full bg-amber-400 animate-ping" />
          <span className="text-xs font-bold text-amber-300">
            Дебаг AST: шаг {debugStepIndex + 1} из {nodes.length}
          </span>
          <button
            onClick={stepDebug}
            className="px-2.5 py-1 bg-amber-500 hover:bg-amber-400 text-black text-xs font-bold rounded shadow transition active:scale-95"
          >
            След. шаг ⏭
          </button>
          <button
            onClick={stopDebug}
            className="px-2 py-1 bg-zinc-800 hover:bg-zinc-700 text-zinc-300 text-xs rounded transition"
          >
            Стоп
          </button>
        </div>
      )}

      {/* Mutating AST Status Badge */}
      {isMutating && (
        <div className="absolute top-3 left-1/2 -translate-x-1/2 z-10 px-3 py-1 rounded-full bg-blue-950/90 border border-blue-500/50 text-[11px] text-blue-300 flex items-center gap-2 shadow-lg">
          <span className="w-2 h-2 rounded-full bg-blue-400 animate-pulse" />
          Синхронизация AST...
        </div>
      )}

      {/* Top Controls Overlay */}
      <div className="absolute top-3 right-4 z-10 flex items-center gap-1.5 bg-surface/90 backdrop-blur border border-border p-1 rounded-lg shadow-xl">
        <button
          onClick={handleAutoLayout}
          className="flex items-center gap-1.5 px-2.5 py-1 text-xs font-medium text-zinc-300 hover:text-white hover:bg-zinc-800 rounded transition"
          title="Автовыравнивание графа (Dagre)"
        >
          <LayoutGrid className="w-3.5 h-3.5 text-blue-400" />
          <span>Автолейаут</span>
        </button>
        <div className="w-px h-4 bg-border mx-1" />
        <button
          onClick={() => zoomIn()}
          className="p-1.5 text-zinc-400 hover:text-white hover:bg-zinc-800 rounded transition"
          title="Приблизить"
        >
          <ZoomIn className="w-3.5 h-3.5" />
        </button>
        <button
          onClick={() => zoomOut()}
          className="p-1.5 text-zinc-400 hover:text-white hover:bg-zinc-800 rounded transition"
          title="Отдалить"
        >
          <ZoomOut className="w-3.5 h-3.5" />
        </button>
        <button
          onClick={() => fitView({ padding: 0.2 })}
          className="p-1.5 text-zinc-400 hover:text-white hover:bg-zinc-800 rounded transition"
          title="По размеру экрана"
        >
          <Maximize2 className="w-3.5 h-3.5" />
        </button>
      </div>

      <ReactFlow
        nodes={enhancedNodes}
        edges={edges}
        nodeTypes={nodeTypes}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onNodeClick={onNodeClick}
        onPaneClick={onPaneClick}
        fitView
        fitViewOptions={{ padding: 0.25 }}
        minZoom={0.2}
        maxZoom={2.5}
        defaultEdgeOptions={{
          type: 'smoothstep',
          style: { stroke: '#4b5563', strokeWidth: 2 },
        }}
      >
        <Background variant={BackgroundVariant.Dots} gap={16} size={1} color="#27272a" />
        <Controls showInteractive={false} className="!bg-surface !border-border !fill-zinc-400 shadow-xl" />
        <MiniMap
          nodeStrokeWidth={3}
          nodeColor={(n) => {
            if (n.type === 'condition') return '#f59e0b';
            if (n.type === 'loop') return '#3b82f6';
            if (n.type === 'method') return '#a855f7';
            if (n.type === 'return') return '#10b981';
            return '#64748b';
          }}
          className="!bg-surface/80 !border-border !rounded-lg !overflow-hidden shadow-2xl"
          maskColor="rgba(9, 9, 11, 0.7)"
        />
      </ReactFlow>
    </div>
  );
};

export const FlowCanvas: React.FC = () => {
  return (
    <ReactFlowProvider>
      <FlowCanvasInternal />
    </ReactFlowProvider>
  );
};
