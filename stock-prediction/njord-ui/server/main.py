import sys
if 'src' not in sys.path:
    sys.path.insert(0, '../../src')
import json

from flask import Flask, request, send_file

import datastore as ds
import util

app = Flask(__name__, static_folder='../frontend/build/static')

@app.route('/api/predictions', methods=['GET'])
def predictions():
    """
    :returns dict if tickers is not None, else list
    """
    tickers = request.args.get('tickers')
    if tickers is not None:
        tickers = tickers.split(',')
    start_date = request.args['startDate']
    end_date = request.args['endDate']

    if tickers is not None:
        preds = ds.get_predictions(tickers, start_date, end_date)
        return {k: [p.asdict() for p in ps] for k, ps in preds.items()}
    else:
        preds = ds.get_all_predictions(start_date, end_date)
        return [p.asdict() for p in preds]

@app.route('/api/daily_candlesticks', methods=['GET'])
def daily_candlesticks():
    tickers = request.args['tickers'].split(',')
    start_date = request.args['startDate']
    end_date = request.args['endDate']
    candles = ds.get_daily_candlesticks(tickers, start_date, end_date)
    candles_dicts = {}
    for k, c in candles.items():
        # XXX TMP STRIPPING OFF TIME INFO FROM DATETIME, converting to string (?)
        if 'date' in c:
            c['date'] = c['date'].dt.strftime('%Y-%m-%d')
        columns = list(c)
        try:
            columns.remove("_id")
        except:
            pass
        candles_dicts[k] = c[columns].to_dict(orient='list')
    return candles_dicts

# / -> index.html
@app.route('/')
def root():
    return send_file('../frontend/build/index.html')

# Serve other files directly
# XXX INSANE SECURITY RISK OBVIOUSLY DON'T DEPLOY THIS
@app.route('/<path:path>')
def any(path):
    return send_file(f'../frontend/build/{path}')

if __name__ == '__main__':
    app.run()
