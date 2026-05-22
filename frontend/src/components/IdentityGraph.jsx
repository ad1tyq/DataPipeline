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
  dagreGraph.setGraph({ rankdir: 'TB', nodesep: 80, ranksep: 80 });

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
    if (node.data.group === 'master') { width = 220; height = 220; }
    if (node.data.group === 'property') { width = 90; height = 90; }
    let x = nodeWithPosition.x - width / 2;
    let y = nodeWithPosition.y - height / 2;

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

      const masterNodes = data.nodes.filter(n => n.group === 'master');

      data.nodes.forEach((n) => {
        let label = n.label;
        let prefix = 'raw-';

        if (n.group === 'master') {
          label = n.label.replace(' (Master)', '').toUpperCase();
          prefix = 'master-';
        } else {
          const match = n.label.match(/\(([^)]+)\)$/);
          if (match) {
            label = match[1];
          }
        }

        transformedNodes.push({
          id: prefix + n.id,
          type: 'custom',
          data: { label: label, group: n.group, attributes: n.data },
          position: { x: 0, y: 0 }
        });
      });

      const globalEmailNodeId = 'prop-email-global';
      let hasEmailConnections = false;

      masterNodes.forEach((master) => {
        const masterId = 'master-' + master.id;
        
        const childEdges = data.edges.filter(e => e.source === master.id && e.label === 'AGGREGATES');

        if (childEdges.length > 0) {
          hasEmailConnections = true;

          transformedEdges.push({
            id: `edge-${masterId}-global-email`,
            source: masterId,
            target: globalEmailNodeId,
            markerEnd: { type: MarkerType.ArrowClosed }
          });

          childEdges.forEach((e) => {
            transformedEdges.push({
              id: `edge-raw-global-email-${e.target}`,
              source: 'raw-' + e.target,
              target: globalEmailNodeId,
              markerEnd: { type: MarkerType.ArrowClosed }
            });
          });
        }
      });

      if (hasEmailConnections) {
        transformedNodes.push({
          id: globalEmailNodeId,
          type: 'custom',
          data: { label: 'email', group: 'property' },
          position: { x: 0, y: 0 }
        });
      }

      data.edges.forEach((e, index) => {
        let sourceId = 'raw-' + e.source;
        if (e.label === 'AGGREGATES') {
          sourceId = 'master-' + e.source;
        }
        const targetId = 'raw-' + e.target;

        transformedEdges.push({
          id: `edge-${index}-${sourceId}-${targetId}`,
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
