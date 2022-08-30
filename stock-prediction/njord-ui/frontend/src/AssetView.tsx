import { observer } from "mobx-react-lite";
import React, { useEffect, useState } from "react";
import Plot from 'react-plotly.js';
import { useParams } from "react-router-dom";

import './AssetView.css';
import ModelConfig from "./store/ModelConfig";

interface AssetViewProps {
  ticker?: string,
  modelConfig?: ModelConfig,
}

const AssetViewCore = (props: AssetViewProps) => {
  let { ticker, modelConfig } = props;
  const params = useParams();
  ticker = ticker ? ticker : params["ticker"]!;
  const [predictions, setPredictions] = useState({} as any);
  const [candlesticks, setCandlesticks] = useState({} as any);
  const latestPred = predictions[ticker] && predictions[ticker].length !== 0 &&
                     predictions[ticker][predictions[ticker].length-1];
  const earliestDate = new Date("2019-01-01");
  const today = new Date();

  // Fetch candlesticks
  useEffect(() => {
    fetch(`/api/daily_candlesticks?tickers=${ticker}&startDate=${encodeURIComponent(earliestDate.toISOString())}&endDate=${encodeURIComponent(today.toISOString())}`)
      .then(response => response.json())
      .then(json => { setCandlesticks(json); return json; });
  }, [ticker]);

  // Fetch predictions
  useEffect(() => {
    fetch(`/api/predictions?tickers=${ticker}&startDate=${encodeURIComponent(earliestDate.toISOString())}&endDate=${encodeURIComponent(today.toISOString())}&model_id=${modelConfig && modelConfig.modelId}`)
      .then(response => response.json())
      .then(json => setPredictions(json));
  }, [ticker, modelConfig?.modelId]);

  return <div className="asset-view">
    <h1>{ticker}</h1>
    <div>
      <div className="asset-predictions">
        {!latestPred ? "No predictions yet. Try another model." : <p>
          {latestPred["window"]}-day prediction
           using historical data up to {new Date(latestPred["predict_from_date"]).toLocaleDateString()}:
          <span className={"asset-model-prediction prediction-" + (latestPred.prediction[0] > 0 ? "pos" : "neg")}>
            {(latestPred.prediction[0]).toFixed(4)}
          </span>
        </p>}
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
          useResizeHandler
          layout={{
            title: 'Price',
            autosize: true,
          }} />
        <Plot
          data={[
            {
              x: predictions[ticker] && predictions[ticker].map((pred: any) => new Date(pred["predict_from_date"])),
              y: predictions[ticker] && predictions[ticker].map((pred: any) => pred.prediction[0]),
              type: 'scattergl',
              mode: 'lines+markers',
              marker: {color: 'blue'},
            }
          ]}
          useResizeHandler
          layout={{
            title: 'Predictions History',
            autosize: true,
          }} />
      </div>
      <div className="asset-fundamentals"></div>
    </div>
  </div>;
}

export default observer(AssetViewCore);
