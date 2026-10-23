---
name: react-flow-monaco-studio
description: Specialized frontend engineering patterns for building visual programming IDEs with React Flow, Monaco Editor, Dagre auto-layout, and two-way code-graph synchronization.
---

# React Flow & Monaco Studio — Frontend Development Skill

## Overview
This skill provides production-grade architectural patterns, code templates, and best practices for developing the **Java Blueprints** frontend studio (React 18 + TypeScript + Vite + Tailwind CSS + shadcn/ui).

---

## 1. React Flow v12 (`@xyflow/react`) Architecture

### 1.1. Custom Node Types
The canvas requires dedicated nodes matching the hackathon concept (Slide 4):
1. **`MethodNode`**: Method signature header (purple theme), entry handle at bottom.
2. **`ConditionNode`**: Diamond-shaped condition block with `True` handle (green) on the right/bottom and `False` handle (red) on the left/bottom.
3. **`LoopNode`**: Container/block with loop header (`for`, `while`), `Body` handle (blue) inside, and `Next` handle after loop completion.
4. **`VarNode` / `OpNode`**: Standard rectangular statement nodes with input (top) and output (bottom) handles.
5. **`ReturnNode`**: Terminal node (green badge), single target handle.

### 1.2. Handles & Custom Ports Setup
```tsx
import { Handle, Position, NodeProps } from '@xyflow/react';

export function ConditionNode({ data, selected }: NodeProps) {
  return (
    <div className={`relative px-4 py-3 bg-zinc-900 border-2 rounded-lg transition-all ${
      selected ? 'border-amber-400 ring-2 ring-amber-400/30' : 'border-amber-600/60'
    } ${data.hasIssue ? 'ring-2 ring-rose-500 animate-pulse' : ''}`}>
      {/* Target handle from previous node */}
      <Handle type="target" position={Position.Top} className="!bg-zinc-400 !w-3 !h-3" />
      
      <div className="flex items-center gap-2 text-xs font-mono text-amber-200">
        <span className="font-bold">◇ if</span>
        <span>{data.condition || data.code}</span>
      </div>

      {/* True Branch Handle */}
      <Handle 
        id="true" 
        type="source" 
        position={Position.Right} 
        className="!bg-emerald-500 !w-3 !h-3" 
      />
      <span className="absolute -right-6 top-1 text-[10px] text-emerald-400 font-bold">Да</span>

      {/* False Branch Handle */}
      <Handle 
        id="false" 
        type="source" 
        position={Position.Bottom} 
        className="!bg-rose-500 !w-3 !h-3" 
      />
      <span className="absolute -bottom-5 left-1 text-[10px] text-rose-400 font-bold">Нет</span>
    </div>
  );
}
```

### 1.3. Dagre Auto-Layout Algorithm
```typescript
import dagre from '@dagrejs/dagre';
import { Node, Edge } from '@xyflow/react';

export function getLayoutedElements(nodes: Node[], edges: Edge[], direction = 'TB') {
  const dagreGraph = new dagre.graphlib.Graph();
  dagreGraph.setDefaultEdgeLabel(() => ({}));
  
  dagreGraph.setGraph({ 
    rankdir: direction, 
    nodesep: 40, 
    ranksep: 60,
    marginx: 20,
    marginy: 20
  });

  nodes.forEach((node) => {
    // Standard node dimensions
    const width = node.type === 'condition' ? 240 : 260;
    const height = node.type === 'loop' ? 120 : 64;
    dagreGraph.setNode(node.id, { width, height });
  });

  edges.forEach((edge) => {
    dagreGraph.setEdge(edge.source, edge.target);
  });

  dagre.layout(dagreGraph);

  const layoutedNodes = nodes.map((node) => {
    const nodeWithPosition = dagreGraph.node(node.id);
    return {
      ...node,
      position: {
        x: nodeWithPosition.x - (node.type === 'condition' ? 120 : 130),
        y: nodeWithPosition.y - 32,
      },
    };
  });

  return { nodes: layoutedNodes, edges };
}
```

---

## 2. Monaco Editor Integration & Two-Way Sync

### 2.1. Bi-directional Synchronization
- **Graph $\rightarrow$ Code**: When a node is clicked or selected, reveal the code line in center and decorate the line range:
```typescript
export function highlightCodeInMonaco(editor: any, monaco: any, lineStart: number, lineEnd: number) {
  if (!editor || !lineStart) return;

  editor.revealLineInCenter(lineStart);
  
  // Set temporary line decoration
  const decorations = editor.deltaDecorations([], [
    {
      range: new monaco.Range(lineStart, 1, lineEnd, 100),
      options: {
        isWholeLine: true,
        className: 'bg-amber-500/20 border-l-4 border-amber-400',
        glyphMarginClassName: 'text-amber-400'
      }
    }
  ]);

  setTimeout(() => {
    editor.deltaDecorations(decorations, []);
  }, 2500);
}
```

- **Code $\rightarrow$ Graph**: When the cursor moves in Monaco, find the AST node covering that line and highlight it in React Flow:
```typescript
editor.onDidChangeCursorPosition((e) => {
  const currentLine = e.position.lineNumber;
  const targetNode = nodes.find(n => 
    n.data.lineStart <= currentLine && currentLine <= n.data.lineEnd
  );
  if (targetNode) {
    setNodes(prev => prev.map(n => ({
      ...n,
      selected: n.id === targetNode.id
    })));
  }
});
```

---

## 3. Real-Time Terminal & SSE Quality Gate Stream

```typescript
export function useVerificationStream(onLog: (line: string) => void, onComplete: (report: any) => void) {
  const runVerify = (code: string) => {
    const eventSource = new EventSource(`/api/verify/stream?code=${encodeURIComponent(code)}`);

    eventSource.onmessage = (event) => {
      const data = JSON.parse(event.data);
      if (data.type === 'LOG') {
        onLog(data.message);
      } else if (data.type === 'COMPLETE') {
        onComplete(data.report);
        eventSource.close();
      }
    };

    eventSource.onerror = () => {
      onLog('[ERROR] Соединение со стримом прервано');
      eventSource.close();
    };
  };

  return { runVerify };
}
```

---

## 4. Drag & Drop AST Blocks Palette
1. **Palette Item (`onDragStart`)**:
```tsx
const onDragStart = (event: React.DragEvent, nodeType: string) => {
  event.dataTransfer.setData('application/reactflow', nodeType);
  event.dataTransfer.effectAllowed = 'move';
};
```
2. **Canvas Drop (`onDrop`)**:
```tsx
const onDrop = (event: React.DragEvent) => {
  event.preventDefault();
  const type = event.dataTransfer.getData('application/reactflow');
  const position = reactFlowInstance.screenToFlowPosition({
    x: event.clientX,
    y: event.clientY,
  });

  const newNode = {
    id: `node_${Date.now()}`,
    type,
    position,
    data: { label: `Новый ${type}`, code: '// код узла' },
  };

  setNodes((nds) => nds.concat(newNode));
};
```
