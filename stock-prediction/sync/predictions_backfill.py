import sys
import os
sys.path.insert(0, os.path.dirname(os.path.realpath(__file__)) + '/../src')
import numpy as np
import pandas as pd
from datetime import date, timedelta, datetime

import torch
import torch.nn as nn

from predictive_model import PredictiveModel
from technical_signals import TechnicalSignalSet
from prediction import Prediction
from predict import predict_price_change
import datastore as ds
import util

from active_models import active_models

n_features = 58
n_outputs = 1
predict_window = 14
LOOKBACK_DAYS = 60
today = date.today()

if len(sys.argv) == 1:
    # tickers = Watchlist + SPY
    watchlist = pd.read_csv('../data/watchlist.csv')['symbol']
    spy = pd.read_csv('../../data/spy_constituents.csv')['Symbol']
    tickers = pd.concat([watchlist, spy])
else:
    tickers = sys.argv[1:]

def df_to_signal_set(df):
    try:
        return TechnicalSignalSet(df, predict_window=predict_window)
    except Exception as ex:
        util.print_exception(ex)
        return None


for model_path in active_models:
    # Load model
    net = nn.Sequential(
        nn.Linear(n_features, 256),
        nn.ReLU(),
        nn.Linear(256, 128),
        nn.ReLU(),
        nn.Linear(128, 32),
        nn.ReLU(),
        nn.Linear(32, 16),
        nn.ReLU(),
        nn.Linear(16, 8),
        nn.ReLU(),
        nn.Linear(8, n_outputs),
    )
    net = net.to(torch.device('cpu'))
    model = PredictiveModel.load(model_path, net)

    for offset in range(0, LOOKBACK_DAYS):
        # Make predictions (in reverse order)
        predict_from_date = today - timedelta(days=offset)
        predictions = predict_price_change(model, df_to_signal_set, tickers, predict_from_date=predict_from_date)
        # Save predictions
        ds.save_predictions([p for t, p in predictions.items()])
