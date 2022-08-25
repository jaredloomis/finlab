import numpy as np
from datetime import date, timedelta, datetime
from typing import NamedTuple, Union, Any

import datastore as ds
import util

class Prediction(NamedTuple):
    model_id: str
    created_date: Union[date, datetime, str]
    predict_from_date: Union[date, datetime, str]
    window: int
    ticker: str
    prediction: Any
    """
    def __init__(self, model_id, created_date, predict_from_date, ticker, prediction):
        self.model_id = model_id
        self.created_date = created_date
        self.predict_from_date = predict_from_date
        self.ticker = ticker
        self.prediction = prediction
    """

    def asdict(self):
        d = self._asdict()
        d["created_date"] = util.normalize_datetime(d["created_date"])
        d["predict_from_date"] = util.normalize_datetime(d["predict_from_date"])
        if isinstance(d["prediction"], np.ndarray):
            d["prediction"] = d["prediction"].tolist()
        return d

    def __repr__(self):
        return str(self)

    def __str__(self):
        return f"Prediction(model_id={self.model_id}, created_date={self.created_date}, predict_from_date={self.predict_from_date}, window={self.window}, ticker={self.ticker}, prediction={self.prediction})"


def predict_price_change(model, mk_signal_set, tickers, predict_from_date=None, min_samples=1000):
    predict_from_date = predict_from_date if predict_from_date is not None else date.today()
    lookback_date = predict_from_date - timedelta(days=min_samples)

    candles = ds.get_daily_candlesticks(tickers, lookback_date, predict_from_date)

    predictions = {}
    for ticker in tickers:
        try:
            #print(candles)
            signals = mk_signal_set(candles[ticker])
            X, y, _ = signals.to_xy()
            y_pred = model(X[-1, :])

            predictions[ticker] = Prediction(model.id, date.today(), predict_from_date, model.window, ticker, y_pred)
        except Exception as ex:
            print('Exception on', ticker)
            util.print_exception(ex)

    return predictions
