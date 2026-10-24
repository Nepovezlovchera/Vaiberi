import React, { useCallback, useMemo, useEffect, useRef, useState } from 'react';
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
  Node,
  Connection,
  useNodesInitialized,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';

import { useStudioStore } from '../../store/useStudioStore';
import { MethodNode } from './nodes/MethodNode';
import { ConditionNode } from './nodes/ConditionNode';
import { LoopNode } from './nodes/LoopNode';
import { VarNode } from './nodes/VarNode';
import { CallNode } from './nodes/CallNode';
import { ReturnNode } from './nodes/ReturnNode';
import { TryNode, SwitchNode } from './nodes/StructNode';
import { JumpNode } from './nodes/JumpNode';
import { getLayoutedElements } from './layout';
import { isEntryNode } from '../../store/graphEdits';
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
    syncStatus,
    syncError,
    moveNode,
    connectNodes,
    mutationError,
    dismissMutationError,
  } = useStudioStore();
  const { fitView, zoomIn, zoomOut, getIntersectingNodes, screenToFlowPosition } = useReactFlow();
  const canvasRef = useRef<HTMLDivElement>(null);

  // Позиция узла до перетаскивания: если перенос в коде не удался, узел возвращается на место
  const dragOrigin = useRef<{ id: string; x: number; y: number } | null>(null);
  // Узел под курсором при перетаскивании (подсветка цели)
  const [dropTargetId, setDropTargetId] = useState<string | null>(null);

  /**
   * Цель переноса — узел под курсором (не по пересечению рамок: соседние узлы стоят вплотную,
   * и малейший сдвиг считался бы переносом). Сам узел и его тело целью быть не могут.
   */
  const findDropTarget = useCallback(
    (event: MouseEvent | TouchEvent, draggedId: string): Node | null => {
      const p = 'touches' in event ? event.changedTouches[0] : event;
      if (!p) return null;
      const point = screenToFlowPosition({ x: p.clientX, y: p.clientY });
      const hits = getIntersectingNodes({ x: point.x, y: point.y, width: 1, height: 1 }).filter(
        (n) => n.id !== draggedId && !n.id.startsWith(draggedId + '.')
      );
      return hits[hits.length - 1] ?? null;
    },
    [getIntersectingNodes, screenToFlowPosition]
  );

  const restoreDragged = useCallback(() => {
    const origin = dragOrigin.current;
    dragOrigin.current = null;
    if (!origin) return;
    setNodes((nds) => nds.map((n) => (n.id === origin.id ? { ...n, position: { x: origin.x, y: origin.y } } : n)));
  }, [setNodes]);

  const onNodeDragStart = useCallback((_: MouseEvent | TouchEvent, node: Node) => {
    dragOrigin.current = { id: node.id, x: node.position.x, y: node.position.y };
  }, []);

  const onNodeDrag = useCallback(
    (event: MouseEvent | TouchEvent, node: Node) => setDropTargetId(findDropTarget(event, node.id)?.id ?? null),
    [findDropTarget]
  );

  // Отпущен над узлом — перенос оператора после него (с Shift — в начало его тела) через /api/graph/mutate.
  // Над пустым местом — просто новая позиция на холсте, код не меняется.
  const onNodeDragStop = useCallback(
    async (event: MouseEvent | TouchEvent, node: Node) => {
      setDropTargetId(null);
      const target = findDropTarget(event, node.id);
      if (!target) {
        dragOrigin.current = null;
        return;
      }
      const moved = await moveNode(node.id, target.id, event.shiftKey);
      if (!moved) restoreDragged();
      dragOrigin.current = null;
    },
    [findDropTarget, moveNode, restoreDragged]
  );

  // Связь, протянутая от ручки узла к другому узлу, — перенос оператора в эту ветку (см. connectNodes)
  const onConnect = useCallback(
    (c: Connection) => {
      if (c.source && c.target) connectNodes(c.source, c.sourceHandle, c.target);
    },
    [connectNodes]
  );

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
      try: TryNode,
      switch: SwitchNode,
      break: JumpNode,
      continue: JumpNode,
      throw: JumpNode,
    }),
    []
  );

  // Новый граф сначала раскладывается по оценочным размерам; когда узлы отрисованы и измерены,
  // раскладываем ещё раз по реальным (иначе широкие узлы наезжают друг на друга). Один раз на набор узлов,
  // чтобы не сбивать позиции, которые пользователь задал перетаскиванием.
  const nodesInitialized = useNodesInitialized();
  const laidOutKey = useRef('');
  useEffect(() => {
    if (!nodesInitialized || nodes.length === 0) return;
    const key = nodes.map((n) => n.id).join('|');
    if (key === laidOutKey.current) return;
    laidOutKey.current = key;
    const layouted = getLayoutedElements(nodes, edges);
    setNodes(layouted.nodes);
    setTimeout(() => fitView({ padding: 0.2 }), 50);
  }, [nodesInitialized, nodes, edges, setNodes, fitView]);

  // Подгоняем вид при смене метода или набора узлов
  useEffect(() => {
    const t = setTimeout(() => fitView({ padding: 0.2 }), 50);
    return () => clearTimeout(t);
  }, [activeMethodId, nodes.length, fitView]);

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
      setNodes((nds) => applyNodeChanges(changes.filter((change) => change.type !== 'remove'), nds));
    },
    [setNodes]
  );

  const onEdgesChange = useCallback(
    (changes: EdgeChange[]) => {
      if (changes.some((change) => change.type === 'remove')) {
        useStudioStore.setState({ mutationError: 'Связь задаётся структурой Java. Измените ветку или порядок блоков; отдельное удаление перехода не создаёт корректный Java.' });
      }
      setEdges((eds) => applyEdgeChanges(changes.filter((change) => change.type !== 'remove'), eds));
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

  const handleFullscreen = useCallback(async () => {
    try {
    if (document.fullscreenElement) {
      await document.exitFullscreen();
    } else {
      await canvasRef.current?.requestFullscreen();
      setTimeout(() => fitView({ padding: 0.2 }), 50);
    }
    } catch {
      useStudioStore.setState({ mutationError: 'Браузер не разрешил полноэкранный режим.' });
    }
  }, [fitView]);

  const onDragOver = useCallback((event: React.DragEvent) => {
    event.preventDefault();
    event.dataTransfer.dropEffect = 'move';
  }, []);

  const onDrop = useCallback(
    (event: React.DragEvent) => {
      event.preventDefault();
      const type = event.dataTransfer.getData('application/reactflow');
      if (!type) return;

      // Блок встаёт после узла, на который брошен (с Shift — в начало его тела), мимо узлов — в конец метода
      const point = screenToFlowPosition({ x: event.clientX, y: event.clientY });
      const target = getIntersectingNodes({ x: point.x, y: point.y, width: 1, height: 1 })[0];
      addCustomNode(type, target?.id ?? null, event.shiftKey);
    },
    [addCustomNode, getIntersectingNodes, screenToFlowPosition]
  );

  // Добавляем selected флаг к активной ноде
  const enhancedNodes = useMemo(() => {
    return nodes.map((node) => ({
      ...node,
      selected: node.id === selectedNodeId,
      className: node.id === dropTargetId ? 'drop-target' : undefined,
      // Пока правка идёт на сервере, граф не трогаем
      draggable: !isMutating && !isEntryNode(node.id),
    }));
  }, [nodes, selectedNodeId, dropTargetId, isMutating]);

  return (
    <div ref={canvasRef} className="relative w-full h-full bg-[#0a0a0c] overflow-hidden" onDragOver={onDragOver} onDrop={onDrop}>
      {/* Method Dropdown Selector (Слайды 3, 4 ТЗ) */}
      {methodsList.length > 0 && (
        <div className="absolute top-3 left-4 z-10 flex items-center gap-2 bg-surface/90 backdrop-blur border border-border px-2.5 py-1.5 rounded-lg shadow-xl">
          <span className="text-[11px] font-semibold text-zinc-400">Метод:</span>
          <select
            value={activeMethodId || ''}
            onChange={(e) => setActiveMethodId(e.target.value)}
            className="bg-zinc-900 border border-border text-xs text-blue-400 font-semibold rounded px-2 py-0.5 focus:outline-none focus:border-blue-500 font-mono max-w-[280px] truncate"
          >
            {/* Без пустого варианта select показал бы первый метод, хотя холст пуст */}
            {!activeMethodId && (
              <option value="" disabled>
                Метод не выбран
              </option>
            )}
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
            Обход узлов графа: шаг {debugStepIndex + 1} из {nodes.length}
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

      {/* Статус синхронизации с бэкендом */}
      {syncStatus !== 'idle' && (
        <div className="absolute bottom-3 left-1/2 -translate-x-1/2 z-10 max-w-[50%]">
          {syncStatus === 'syncing' && (
            <span className="inline-block px-2.5 py-1 text-xs rounded-full bg-blue-950/80 border border-blue-500/40 text-blue-300">
              Анализ…
            </span>
          )}
          {syncStatus === 'ok' && (
            <span className="inline-block px-2.5 py-1 text-xs rounded-full bg-emerald-950/80 border border-emerald-500/40 text-emerald-300">
              Граф построен бэкендом
            </span>
          )}
          {syncStatus === 'error' && (
            <span
              className="block truncate px-2.5 py-1 text-xs rounded-full bg-rose-950/80 border border-rose-500/40 text-rose-300"
              title={syncError ?? ''}
            >
              {syncError}
            </span>
          )}
          {syncStatus === 'offline' && (
            <span className="inline-block px-2.5 py-1 text-xs rounded-full bg-amber-950/80 border border-amber-500/40 text-amber-300">
              Демо-граф (бэкенд оффлайн)
            </span>
          )}
        </div>
      )}

      {/* Ошибка правки графа: правка уже откачена, код не изменён */}
      {mutationError && (
        <div className="absolute top-14 right-4 z-20 w-[min(420px,60%)] rounded-lg border border-rose-500/50 bg-rose-950/95 shadow-2xl">
          <div className="flex items-start justify-between gap-2 px-3 pt-2">
            <span className="text-xs font-semibold text-rose-200">{mutationError.split('\n')[0]}</span>
            <button
              onClick={dismissMutationError}
              className="text-rose-300 hover:text-white text-xs leading-none"
              title="Закрыть"
            >
              ✕
            </button>
          </div>
          {mutationError.includes('\n') && (
            <pre className="m-2 mt-1 max-h-40 overflow-auto whitespace-pre-wrap rounded bg-black/40 p-2 text-[11px] text-rose-100 font-mono">
              {mutationError.split('\n').slice(1).join('\n')}
            </pre>
          )}
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
          onClick={handleFullscreen}
          className="p-1.5 text-zinc-400 hover:text-white hover:bg-zinc-800 rounded transition"
          title="Во весь экран"
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
        onNodeDragStart={onNodeDragStart}
        onNodeDrag={onNodeDrag}
        onNodeDragStop={onNodeDragStop}
        onConnect={onConnect}
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
