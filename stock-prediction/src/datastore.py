from io import StringIO

import pandas as pd
import time
import datetime
import requests
import json

import pymongo
from pymongo import MongoClient
from pymongo.errors import BulkWriteError, DuplicateKeyError
import pymongo.results
import pandas_datareader as pdr
import finnhub
from datetime import timedelta

from prediction import Prediction
from model_env import ModelEnv
import util
from model import Model

finnhub_api_key = "cas9okqad3ifjkt0rcq0"
finnhub_client = finnhub.Client(api_key=finnhub_api_key)
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

    cur = db.candles_1day.aggregate(
        [
            {"$match": {"symbol": {"$in": tickers.tolist()}}},
            {"$sort": {"time": 1}},
            {
                "$group": {
                    "_id": "$symbol",
                    "close": {"$last": "$close"},
                    "time": {"$last": "time"},
                }
            },
        ]
    )

    for sample in cur:
        ret[sample["_id"]] = {"close": sample["close"], "time": sample["time"]}

    """
    for ticker in tickers:
        # Pull all samples within range
        cur = db.candles_1day.find({
                        'symbol': { '$eq': ticker }
                    }).limit(1).sort("date", pymongo.DESCENDING)
        try:
            ret[ticker] = cur[0]
        except:
            print('No price data for ticker', ticker)
    """

    mongo.close()

    return ret


def download_candles_intraday_extended(tickers, interval='5min'):
    mongo = mongo_client()
    db = mongo.stock_analysis

    MAX_REQUESTS = 500
    request_count = 0

    for ticker in tickers:
        for year in [1, 2]:
            for month in range(1, 13):
                for retry in range(3):
                    try:
                        if request_count > MAX_REQUESTS:
                            print('download_candles: Hit maximum daily requests for  alphavantage API')
                            return
                        request_count += 1

                        # Make request
                        month_slice = f'year{year}month{month}'
                        url = f'https://www.alphavantage.co/query?function=TIME_SERIES_INTRADAY_EXTENDED \
                              &symbol={ticker}&interval={interval}&slice={month_slice}&apikey={alpha_vantage_key}' \
                              .replace(' ', '')
                        res = requests.get(url)

                        # Check for JSON rate limit error
                        err = None
                        try:
                            err = json.loads(res.text)
                        except:
                            pass
                        if err is not None:
                            print(f"Error 1 downloading intraday_history_extended for {ticker}. Waiting 60s.")
                            print(url)
                            print(err)
                            time.sleep(60)
                            continue

                        # Parse CSV
                        candles_csv = res.text
                        candles = pd.read_csv(StringIO(candles_csv))
                        candles = candles.assign(symbol=ticker)
                        candles = candles.to_dict('records')

                        # Store in db
                        if len(candles) > 0:
                            db[f'candles_{interval}'].insert_many(candles, ordered=False)
                            print('saved', ticker, 'intraday_history_extended')
                        break
                    except pymongo.errors.BulkWriteError:
                        continue
                    except Exception as ex:
                        # For any unknown error, print and wait 60s to try again
                        util.print_exception(ex)
                        print(f"Error 2 downloading intraday_history_extended for {ticker}. Waiting 60s.")
                        time.sleep(60)


def download_candles_intraday(tickers, interval='5min'):
    mongo = mongo_client()
    db = mongo.stock_analysis

    MAX_REQUESTS = 500
    request_count = 0

    for ticker in tickers:
        for retry in range(3):
            try:
                if request_count > MAX_REQUESTS:
                    print('download_candles: Hit maximum daily requests for  alphavantage API')
                    return
                request_count += 1

                # Make request
                url = f'https://www.alphavantage.co/query?function=TIME_SERIES_INTRADAY \
                      &symbol={ticker}&interval={interval}&apikey={alpha_vantage_key}' \
                      .replace(' ', '')
                res = requests.get(url)

                # Check for JSON rate limit error
                err = None
                try:
                    err = json.loads(res.text)
                except Exception as ex:
                    print(ex)
                    util.print_exception(ex)
                    pass
                if err is not None:
                    print(f"Error downloading intraday_candles for {ticker}. Waiting 60s.")
                    print(err)
                    time.sleep(60)
                    continue

                # Parse CSV
                candles_csv = res.text
                candles = pd.read_csv(StringIO(candles_csv))
                candles = candles.assign(symbol=ticker)
                candles = candles.to_dict('records')

                # Store in db
                if len(candles) > 0:
                    db[f'candles_{interval}'].insert_many(candles, ordered=False)
                break
            except pymongo.errors.BulkWriteError:
                continue
            except Exception as ex:
                # For any unknown error, print and wait 60s to try again
                util.print_exception(ex)
                print(f"Error downloading intraday_history for {ticker}. Waiting 60s.")
                time.sleep(60)


def get_candles(tickers, start, end, interval='5min'):
    start = util.normalize_datetime(start)
    end = util.normalize_datetime(end)
    _, interval = util.parse_interval(interval)

    mongo = mongo_client()
    db = mongo.stock_analysis

    ret = {}

    for ticker in tickers:
        # Pull all samples within range
        cur = db[f'candles_{interval}'].find(
            {
                "symbol": {"$eq": ticker},
                "time": {
                    "$gte": start,
                    "$lte": end,
                },
            }
        )
        data = pd.DataFrame([sample for sample in cur])
        data.index = data['time']
        ret[ticker] = data

    mongo.close()

    return ret


def download_candles(tickers, start_date, end_date, interval='1day') -> None:
    delta, interval = util.parse_interval(interval)

    if delta >= timedelta(days=1):
        download_daily_candlesticks(tickers, start_date, end_date)
    else:
        # TODO determine when to use the extended call vs standard
        download_candles_intraday_extended(tickers, interval=interval)


def download_last_price_updates(tickers):
    import websocket

    mongo = mongo_client()
    db = mongo.stock_analysis

    def on_message(ws, message):
        try:
            message = json.loads(message)
            db.ticks.insert_many(message['data'])
        except Exception as ex:
            util.print_exception(ex)

    def on_error(ws, error):
        print(error)

    def on_close(ws):
        print("### closed ###")

    def on_open(ws):
        for ticker in tickers:
            ws.send('{"type":"subscribe","symbol":"' + ticker + '"}')

    websocket.enableTrace(True)
    ws = websocket.WebSocketApp("wss://ws.finnhub.io?token=" + finnhub_api_key,
                                on_message=on_message,
                                on_error=on_error,
                                on_close=on_close)
    ws.on_open = on_open
    ws.run_forever()


def get_daily_candlesticks(tickers, start_date, end_date):
    return get_candles(tickers, start_date, end_date, interval='1day')
"""
    start_date = util.normalize_datetime(start_date)
    end_date = util.normalize_datetime(end_date)

    mongo = mongo_client()
    db = mongo.stock_analysis

    ret = {}

    for ticker in tickers:
        # Pull all samples within range
        cur = db.candles_1day.find(
            {
                "symbol": {"$eq": ticker},
                "time": {
                    "$gte": start_date,
                    "$lte": end_date,
                },
            }
        )
        data = pd.DataFrame([sample for sample in cur])
        ret[ticker] = data

    mongo.close()

    return ret
"""


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
            data["time"] = data.index.values.astype("datetime64[ns]")
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

            print(data)

            # Insert into mongo
            if not data.empty:
                try:
                    db.candles_1day.insert_many(data.to_dict("records"), ordered=False)
                except BulkWriteError as ex:
                    pass
                    # print(ex)
        except Exception as ex:
            util.print_exception(ex)
            print(f"Error downloading daily candlesticks for {ticker}")

    mongo.close()


def delete_all_daily_candlesticks(ticker):
    """
    Deletes ALL daily candlesticks for a given ticker. CANNOT BE UNDONE.
    """
    mongo = mongo_client()
    db = mongo.stock_analysis
    db.candles_1day.delete_many({'symbol': {'$eq': ticker}})
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
            db.candles_1day.insert_many(candles, ordered=False)
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
            print(f"Inserted {len(financials['data'])} financials for {ticker}")
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
                'symbol': {'$eq': ticker},
                'startDate': {'$gte': start_date, '$lte': end_date}
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
                'symbol': {'$eq': ticker},
                'startDate': {'$gte': start_date, '$lte': end_date}
            }
        ).sort("startDate", pymongo.ASCENDING) \
            .sort("endDate", pymongo.ASCENDING) \
            .limit(n_latest)

        data = pd.DataFrame([sample for sample in cur])
        ret[ticker] = data

    mongo.close()

    return ret


def get_financials_reported_attrs(tickers, attrs, date):
    date = util.normalize_datetime(date)

    mongo = mongo_client()
    db = mongo.stock_analysis

    ret = {}

    for ticker in tickers:
        # Pull the most recent sample before date
        samples = list(db.financials_reported.find(
            {
                'symbol': {'$eq': ticker},
                'startDate': {'$lte': date},
                'endDate': {'$gte': date},
            }
        ).sort("startDate", pymongo.DESCENDING)
         .sort("endDate", pymongo.ASCENDING)
         .limit(1))

        if len(samples) == 0:
            continue
        else:
            sample = samples[0]

        ret[ticker] = {}
        # For each attr, search through all the reports for a matching value
        if sample is not None and sample['report'] is not None:
            for attr in attrs:
                for category in ['bs', 'cf', 'ic']:
                    cat_attrs = sample['report'][category]
                    # The schema on finnhub.io shows this format
                    if isinstance(cat_attrs, dict):
                        if attr in cat_attrs.keys():
                            ret[ticker][attr] = cat_attrs[attr]
                            break
                    # But all (most?) samples I've found are in this format
                    elif isinstance(cat_attrs, list):
                        for attr_entry in cat_attrs:
                            if attr_entry['concept'] == attr:
                                ret[ticker][attr] = attr_entry['value']
                                break

    mongo.close()

    return ret


def get_financials_reported_attrs_ts(tickers, attrs, start_date, end_date):
    """
    Get from the db a timeseries of financials reported attributes for the specified time period.

    Returns:
    dict[str, DataFrame]: a dataframe for each asset, where each column is an attribute, row key is date.
    """
    start_date = util.normalize_datetime(start_date)
    end_date = util.normalize_datetime(end_date)

    ret = {ticker: pd.DataFrame(columns=attrs) for ticker in tickers}

    date = start_date
    while date <= end_date:
        day_samples = get_financials_reported_attrs(tickers, attrs, date)
        date += timedelta(days=1)
        for ticker, row in day_samples.items():
            ret[ticker].loc[date] = row

    return ret


def get_financials_reported_attrs_ts_2(tickers, attrs, start_date, end_date):
    """
    Get from the db a timeseries of financials reported attributes for the specified time period.

    Returns:
    dict[str, DataFrame]: a dataframe for each asset, where each column is an attribute, row key is date.
    """
    start_date = util.normalize_datetime(start_date)
    end_date = util.normalize_datetime(end_date)

    mongo = mongo_client()
    db = mongo.stock_analysis

    ret = {}

    for ticker in tickers:
        reports = list(db.financials_reported.find(
            {
                'symbol': {'$eq': ticker},
                'endDate': {'$gte': start_date, '$lte': end_date},
            }
        ).sort("startDate", pymongo.ASCENDING)
         .sort("endDate", pymongo.DESCENDING))  # Prefer shorter-term reports

        df = pd.DataFrame(columns=attrs, index=pd.date_range(start=start_date, end=end_date))

        # For each report, copy data to DataFrame
        for report in reports:
            attrs_dict = _financials_reported_attrs(report, attrs)
            for attr, value in attrs_dict.items():
                max_start_date = max(util.normalize_datetime(report['startDate']), start_date)
                df.loc[pd.date_range(start=max_start_date, end=util.normalize_datetime(report['endDate'])), attr] = value

        ret[ticker] = df

    return ret


def _financials_reported_attrs(report, attrs=None):
    ret = {}

    if not attrs:
        for category in ['bs', 'cf', 'ic']:
            cat_attrs = report['report'][category]
            # The schema on finnhub.io shows this format
            if isinstance(cat_attrs, dict):
                ret |= cat_attrs
            # But all (most?) samples I've found are in this format
            elif isinstance(cat_attrs, list):
                for attr_entry in cat_attrs:
                    ret[attr_entry['concept']] = attr_entry['value']
    else:
        for attr in attrs:
            for category in ['bs', 'cf', 'ic']:
                cat_attrs = report['report'][category]
                # The schema on finnhub.io shows this format
                if isinstance(cat_attrs, dict):
                    if attr in cat_attrs.keys():
                        ret[attr] = cat_attrs[attr]
                        break
                # But all (most?) samples I've found are in this format
                elif isinstance(cat_attrs, list):
                    for attr_entry in cat_attrs:
                        if attr_entry['concept'] == attr:
                            ret[attr] = attr_entry['value']
                            break

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
            'ticker': {'$eq': ticker},
            'predict_from_date': {'$gte': start_date, '$lte': end_date}
        }
        if model_id is not None:
            query['model_id'] = {'$eq': model_id}
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
        'predict_from_date': {'$gte': start_date, '$lte': end_date}
    }
    if model_id is not None:
        query['model_id'] = {'$eq': model_id}
    cur = db.model_predictions.find(query).sort("predict_from_date", pymongo.ASCENDING)
    predictions = []
    for sample in cur:
        del sample["_id"]
        predictions.append(Prediction(**sample))

    mongo.close()

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
        'predict_from_date': {'$gte': start_date, '$lte': end_date}
    }
    ids = db.model_predictions.find(query).distinct('model_id')

    mongo.close()

    return ids


def save_model_envs(envs):
    mongo = mongo_client()
    db = mongo.stock_analysis
    db.model_envs.insert_many(map(lambda e: e.to_dict(), envs))
    mongo.close()


def get_all_model_envs():
    mongo = mongo_client()
    db = mongo.stock_analysis
    cur = db.model_envs.find({})
    ret = [ModelEnv.from_object(obj) for obj in cur]
    mongo.close()
    return ret


def save_models(models: list[Model]):
    mongo = mongo_client()
    db = mongo.stock_analysis
    db.models.insert_many(map(lambda m: m.serialize(), models))
    mongo.close()


def get_model(model_id: str):
    mongo = mongo_client()
    db = mongo.stock_analysis
    obj = db.models.find_one({'id': {'$eq': model_id}})
    if obj is not None:
        ret = Model.deserialize(obj)
        mongo.close()
        return ret
    else:
        return None


def get_all_models() -> list[Model]:
    mongo = mongo_client()
    db = mongo.stock_analysis
    cur = db.models.find({})
    ret = [Model.deserialize(obj) for obj in cur]
    mongo.close()
    return ret


def update_model(model: Model) -> pymongo.results.UpdateResult:
    mongo = mongo_client()
    db = mongo.stock_analysis
    result = db.models.update_one({'id': {'$eq': model.model_id}}, model.serialize())
    mongo.close()
    return result
