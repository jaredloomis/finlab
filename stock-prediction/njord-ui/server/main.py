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
    tickers = request.args['tickers'].split(',')
    start_date = request.args['startDate']
    end_date = request.args['endDate']
    preds = ds.get_predictions(tickers, start_date, end_date)
    preds_dicts = {k: [p.asdict() for p in ps] for k, ps in preds.items()}
    return preds_dicts

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
