import React, { useEffect, useState } from "react";
import Plot from 'react-plotly.js';

import './AssetView.css';

interface AssetViewProps {
  ticker: string
}

export default function AssetView({ ticker }: AssetViewProps) {
  const [predictions, setPredictions] = useState({} as any);

  useEffect(() => {
    const earliestDate = new Date("2020-01-01");
    const today = new Date();
    fetch(`/api/predictions?tickers=${ticker}&startDate=${encodeURIComponent(earliestDate.toISOString())}&endDate=${encodeURIComponent(today.toISOString())}`)
      .then(response => response.json())
      .then(json => setPredictions(json));
  }, [ticker]);

  return <div className="asset-view">
    <h1>{ticker}</h1>
    <div>
      <h2>Predictions</h2>
      <div className="asset-predictions">
        {predictions[ticker] && predictions[ticker].map((pred: any) => <>
          <p>
            {pred["window"]}-day prediction
            starting from {new Date(pred["predict_from_date"]).toLocaleDateString()}:<br/>
            <span className="asset-model-prediction">{pred.prediction[0]}</span>
          </p>
          <code>{JSON.stringify(pred)}</code>
        </>)}
        {predictions[ticker] && predictions[ticker].length !== 0 || "No predictions found!"}
      </div>
      <h2>Charts</h2>
      <div className="asset-charts">
      <Plot
        data={[
          {
            x: predictions[ticker] && predictions[ticker].map((pred: any) => new Date(pred["predict_from_date"])),
            y: predictions[ticker] && predictions[ticker].map((pred: any) => pred["prediction"][0]),
            type: 'scatter',
            //mode: 'lines+markers',
            //marker: {color: 'red'},
          }
        ]}

        layout={ {width: 320, height: 240, title: 'A Fancy Plot'} }

        />
      </div>
      <h2>Fundamentals</h2>
      <div className="asset-fundamentals"></div>
    </div>
  </div>;
}
