import numpy as np
import pandas as pd
import yfinance as yf
import pymongo
from pymongo import MongoClient
from pymongo.errors import BulkWriteError
from datetime import datetime
import finnhub
import time

finnhub_client = finnhub.Client(api_key="cas9okqad3ifjkt0rcq0")

def mongo_client():
    """
    TODO: Add auth
    """
    return MongoClient()

def get_latest_price(tickers):
    mongo = mongo_client()
    db = mongo.stock_analysis

    ret = {}

    cur = db.daily_samples.aggregate([
        { "$match": { 'symbol': { '$in': tickers.tolist() } } },
        { "$sort": { "date": 1 }},
        { "$group": { 
            "_id": "$symbol",
            "close": { "$last": "$close" },
            "date": { "$last": "$date" }
        }},
     ])

    for sample in cur:
        ret[sample['_id']] = {
            'close': sample['close'],
            'date': sample['date']
        }

    """
    for ticker in tickers:
        # Pull all samples within range
        cur = db.daily_samples.find({
                        'symbol': { '$eq': ticker }
                    }).limit(1).sort("date", pymongo.DESCENDING)
        try:
            ret[ticker] = cur[0]
        except:
            print('No price data for ticker', ticker)
    """

    mongo.close()

    return ret

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
                pass
                #print(ex)

    mongo.close()

def get_company_profiles(tickers, only_latest=True):
    """
    Pulls company profile samples from MongoDB.

    TODO FASTER

    only_latest: if True, will only return the latest profile for each ticker.
    """
    mongo = mongo_client()
    db = mongo.stock_analysis

    ret = pd.DataFrame()

    for ticker in tickers:
        pipeline = [{ '$match': { 'ticker': { '$eq': ticker } } }]

        if only_latest:
            pipeline.extend([
                { '$sort': { 'date': -1 } },
                { '$limit': 1 }
            ])

        cur = db.company_profiles.aggregate(pipeline)
        data = [sample for sample in cur]

        for row in data:
            ret = ret.append(row, ignore_index=True)

    mongo.close()

    return pd.DataFrame(ret)

def download_company_profiles(tickers):
    """
    Download basic company profiles from FinnHub, and store in MongoDB. Includes market cap.
    https://finnhub.io/docs/api/company-profile2
    Note: requests are throttled on a minutely basis (currently 60 API calls/minute).
    """
    mongo = mongo_client()
    db = mongo.stock_analysis

    for ticker in tickers:
        try:
            profile = finnhub_client.company_profile2(symbol=ticker)
            profile['date'] = datetime.now()
            db.company_profiles.insert_one(profile)
            print(f"Inserted profile for {ticker}")
        except finnhub.FinnhubAPIException:
            print(f"FinnhubAPIException! on {ticker} Sleeping for one minute")
            time.sleep(60)

    mongo.close()
