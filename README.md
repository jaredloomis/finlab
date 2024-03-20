# Finlab

A collection of tools and experiments around financial data modeling. All the useful stuff is in the `stock-prediction` folder. Notable features:

- Expression language for defining and transforming **Signals** (time-series arrays). Signals can be pulled from APIs, databases, files, etc. For example `rsi<window=14>(candles<interval=5min>)` with give you the 14-period RSI of the 5-minute candles.
- Simple API for training, evaluating, and (de)serializing models.
- Uses **polars** for many core features (still working on the transition from pandas).

This is not the latest version of the code - contact me if this is useful, and I will update it.
