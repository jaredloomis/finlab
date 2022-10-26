import datastore as ds


def sync_5min_candlesticks(tickers):
    ds.download_candles_intraday_extended(tickers)
