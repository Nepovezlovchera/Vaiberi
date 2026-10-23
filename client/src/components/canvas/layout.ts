import dagre from '@dagrejs/dagre';
import { Node, Edge } from '@xyflow/react';

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
    const width = node.type === 'condition' ? 280 : node.type === 'loop' ? 300 : 260;
    const height = node.type === 'method' ? 80 : node.type === 'loop' ? 100 : 70;
    dagreGraph.setNode(node.id, { width, height });
  });

  edges.forEach((edge) => {
    dagreGraph.setEdge(edge.source, edge.target);
  });

  dagre.layout(dagreGraph);

  const layoutedNodes = nodes.map((node) => {
    const nodeWithPosition = dagreGraph.node(node.id);
    const width = node.type === 'condition' ? 280 : node.type === 'loop' ? 300 : 260;
    const height = node.type === 'method' ? 80 : node.type === 'loop' ? 100 : 70;

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
