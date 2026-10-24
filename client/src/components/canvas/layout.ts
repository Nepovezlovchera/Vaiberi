import dagre from '@dagrejs/dagre';
import { Node, Edge } from '@xyflow/react';

// Размеры узлов для dagre: единый источник для расчёта и для смещения позиции
function getNodeSize(type?: string): { width: number; height: number } {
  switch (type) {
    case 'condition':
      return { width: 280, height: 70 };
    case 'loop':
      return { width: 300, height: 100 };
    case 'method':
      return { width: 260, height: 80 };
    case 'try':
    case 'switch':
      return { width: 280, height: 90 };
    case 'break':
    case 'continue':
    case 'throw':
      return { width: 220, height: 56 };
    default:
      return { width: 260, height: 70 };
  }
}

export function getLayoutedElements(nodes: Node[], edges: Edge[], direction = 'TB') {
  const dagreGraph = new dagre.graphlib.Graph();
  dagreGraph.setDefaultEdgeLabel(() => ({}));

  dagreGraph.setGraph({
    rankdir: direction,
    nodesep: 40,
    ranksep: 50,
    marginx: 30,
    marginy: 30,
  });

  nodes.forEach((node) => {
    dagreGraph.setNode(node.id, getNodeSize(node.type));
  });

  edges.forEach((edge) => {
    dagreGraph.setEdge(edge.source, edge.target);
  });

  dagre.layout(dagreGraph);

  const layoutedNodes = nodes.map((node) => {
    const nodeWithPosition = dagreGraph.node(node.id);
    const { width, height } = getNodeSize(node.type);

    return {
      ...node,
      position: {
        x: nodeWithPosition.x - width / 2,
        y: nodeWithPosition.y - height / 2,
      },
    };
  });

  return { nodes: layoutedNodes, edges };
}
