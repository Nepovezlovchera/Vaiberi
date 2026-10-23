import React, { useCallback, useMemo } from 'react';
import {
  ReactFlow,
  Background,
  Controls,
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
  const { nodes, edges, setNodes, setEdges, selectNode, selectedNodeId, addCustomNode } = useStudioStore();
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
