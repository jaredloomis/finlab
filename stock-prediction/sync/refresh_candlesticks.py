# In case of a stock split, run:
# python refresh_candlesticks.py TICKER
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.realpath(__file__)) + '/../src')

import datastore as ds

ticker = sys.argv[1]

if ticker is None:
    print("Pass a ticker:\n> python refresh_candlesticks.py TICKER")
else:
    ds.delete_all_daily_candlesticks(ticker)
    ds.download_daily_candlesticks([ticker], "2000-01-01", "2222-01-01")
