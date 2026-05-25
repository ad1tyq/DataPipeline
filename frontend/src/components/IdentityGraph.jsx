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
  
  const gridSpacingX = 1000;
  const gridSpacingY = 800;
  
  const positionedNodes = [];
  const masterToRaws = {};
  
  masterNodes.forEach(m => masterToRaws[m.id] = []);
  
  edges.forEach(e => {
    if (e.label && e.label.startsWith('AGGREGATES')) {
      if (masterToRaws[e.source]) {
        masterToRaws[e.source].push(e.target);
      }
    }
  });

  const positionedIds = new Set();

  masterNodes.forEach((master, index) => {
    const col = index;
    const row = 0;
    
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
      const cols = 3;
      const spacingX = 180;
      const spacingY = 180;
      
      const isUp = i % 2 === 0;
      const localIndex = Math.floor(i / 2);
      
      const planetCol = localIndex % cols;
      const planetRow = Math.floor(localIndex / cols);
      
      const groupSize = isUp ? Math.ceil(numPlanets / 2) : Math.floor(numPlanets / 2);
      const nodesInRow = Math.min(cols, groupSize - planetRow * cols);
      
      const startX = centerX - ((nodesInRow - 1) * spacingX) / 2;
      const planetX = startX + planetCol * spacingX;
      
      const planetY = isUp 
          ? centerY - 250 - planetRow * spacingY 
          : centerY + 250 + planetRow * spacingY;
      
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
        position: { x: Math.random() * 2000, y: 500 + Math.random() * 800 }
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
        const attributes = { ...node.data };

        if (node.group === 'master') {
          const masterName = node.label.replace(' (Master)', '');
          label = masterName.toUpperCase();
          prefix = 'master-';
          attributes.displayName = masterName;
        } else {
          const match = node.label.match(/^(.*?) \(([^)]+)\)$/);
          if (match) {
            attributes.fullName = match[1].trim();
            attributes.sourceSystem = match[2];
            label = match[2];
          } else {
            const fallbackMatch = node.label.match(/\(([^)]+)\)$/);
            if (fallbackMatch) {
              label = fallbackMatch[1];
              attributes.sourceSystem = fallbackMatch[1];
            }
          }
        }

        return {
          id: prefix + node.id,
          type: 'custom',
          data: { label: label, group: node.group, attributes: attributes },
          position: { x: 0, y: 0 }
        };
      });

      const transformedEdges = data.edges.map((edge, index) => {
        let sourceId = 'raw-' + edge.source;
        if (edge.label && edge.label.startsWith('AGGREGATES')) {
          sourceId = 'master-' + edge.source;
        }
        const targetId = 'raw-' + edge.target;

        const isAggregation = edge.label && edge.label.startsWith('AGGREGATES');
        const isCrossConnection = !isAggregation;
        const isDuplicate = edge.label && edge.label.startsWith('POSSIBLE_DUPLICATE');

        return {
          id: `edge-${sourceId}-${targetId}-${index}`,
          source: sourceId,
          target: targetId,
          label: edge.label,
          type: isCrossConnection ? 'default' : 'straight', 
          animated: isDuplicate,
          zIndex: isCrossConnection ? 10 : 0, 
          style: { 
            stroke: isDuplicate ? '#ef4444' : '#94a3b8', 
            strokeWidth: isDuplicate ? 2 : 1 
          },
          markerEnd: { 
            type: MarkerType.ArrowClosed, 
            color: isDuplicate ? '#ef4444' : '#94a3b8' 
          }
        };
      });

      const masterNodesList = transformedNodes.filter(n => n.data.group === 'master');
      for (let i = 0; i < masterNodesList.length - 1; i++) {
        transformedEdges.push({
          id: `edge-golden-${masterNodesList[i].id}-${masterNodesList[i+1].id}`,
          source: masterNodesList[i].id,
          target: masterNodesList[i+1].id,
          type: 'straight',
          style: { stroke: '#fbbf24', strokeWidth: 4 },
          markerEnd: {
            type: MarkerType.ArrowClosed,
            color: '#fbbf24'
          }
        });
      }

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
        minZoom={0.05}
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
