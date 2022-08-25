import React, { useState } from 'react';
import logo from './logo.svg';
import './App.css';
import AssetView from './AssetView';

function App() {
  const [ticker, _setTicker] = useState("");
  const setTicker: (t: any) => void = (ticker: any) => _setTicker(ticker.toUpperCase());
  return <div className="App-wrapper">
    <div>
      <input type="text" onChange={e => setTicker(e.target.value)} />
    </div>
    <div className="App-asset-container">
      <AssetView ticker={ticker}></AssetView>
    </div>
  </div>;
}

export default App;
