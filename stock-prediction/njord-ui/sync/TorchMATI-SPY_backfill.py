import sys
import os
sys.path.insert(0, os.path.dirname(os.path.realpath(__file__)) + '/../../src')
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

LOOKBACK_DAYS = 1000
today = date.today()

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
model = PredictiveModel.load('TorchMATI|14|2022-08-26 09:46:08.495973.pt', net)

# Get candles for every SPY
spy = pd.read_csv('../../../data/spy_constituents.csv')['Symbol']

def df_to_signal_set(df):
    try:
        return TechnicalSignalSet(df, predict_window=predict_window)
    except:
        return None

for offset in range(0, LOOKBACK_DAYS):
    # Make predictions (in reverse order)
    predict_from_date = today - timedelta(days=offset)
    model = PredictiveModel(net, "TorchMATI", predict_window, datetime.now())
    predictions = predict_price_change(model, df_to_signal_set, spy, predict_from_date=predict_from_date)
    # Save predictions
    ds.save_predictions([p for t, p in predictions.items()])
