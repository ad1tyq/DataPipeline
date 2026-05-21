import React from 'react';
import IdentityGraph from './components/IdentityGraph';
import './App.css';

function App() {
  return (
    <div className="app-container">
      <header className="app-header">
        <h1>Identity Resolution Pipeline</h1>
      </header>
      <main className="graph-container">
        <IdentityGraph />
      </main>
    </div>
  );
}

export default App;
