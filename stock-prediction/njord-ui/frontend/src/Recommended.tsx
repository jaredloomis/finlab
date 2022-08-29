import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";

import { DateTime, Duration } from 'luxon';

import './AssetView.css';
import './Recommended.css';
import { observer } from "mobx-react-lite";
import ModelConfig from "./store/ModelConfig";

interface RecommendedProps {
  modelConfig?: ModelConfig;
}

function RecommendedCore({ modelConfig }: RecommendedProps) {
  const [predictions, setPredictions] = useState([] as any[]);
  const yesterday = DateTime.now().minus(Duration.fromObject({ days: 1 })).toISODate();
  const tomorrow = DateTime.now().plus(Duration.fromObject({ days: 1 })).toISODate();

  useEffect(() => {
    // Get all predictions within the last two days
    fetch(`/api/predictions?startDate=${encodeURIComponent(yesterday)}&endDate=${encodeURIComponent(tomorrow)}&model_id=${modelConfig?.modelId}`)
      .then(response => response.json())
      // Sort by absolute value of `prediction`
      .then(predictions =>
        predictions.sort((a: any, b: any) => Math.abs(b.prediction[0]) - Math.abs(a.prediction[0]))
      )
      // Get the top 100 and save to state
      .then(predictions => setPredictions(predictions.slice(0, 100)))
  }, [modelConfig?.modelId]);

  return <div className="Recommended-container">
    <h1>High-Change Assets</h1>
    <div className="Recommended-list">
      {predictions.map((p, i) =>
        <div className="Recommended-item" key={i}>
          <Link to={`/ticker/${p.ticker}`}>{p.ticker}</Link>
          <span className={"asset-model-prediction prediction-" + (p.prediction[0] > 0 ? 'pos' : 'neg')}>{p.prediction[0]}</span>
        </div>
      )}
    </div>
  </div>;
}

export default observer(RecommendedCore);
