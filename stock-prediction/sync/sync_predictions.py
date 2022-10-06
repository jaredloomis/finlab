import sys
import os
sys.path.insert(0, os.path.dirname(os.path.realpath(__file__)) + '/../src')

import torch
import torch.nn as nn

from predictive_model import PredictiveModel
from technical_signals import TechnicalSignalSet
from predict import predict_price_change
import datastore as ds

from active_models import active_models

# Config
n_features = 58
n_outputs = 1
predict_window = 14

def df_to_signal_set(df):
    try:
        return TechnicalSignalSet(df, predict_window=predict_window)
    except:
        return None

def sync_predictions(tickers):
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

        # Make predictions
        predictions = predict_price_change(model, df_to_signal_set, tickers)

        # Save predictions
        ds.save_predictions([p for t, p in predictions.items()])
