import React from 'react';

const DetailedPanel = (props) => {
  const node = props.node;
  const onClose = props.onClose;

  if (!node) {
    return null;
  }

  const data = node.data;

  const renderValue = (value) => {
    if (typeof value === 'object') {
      return JSON.stringify(value);
    }
    return String(value);
  };

  const hasAttributes = data.attributes && Object.keys(data.attributes).length > 0;

  return (
    <div className="detailed-panel">
      <div className="panel-header">
        <h2>{data.label}</h2>
        <button onClick={onClose}>&times;</button>
      </div>

      <p><strong>Group:</strong> {data.group}</p>

      <h3>Attributes</h3>
      
      {hasAttributes ? (
        <table className="attributes-table">
          <tbody>
            {Object.keys(data.attributes).map((key) => {
              const value = data.attributes[key];
              return (
                <tr key={key}>
                  <th>{key}</th>
                  <td>{renderValue(value)}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      ) : (
        <p>No attributes available.</p>
      )}
    </div>
  );
};

export default DetailedPanel;
