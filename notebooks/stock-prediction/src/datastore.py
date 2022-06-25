import numpy as np
import pandas as pd
import yfinance as yf
from pymongo import MongoClient
from pymongo.errors import BulkWriteError
from datetime import datetime

def mongo_client():
    """
    TODO: Add auth
    """
    return MongoClient()

def get_daily_candlesticks(tickers, start_date, end_date):
    mongo = mongo_client()
    db = mongo.stock_analysis

    ret = {}

    for ticker in tickers:
        # Pull all samples within range
        cur = db.daily_samples.find({
            'symbol': { '$eq': ticker },
            'date': { '$gt': datetime.fromisoformat(start_date), '$lt': datetime.fromisoformat(end_date) }
        })
        data = pd.DataFrame([sample for sample in cur])
        ret[ticker] = data

    mongo.close()

    return ret

def download_daily_candlesticks(tickers, start_date, end_date):
    """
    Download daily candlesticks from yfinance and store them in MongoDB.
    """
    mongo = mongo_client()
    db = mongo.stock_analysis

    for ticker in tickers:
        print(ticker)
        # Get the data
        data = yf.download(ticker, start_date, end_date)
        
        # Clean and format data
        data['date'] = data.index.values.astype("datetime64[ns]")
        data.rename(columns = {
            'Open': 'open',
            'High': 'high',
            'Low': 'low',
            'Close': 'close',
            'Adj Close': 'adj_close',
            'Volume': 'volume'
        }, inplace = True)
        data['symbol'] = ticker

        # Insert into mongo
        if not data.empty:
            try:
                db.daily_samples.insert_many(data.to_dict('records'), ordered=False)
            except BulkWriteError as ex:
                print(ex)

    mongo.close()
