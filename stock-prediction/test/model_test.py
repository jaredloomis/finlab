import sys
import os
sys.path.insert(0, os.path.dirname(os.path.realpath(__file__)) + '/../src')

import torch.nn as nn
from sklearn.preprocessing import StandardScaler
from model import Model, TorchBackend, SklearnBackend


def test_model_serialize_deserialize_torch():
    net = nn.Linear(3, 12)
    X_scaler = StandardScaler()
    y_scaler = StandardScaler()
    model = Model('test', 'Test', TorchBackend(net, 'import torch.nn as nn\nmodel = nn.Linear(3, 12)'), [], X_scaler, y_scaler)
    model_cpy = Model.deserialize(model.serialize())
    assert model == model_cpy

"""https://stackoverflow.com/questions/74081229/pickling-then-unpickling-a-scikit-learn-model-doesnt-yield-the-original-model
def test_model_serialize_deserialize_sklearn():
    backend = RandomForestRegressor()
    X_scaler = StandardScaler()
    y_scaler = StandardScaler()
    model = Model('test', 'Test', SklearnBackend(backend), [], X_scaler, y_scaler)
    model_cpy = Model.deserialize(model.serialize())
    print(model)
    print(model_cpy)
    assert model == model_cpy
"""
