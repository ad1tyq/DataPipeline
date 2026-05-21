import React from 'react';
import { Handle, Position } from '@xyflow/react';

const IdentityNode = (props) => {
  const data = props.data;
  
  let cardClass = "node-card";
  if (data.group === 'master') {
    cardClass = "node-card master-node";
  }

  return (
    <div className={cardClass}>
      <Handle type="target" position={Position.Top} />
      
      <div className="node-title">
        {data.label}
      </div>
      
      <div className="node-group">
        {data.group.toUpperCase()}
      </div>
      
      <Handle type="source" position={Position.Bottom} />
    </div>
  );
};

export default IdentityNode;
