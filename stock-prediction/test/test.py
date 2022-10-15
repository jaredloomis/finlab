import pandas as pd
import pytest


def test_automl_train():
    from signals import Signal
    from automl import AutoTrainer, ModelConf, DatasetSpec, DataLoader, SignalLoader, create_dataloaders

    # User configuration needed to create a new model
    conf = ModelConf(
        type='pytorch',
        features=[{'id': 'rsi', 'window': 14, 'base_signal': {'id': 'candles', 'interval': '1d', 'select': 'close'}}],
        labels=[{'id': 'pchange', 'window': 14, 'base_signal': {'id': 'candles', 'interval': '1d', 'select': 'close'}}],
        model_code='''
        import torch
        import torch.nn as nn
        
        model = nn.Sequential(
            nn.Linear(128, 64),
            nn.Linear(64, 1)
        )
        '''
    )

    # Data Loading, Feature Extraction
    signal_loader = SignalLoader.for_model(conf, chunk_size=16, approximate_chunk_size=True)

    # Training
    trainer = AutoTrainer(conf.create_model(), signal_loader)
    trained_model = trainer.train(epochs=100, metrics=True)
    metrics = trainer.get_metrics()
    assert metrics[]
    pass
