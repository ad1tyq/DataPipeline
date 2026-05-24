import React, { useState, useEffect } from 'react';
import { ReactFlow, Controls, Background, useNodesState, useEdgesState, MarkerType } from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import dagre from 'dagre';
import { fetchIdentityGraph } from '../api/fetch';
import './legend.css';
import IdentityNode from './IdentityNode';
import DetailedPanel from './DetailedPanel';

const nodeTypes = {
  custom: IdentityNode,
};

const getLayoutedElements = (nodes, edges) => {
  const masterNodes = nodes.filter(n => n.data.group === 'master');
  
  const columns = 5;
  const gridSpacingX = 800;
  const gridSpacingY = 800;
  
  const positionedNodes = [];
  const masterToRaws = {};
  
  masterNodes.forEach(m => masterToRaws[m.id] = []);
  
  edges.forEach(e => {
    if (e.label === 'AGGREGATES') {
      if (masterToRaws[e.source]) {
        masterToRaws[e.source].push(e.target);
      }
    }
  });

  const positionedIds = new Set();

  masterNodes.forEach((master, index) => {
    const col = index % columns;
    const row = Math.floor(index / columns);
    
    // Master Node Dimensions (to center it visually)
    const masterWidth = 180;
    const masterHeight = 180;
    
    const centerX = col * gridSpacingX;
    const centerY = row * gridSpacingY;
    
    positionedNodes.push({
      ...master,
      position: { x: centerX - masterWidth / 2, y: centerY - masterHeight / 2 }
    });
    positionedIds.add(master.id);

    const rawIds = masterToRaws[master.id] || [];
    const numPlanets = rawIds.length;
    const orbitRadius = 250; 
    
    const rawWidth = 150;
    const rawHeight = 150;

    rawIds.forEach((rawId, i) => {
      const angle = (i * 2 * Math.PI) / numPlanets;
      
      const planetX = centerX + orbitRadius * Math.cos(angle);
      const planetY = centerY + orbitRadius * Math.sin(angle);
      
      const rawNode = nodes.find(n => n.id === rawId);
      if (rawNode && !positionedIds.has(rawId)) {
        positionedNodes.push({
          ...rawNode,
          position: { x: planetX - rawWidth / 2, y: planetY - rawHeight / 2 }
        });
        positionedIds.add(rawId);
      }
    });
  });

  nodes.forEach(n => {
    if (!positionedIds.has(n.id)) {
      positionedNodes.push({
        ...n,
        position: { x: Math.random() * 800, y: Math.random() * 800 }
      });
    }
  });

  return { nodes: positionedNodes, edges: edges };
};

const IdentityGraph = () => {
  const [nodes, setNodes, onNodesChange] = useNodesState([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState([]);
  const [selectedNode, setSelectedNode] = useState(null);

  useEffect(() => {
    fetchIdentityGraph().then((data) => {
      const transformedNodes = data.nodes.map((node) => {
        let label = node.label;
        let prefix = 'raw-';

        if (node.group === 'master') {
          label = node.label.replace(' (Master)', '').toUpperCase();
          prefix = 'master-';
        } else {
          const match = node.label.match(/\(([^)]+)\)$/);
          if (match) {
            label = match[1];
          }
        }

        return {
          id: prefix + node.id,
          type: 'custom',
          data: { label: label, group: node.group, attributes: node.data },
          position: { x: 0, y: 0 }
        };
      });

      const transformedEdges = data.edges.map((edge, index) => {
        let sourceId = 'raw-' + edge.source;
        if (edge.label === 'AGGREGATES') {
          sourceId = 'master-' + edge.source;
        }
        const targetId = 'raw-' + edge.target;

        return {
          id: `edge-${sourceId}-${targetId}-${index}`,
          source: sourceId,
          target: targetId,
          label: edge.label,
          type: edge.label === 'POSSIBLE_DUPLICATE' ? 'straight' : 'default',
          animated: edge.label === 'POSSIBLE_DUPLICATE',
          style: { 
            stroke: edge.label === 'POSSIBLE_DUPLICATE' ? '#ef4444' : '#94a3b8', 
            strokeWidth: edge.label === 'POSSIBLE_DUPLICATE' ? 2 : 1 
          },
          markerEnd: { 
            type: MarkerType.ArrowClosed, 
            color: edge.label === 'POSSIBLE_DUPLICATE' ? '#ef4444' : '#94a3b8' 
          }
        };
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

      <div className="legend-container">
        <h3 className="legend-title">Identity Resolution Graph</h3>
        <p className="legend-subtitle">Hover over nodes to see underlying data.</p>
        
        <div className="legend-item">
          <div className="legend-icon"><div className="legend-circle-golden"></div></div>
          <span>Golden Record (Master)</span>
        </div>
        
        <div className="legend-item">
          <div className="legend-icon"><div className="legend-circle-raw"></div></div>
          <span>Raw Source System</span>
        </div>
        
        <div className="legend-item">
          <div className="legend-icon"><div className="legend-line-solid"></div></div>
          <span>Confirmed Match</span>
        </div>
        
        <div className="legend-item">
          <div className="legend-icon"><div className="legend-line-dashed"></div></div>
          <span>Possible Duplicate</span>
        </div>
      </div>

      <DetailedPanel node={selectedNode} onClose={handleClosePanel} />
    </div>
  );
};

export default IdentityGraph;
