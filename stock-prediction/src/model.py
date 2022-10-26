from typing import Union, Any, Optional
import torch
import torch.nn as nn
import sklearn
import pickle
import imp

from signal_library import FetchOptions, fetch_signal_set
from signal_expr import SignalExpr
import util

class TrainOptions:
    epochs: int

    def __init__(self, epochs):
        self.epochs = epochs


class Backend:
    def train(self, features: list[SignalExpr], labels: list[SignalExpr]):
        raise 'Backend.train: not yet implemented!'

    def serialize(self) -> Union[dict[str, Any], bytes]:
        raise 'Backend.serialize: not yet implemented!'

    # XXX: Must be updated when additional model backend are implemented
    @staticmethod
    def deserialize(model: Union[dict[str, Any], bytes]) -> Any:
        if model['model_type'] == 'pytorch':
            return TorchBackend.deserialize(model)
        elif model['model_type'] == 'sklearn':
            return SklearnBackend.deserialize(model)
        else:
            raise f'Couldn\'t deserialize RawModel {model}'

    def __call__(self, *args, **kwargs):
        raise 'Backend.__call__: not yet implemented!'


class TorchBackend(Backend):
    model: nn.Module
    model_code: str

    def __init__(self, model: nn.Module, model_code: str):
        self.model = model
        self.model_code = model_code
        # TODO configurable? Save?
        self.optimizer = torch.optim.Adam(self.model.parameters(), lr=0.001)
        self.criterion = torch.nn.MSELoss()

    def train(self, X, y, device='cpu'):
        # Convert to torch
        X = torch.from_numpy(X).float().to(device)
        y = torch.from_numpy(y).float().to(device)

        # Train
        self.model.train()
        y_pred = self.model(X)

        # Compute loss, zero gradients, backward pass, update weights
        self.optimizer.zero_grad()
        loss = self.criterion(y_pred, y)
        loss.backward()
        self.optimizer.step()

        return loss.item()

    def serialize(self):
        return {
            'model_type': 'pytorch',
            'state_dict': {k: v.tolist() for k, v in self.model.state_dict().items()},
            # Serialize the Tensors (numpy or lists)
            'model_code': self.model_code,
        }

    @staticmethod
    def deserialize(model):
        # Create the underlying model
        module = imp.new_module('mymodule')
        exec(model['model_code'], module.__dict__)
        # Load state dict
        state_dict = {k: torch.Tensor(v) for k, v in model['state_dict'].items()}
        module.model.load_state_dict(state_dict)
        return TorchBackend(module.model, model['model_code'])

    def __eq__(self, other):
        return self.model_code == other.model_code and \
               all([torch.equal(self.model.state_dict()[key], other.model.state_dict()[key]) for key in
                    self.model.state_dict().keys()])

    def __call__(self, X):
        self.model.eval()
        return self.model(X)


class SklearnBackend(Backend):
    model: sklearn.base.BaseEstimator

    def __init__(self, model: sklearn.base.BaseEstimator):
        self.model = model

    def train(self, *args, **kwargs):
        return self.model.fit(*args, **kwargs)

    def serialize(self):
        return {
            'model_type': 'sklearn',
            'model': pickle.dumps(self.model),
        }

    @staticmethod
    def deserialize(model):
        return SklearnBackend(pickle.loads(model['model']))

    def __eq__(self, other):
        return self.model == other.model

    def __call__(self, X):
        return self.model.predict(X)


SerializedRawModel = Union[dict[str, Any], bytes]


def serialize_scaler(scaler: sklearn.base.BaseEstimator) -> bytes:
    return pickle.dumps(scaler)


def deserialize_scaler(scaler: bytes) -> sklearn.base.BaseEstimator:
    return pickle.loads(scaler)


class Model:
    model_id: str
    display_name: str
    backend: Backend
    features: list[SignalExpr]
    labels: list[SignalExpr]
    X_scaler: sklearn.base.BaseEstimator
    y_scaler: sklearn.base.BaseEstimator

    def __init__(self,
                 model_id: str, display_name: str, backend: Backend,
                 features: list[SignalExpr], labels: list[SignalExpr],
                 X_scaler: sklearn.base.BaseEstimator, y_scaler: sklearn.base.BaseEstimator):
        self.model_id = model_id
        self.display_name = display_name
        self.backend = backend
        self.features = features
        self.labels = labels
        self.X_scaler = X_scaler
        self.y_scaler = y_scaler

    def serialize(self) -> dict[str, Any]:
        return {
            'id': self.model_id,
            'display_name': self.display_name,
            'model': self.backend.serialize(),
            'features': self.features,
            'labels': self.labels,
            'X_scaler': serialize_scaler(self.X_scaler),
            'y_scaler': serialize_scaler(self.y_scaler),
        }

    @staticmethod
    def deserialize(obj: dict[str, Any]) -> Any:
        return Model(
            obj['id'], obj['display_name'], Backend.deserialize(obj['model']),
            obj['features'], obj['labels'], deserialize_scaler(obj['X_scaler']), deserialize_scaler(obj['y_scaler'])
        )

    def train(self, fetch_options: FetchOptions, train_options: TrainOptions):
        # Fetch data
        signals_by_ticker = fetch_signal_set(self.features, self.labels, fetch_options)
        #signals = SignalSet.concat(list(signals_by_ticker.values()))
        #print(signals_by_ticker)

        # TODO shuffle data

        # Set up scalers
        for ticker, signals in signals_by_ticker.items():
            X, y, _ = signals.to_xy(no_scaling=True)
            if X.shape[0] > 0:
                self.X_scaler.partial_fit(X)
            if y.shape[0] > 0:
                self.y_scaler.partial_fit(y)

        # TODO batching
        for ticker, signals in signals_by_ticker.items():
            try:
                for epoch in range(train_options.epochs):
                    X, y, Xy_date = signals.to_xy(self.X_scaler, self.y_scaler)
                    loss = self.backend.train(X, y)
                    print(loss)
            except Exception as ex:
                print(f'error training on data for ticker {ticker}')
                util.print_exception(ex)

    def eval(self, fetch_options: FetchOptions):
        signals_by_ticker = fetch_signal_set(self.features, self.labels, fetch_options)

        ret = {}
        for sym, sigs in signals_by_ticker.items():
            try:
                ret[sym] = self.y_scaler.inverse_transform(
                    self.backend(sigs.to_xy(self.X_scaler, self.y_scaler)[0][-1].reshape(1, -1)).reshape(-1, 1)
                )[:, 0][-1]
            except Exception as ex:
                util.print_exception(ex)

        return ret

    def __call__(self, *args, **kwargs):
        return self.backend(*args, **kwargs)

    def __repr__(self):
        return str(self)

    def __str__(self):
        return str(self.serialize())

    def __eq__(self, other) -> bool:
        return self.model_id == other.model_id and self.display_name == other.display_name and \
               self.backend == other.backend and self.features == other.features and \
               self.labels == other.labels and \
               pickle.dumps(self.X_scaler) == pickle.dumps(other.X_scaler) and \
               pickle.dumps(self.y_scaler) == pickle.dumps(other.y_scaler)
