import sys
import os
sys.path.insert(0, os.path.dirname(os.path.realpath(__file__)) + '../../src')
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

# Get important dates
today = date.today()
last_week = today - timedelta(weeks=1)
tomorrow = today + timedelta(days=1)
years_ago = today - timedelta(weeks=52*3)

# Get candles for every SPY
spy = pd.read_csv('../../../data/spy_constituents.csv')['Symbol']
ds.download_daily_candlesticks(spy, last_week, tomorrow)
candles = ds.get_daily_candlesticks(spy, years_ago, tomorrow)

# Load model
n_features = 58
n_outputs = 1
predict_window = 14
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
model = PredictiveModel.load('TorchMATI|14|2022-08-25 11:13:01.837960.pt', net)

# Make predictions
def df_to_signal_set(df):
    try:
        return TechnicalSignalSet(df, predict_window=predict_window)
    except:
        return None
model = PredictiveModel(net, "TorchMATI", predict_window, datetime.now())
predictions = predict_price_change(model, df_to_signal_set, spy)


# Save predictions
ds.save_predictions([p for t, p in predictions.items()])
