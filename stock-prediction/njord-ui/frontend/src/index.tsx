import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import AssetView from './AssetView';
import { HashRouter, Route, Routes } from 'react-router-dom';
import Recommended from './Recommended';
import App from './App';

const root = ReactDOM.createRoot(
  document.getElementById('root') as HTMLElement
);
root.render(
  <React.StrictMode>
    <HashRouter>
    <App/>
  </HashRouter>
  </React.StrictMode>
);
