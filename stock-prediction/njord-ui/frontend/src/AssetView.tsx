import React, { useEffect, useState } from "react";
import Plot from 'react-plotly.js';

import './AssetView.css';

interface AssetViewProps {
  ticker: string
}

export default function AssetView({ ticker }: AssetViewProps) {
  const [predictions, setPredictions] = useState({} as any);
  const [candlesticks, setCandlesticks] = useState({} as any);

  useEffect(() => {
    const earliestDate = new Date("2020-01-01");
    const today = new Date();
    fetch(`/api/predictions?tickers=${ticker}&startDate=${encodeURIComponent(earliestDate.toISOString())}&endDate=${encodeURIComponent(today.toISOString())}`)
      .then(response => response.json())
      .then(json => setPredictions(json));
    fetch(`/api/daily_candlesticks?tickers=${ticker}&startDate=${encodeURIComponent(earliestDate.toISOString())}&endDate=${encodeURIComponent(today.toISOString())}`)
      .then(response => response.json())
      .then(json => { setCandlesticks(json); return json; });
  }, [ticker]);

  return <div className="asset-view">
    <h1>{ticker}</h1>
    <div>
      <div className="asset-predictions">
        {predictions[ticker] && predictions[ticker].map((pred: any) =>
          <p key={pred["predict_from_date"]}>
            {pred["window"]}-day prediction
            starting from {new Date(pred["predict_from_date"]).toLocaleDateString()}:<br/>
            <span className={"asset-model-prediction asset-model-prediction-" + (pred.prediction[0] > 0 ? "pos" : "neg")}>
              {(pred.prediction[0] * 100).toFixed(2)}
            </span>
          </p>
        )}
        {predictions[ticker] && predictions[ticker].length !== 0 || "No predictions found!"}
      </div>
      <div className="asset-charts">
        <Plot
          data={[
            {
              x: candlesticks[ticker] && candlesticks[ticker]["date"] &&
                 candlesticks[ticker]["date"].map((d: any) => new Date(d)),
              y: candlesticks[ticker] && candlesticks[ticker]["open"],
              type: 'scattergl',
              mode: 'lines',
              marker: {color: 'blue'},
            }
          ]}
          layout={{
            title: 'Price',
            }} />
        <Plot
          data={[
            {
              x: predictions[ticker] && predictions[ticker].map((pred: any) => new Date(pred["predict_from_date"])),
              y: predictions[ticker] && predictions[ticker].map((pred: any) => pred.prediction[0] * 100),
              type: 'scattergl',
              mode: 'lines+markers',
              marker: {color: 'blue'},
            }
          ]}
          layout={{
            //width: 320, height: 240,
            //responsive: true,
            title: 'Predictions History'
            }} />
      </div>
      <div className="asset-fundamentals"></div>
    </div>
  </div>;
}
