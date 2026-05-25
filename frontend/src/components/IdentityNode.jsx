import React from 'react';
import { Handle, Position } from '@xyflow/react';

const IdentityNode = (props) => {
  const data = props.data;
  
  let cardClass = "node-card";
  if (data.group === 'master') {
    cardClass = "node-card master-node";
  } else if (data.group === 'property') {
    cardClass = "node-card property-node";
  } else {
    cardClass = "node-card raw-source-node";
  }

  const handleStyle = { position: 'absolute', top: '50%', left: '50%', opacity: 0 };

  return (
    <div className={cardClass}>
      <Handle type="target" position={Position.Top} style={handleStyle} />
      
      <div className="node-title">
        {data.label}
      </div>
      
      <Handle type="source" position={Position.Bottom} style={handleStyle} />
    </div>
  );
};

export default IdentityNode;
