import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import AssetView from './AssetView';
import { HashRouter, Route, Routes } from 'react-router-dom';
import Recommended from './Recommended';

const root = ReactDOM.createRoot(
  document.getElementById('root') as HTMLElement
);
root.render(
  <React.StrictMode>
    <HashRouter>
    <Routes>
      <Route path="/" element={<Recommended />} />
      <Route path="/ticker/:ticker" element={<AssetView />} />
    </Routes>
  </HashRouter>
  </React.StrictMode>
);
