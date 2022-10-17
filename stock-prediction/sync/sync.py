import sys
import os
sys.path.insert(0, os.path.dirname(os.path.realpath(__file__)) + '/../src')
import pandas as pd
from datetime import date, timedelta

import datastore as ds
from sync_predictions import sync_predictions
from sync_intraday import sync_5min_candlesticks

# tickers = Watchlist + SPY + 200 random tickers
watchlist = pd.read_csv('../data/watchlist.csv')['symbol']
spy = pd.read_csv('../../data/spy_constituents.csv')['Symbol']
all_tickers = pd.read_csv('../../data/all_known_tickers.csv', header=None)[0]
all_tickers = all_tickers.sample(frac=1)
tickers = pd.concat([watchlist, spy, all_tickers.head(200)])
tickers = tickers.sample(frac=1).drop_duplicates()

# Download daily candles for all tickers for the last week
"""
today = date.today()
last_week = today - timedelta(weeks=1)
tomorrow = today + timedelta(days=1)
ds.download_daily_candlesticks(tickers, last_week, tomorrow)
"""

# Download 5-min candles
sync_5min_candlesticks(tickers)

# Sync predictions
sync_predictions(tickers)
