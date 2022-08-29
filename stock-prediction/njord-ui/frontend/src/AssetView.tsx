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

  useEffect(() => {
    const earliestDate = new Date("2019-01-01");
    const today = new Date();
    fetch(`/api/predictions?tickers=${ticker}&startDate=${encodeURIComponent(earliestDate.toISOString())}&endDate=${encodeURIComponent(today.toISOString())}&model_id=${modelConfig && modelConfig.modelId}`)
      .then(response => response.json())
      .then(json => setPredictions(json));
    fetch(`/api/daily_candlesticks?tickers=${ticker}&startDate=${encodeURIComponent(earliestDate.toISOString())}&endDate=${encodeURIComponent(today.toISOString())}`)
      .then(response => response.json())
      .then(json => { setCandlesticks(json); return json; });
  }, [ticker, modelConfig?.modelId]);

  return <div className="asset-view">
    <h1>{ticker}</h1>
    <div>
      <div className="asset-predictions">
        {latestPred && <p>
          {latestPred["window"]}-day prediction
           using historical data up to {new Date(latestPred["predict_from_date"]).toLocaleDateString()}:<br/>
          <span className={"asset-model-prediction prediction-" + (latestPred.prediction[0] > 0 ? "pos" : "neg")}>
            {(latestPred.prediction[0]).toFixed(4)}
          </span>
        </p>}
        {/* TODO show expandable history of predictions.
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
        */}
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
              y: predictions[ticker] && predictions[ticker].map((pred: any) => pred.prediction[0]),
              type: 'scattergl',
              mode: 'lines+markers',
              marker: {color: 'blue'},
            }
          ]}
          layout={{
            title: 'Predictions History'
          }} />
      </div>
      <div className="asset-fundamentals"></div>
    </div>
  </div>;
}

export default observer(AssetViewCore);
