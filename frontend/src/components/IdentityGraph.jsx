import React, { useState, useEffect } from 'react';
import { ReactFlow, Controls, Background, useNodesState, useEdgesState, MarkerType } from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import dagre from 'dagre';
import { fetchIdentityGraph } from '../api/fetch';
import IdentityNode from './IdentityNode';
import DetailedPanel from './DetailedPanel';

const nodeTypes = {
  custom: IdentityNode,
};

const getLayoutedElements = (nodes, edges) => {
  const dagreGraph = new dagre.graphlib.Graph();
  dagreGraph.setDefaultEdgeLabel(() => ({}));
  dagreGraph.setGraph({ rankdir: 'TB' });

  nodes.forEach((node) => {
    let width = 150, height = 150;
    if (node.data.group === 'master') { width = 250; height = 250; }
    if (node.data.group === 'property') { width = 90; height = 90; }
    dagreGraph.setNode(node.id, { width, height });
  });

  edges.forEach((edge) => {
    dagreGraph.setEdge(edge.source, edge.target);
  });

  dagre.layout(dagreGraph);

  const newNodes = nodes.map((node) => {
    const nodeWithPosition = dagreGraph.node(node.id);
    let width = 150, height = 150;
    if (node.data.group === 'master') { width = 250; height = 250; }
    if (node.data.group === 'property') { width = 90; height = 90; }
    let x = nodeWithPosition.x - width / 2;
    let y = nodeWithPosition.y - height / 2;

    if (node.data.label === 'CORE_CBS') {
      x -= 120; 
    }

    return {
      ...node,
      targetPosition: 'top',
      sourcePosition: 'bottom',
      position: { x, y },
    };
  });

  return { nodes: newNodes, edges: edges };
};

const IdentityGraph = () => {
  const [nodes, setNodes, onNodesChange] = useNodesState([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState([]);
  const [selectedNode, setSelectedNode] = useState(null);

  useEffect(() => {
    fetchIdentityGraph().then((data) => {
      const transformedNodes = [];
      const transformedEdges = [];

      let masterId = null;

      data.nodes.forEach((n) => {
        let label = n.label;
        let prefix = 'raw-';
        if (n.group === 'master') {
          label = 'PRIYA';
          prefix = 'master-';
          masterId = prefix + n.id;
        } else if (n.label.includes('CORE_CBS')) {
          label = 'CORE_CBS';
        } else if (n.label.includes('EMAIL')) {
          label = 'EMAIL';
        }

        transformedNodes.push({
          id: prefix + n.id,
          type: 'custom',
          data: { label: label, group: n.group, attributes: n.data },
          position: { x: 0, y: 0 }
        });
      });

      if (masterId) {
        const emailNodeId = 'prop-email';
        transformedNodes.push({
          id: emailNodeId,
          type: 'custom',
          data: { label: 'email', group: 'property' },
          position: { x: 0, y: 0 }
        });

        transformedEdges.push({
          id: 'edge-master-email',
          source: masterId,
          target: emailNodeId,
          markerEnd: { type: MarkerType.ArrowClosed }
        });

        data.nodes.forEach((n) => {
          if (n.group === 'raw_source') {
            transformedEdges.push({
              id: 'edge-raw-email-' + n.id,
              source: 'raw-' + n.id,
              target: emailNodeId,
              markerEnd: { type: MarkerType.ArrowClosed }
            });
          }
        });
      }

      data.edges.forEach((e, index) => {
        let sourceId = 'raw-' + e.source;
        if (e.label === 'AGGREGATES') {
          sourceId = 'master-' + e.source;
        }
        const targetId = 'raw-' + e.target;

        transformedEdges.push({
          id: 'edge-' + index + '-' + sourceId + '-' + targetId,
          source: sourceId,
          target: targetId,
          markerEnd: { type: MarkerType.ArrowClosed }
        });
      });

      const layouted = getLayoutedElements(transformedNodes, transformedEdges);
      
      setNodes(layouted.nodes);
      setEdges(layouted.edges);
    });
  }, []);

  const handleNodeClick = (event, node) => {
    setSelectedNode(node);
  };

  const handleClosePanel = () => {
    setSelectedNode(null);
  };

  return (
    <div className="graph-wrapper">
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onNodeClick={handleNodeClick}
        nodeTypes={nodeTypes}
        fitView
      >
        <Controls />
        <Background color="#f8fafc" gap={16} />
      </ReactFlow>
      
      <DetailedPanel node={selectedNode} onClose={handleClosePanel} />
    </div>
  );
};

export default IdentityGraph;
