import numpy as np
import pandas as pd
import time
import datetime
import requests

import pymongo
from pymongo import MongoClient
from pymongo.errors import BulkWriteError, DuplicateKeyError
import yfinance as yf
import pandas_datareader as pdr
import finnhub

from prediction import Prediction
import util

finnhub_client = finnhub.Client(api_key="cas9okqad3ifjkt0rcq0")
alpha_vantage_key = "B1OHE9R769FVLIYU"

def mongo_client():
    """
    TODO: Add auth
    """
    return MongoClient()


def get_latest_price(tickers):
    mongo = mongo_client()
    db = mongo.stock_analysis

    ret = {}

    cur = db.daily_samples.aggregate(
        [
            {"$match": {"symbol": {"$in": tickers.tolist()}}},
            {"$sort": {"date": 1}},
            {
                "$group": {
                    "_id": "$symbol",
                    "close": {"$last": "$close"},
                    "date": {"$last": "$date"},
                }
            },
        ]
    )

    for sample in cur:
        ret[sample["_id"]] = {"close": sample["close"], "date": sample["date"]}

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
    start_date = util.normalize_datetime(start_date)
    end_date = util.normalize_datetime(end_date)
    
    mongo = mongo_client()
    db = mongo.stock_analysis

    ret = {}

    for ticker in tickers:
        # Pull all samples within range
        cur = db.daily_samples.find(
            {
                "symbol": {"$eq": ticker},
                "date": {
                    "$gte": start_date,
                    "$lte": end_date,
                },
            }
        )
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
        try:
            # Get the data
            data = pdr.DataReader(
                ticker, "yahoo", start_date, end_date
            )  # yf.download(ticker, start_date, end_date)

            # Clean and format data
            data["date"] = data.index.values.astype("datetime64[ns]")
            data.rename(
                columns={
                    "Open": "open",
                    "High": "high",
                    "Low": "low",
                    "Close": "close",
                    "Adj Close": "adj_close",
                    "Volume": "volume",
                },
                inplace=True,
            )
            data["symbol"] = ticker

            # Insert into mongo
            if not data.empty:
                try:
                    db.daily_samples.insert_many(data.to_dict("records"), ordered=False)
                except BulkWriteError as ex:
                    pass
                    # print(ex)
        except Exception as ex:
            print_exception(ex)
            print(f"Error downloading daily candlesticks for {ticker}")

    mongo.close()

def delete_all_daily_candlesticks(ticker):
    """
    Deletes ALL daily candlesticks for a given ticker. CANNOT BE UNDONE.
    """
    mongo = mongo_client()
    db = mongo.stock_analysis
    db.daily_samples.delete_many({ 'symbol': { '$eq': ticker } })
    mongo.close()


def download_forex_daily_candlesticks(pairs):
    mongo = mongo_client()
    db = mongo.stock_analysis

    for from_sym, to_sym in pairs:
        try:
            # Download candles
            url = f'https://www.alphavantage.co/query?function=FX_DAILY&outputsize=full&from_symbol={from_sym}&to_symbol={to_sym}&apikey={alpha_vantage_key}'
            json = requests.get(url).json()
            candles = json['Time Series FX (Daily)']
            # Transform into a list of dicts
            candles = [{
                'symbol': f'{from_sym}/{to_sym}',
                'open': float(candle["1. open"]),
                'high': float(candle["2. high"]),
                'low': float(candle["3. low"]),
                'close': float(candle["4. close"]),
                'date': util.normalize_datetime(date_str)
                } for date_str, candle in candles.items()]
            # Store in db
            db.daily_samples.insert_many(candles, ordered=False)
        except Exception as ex:
            print(f"Error downloading daily candlesticks for {from_sym}/{to_sym}. Waiting 60s.")
            time.sleep(60)


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
        pipeline = [{"$match": {"ticker": {"$eq": ticker}}}]

        if only_latest:
            pipeline.extend([{"$sort": {"date": -1}}, {"$limit": 1}])

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
            profile["date"] = datetime.datetime.now()
            db.company_profiles.insert_one(profile)
            print(f"Inserted profile for {ticker}")
        except finnhub.FinnhubAPIException:
            print(f"FinnhubAPIException! on {ticker} Sleeping for one minute")
            time.sleep(60)

    mongo.close()


def download_financials_reported(tickers, freq="quarterly", **kwargs):
    mongo = mongo_client()
    db = mongo.stock_analysis

    for ticker in tickers:
        try:
            financials = finnhub_client.financials_reported(
                symbol=ticker, freq=freq, **kwargs
            )
            for entry in financials["data"]:
                try:
                    entry["startDate"] = util.normalize_datetime(entry["startDate"])
                    entry["endDate"] = util.normalize_datetime(entry["endDate"])
                    db.financials_reported.insert_one(entry)
                except DuplicateKeyError as ex:
                    pass
            print(f"Inserted financials for {ticker}")
        except finnhub.FinnhubAPIException:
            print(f"FinnhubAPIException! on {ticker} Sleeping for one minute")
            time.sleep(60)
        except ConnectionError:
            time.sleep(10)
    
    mongo.close()


def get_financials_reported(tickers, start_date, end_date):
    start_date = util.normalize_datetime(start_date)
    end_date = util.normalize_datetime(end_date)

    mongo = mongo_client()
    db = mongo.stock_analysis

    ret = {}

    for ticker in tickers:
        # Pull all samples within range
        # XXX TODO
        cur = db.financials_reported.find(
            {
                'symbol': { '$eq': ticker },
                'startDate': { '$gte': start_date, '$lte': end_date }
            }
        )
        data = pd.DataFrame([sample for sample in cur])
        ret[ticker] = data

    mongo.close()

    return ret

def get_latest_financials_reported(tickers, start_date, end_date, n_latest=1):
    start_date = util.normalize_datetime(start_date)
    end_date = util.normalize_datetime(end_date)

    mongo = mongo_client()
    db = mongo.stock_analysis

    ret = {}

    for ticker in tickers:
        # Pull latest samples
        cur = db.financials_reported.find(
            {
                'symbol': { '$eq': ticker },
                'startDate': { '$gte': start_date, '$lte': end_date }
            }
        ).sort("startDate", pymongo.ASCENDING).limit(n_latest)
        data = pd.DataFrame([sample for sample in cur])
        ret[ticker] = data

    mongo.close()

    return ret

def save_predictions(predictions):
    mongo = mongo_client()
    db = mongo.stock_analysis

    for prediction in predictions:
        try:
            db.model_predictions.insert_one(prediction.asdict())
        except DuplicateKeyError as ex:
            pass
    
    mongo.close()

def get_predictions(tickers, start_date, end_date, model_id=None):
    """
    :returns { [ticker]: [Prediction] }
    """
    start_date = util.normalize_datetime(start_date)
    end_date = util.normalize_datetime(end_date)

    mongo = mongo_client()
    db = mongo.stock_analysis

    ret = {}

    for ticker in tickers:
        query = {
            'ticker': { '$eq': ticker },
            'predict_from_date': { '$gte': start_date, '$lte': end_date }
        }
        if model_id is not None:
            query['model_id'] = { '$eq': model_id }
        # Pull latest samples
        cur = db.model_predictions.find(query).sort("predict_from_date", pymongo.ASCENDING)
        predictions = []
        for sample in cur:
            del sample["_id"]
            predictions.append(Prediction(**sample))
        ret[ticker] = predictions

    mongo.close()

    return ret

def get_all_predictions(start_date, end_date, model_id=None):
    """
    :returns [Prediction]
    """
    start_date = util.normalize_datetime(start_date)
    end_date = util.normalize_datetime(end_date)

    mongo = mongo_client()
    db = mongo.stock_analysis

    query = {
        'predict_from_date': { '$gte': start_date, '$lte': end_date }
    }
    if model_id is not None:
        query['model_id'] = { '$eq': model_id }
    cur = db.model_predictions.find(query).sort("predict_from_date", pymongo.ASCENDING)
    predictions = []
    for sample in cur:
        del sample["_id"]
        predictions.append(Prediction(**sample))
    
    return predictions

def get_model_ids():
    """
    :returns [str]
    """
    start_date = util.normalize_datetime("2010-01-01")
    end_date = util.normalize_datetime("2900-01-01")

    mongo = mongo_client()
    db = mongo.stock_analysis

    query = {
        'predict_from_date': { '$gte': start_date, '$lte': end_date }
    }
    ids = db.model_predictions.find(query).distinct('model_id')
    
    return ids
