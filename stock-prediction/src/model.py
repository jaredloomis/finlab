from typing import Union, Any, Optional
import torch
import torch.nn as nn
import sklearn
import pickle
import imp


class SignalExpr:
    signal_id: str
    args: dict[str, Any]
    # Select a specific value of output
    # ex. {'id': 'candles_1d', select: 'open'}
    select: Optional[str]

    def __init__(self, signal_id, args, select=None):
        self.signal_id = signal_id
        self.args = args
        self.select = select

    def qualified_id(self):
        ret = self.signal_id
        for name, val in self.args.items():
            if isinstance(val, SignalExpr):
                ret += '_' + val.qualified_id()
            else:
                ret += '_' + name + str(val)
        return ret


class Backend:
    def train(self, *args, **kwargs):
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

    def train(self, *args, **kwargs):
        self.model.train()
        return self.model(*args, **kwargs)

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

    def __call__(self, *args, **kwargs):
        self.model.eval()
        return self.model(*args, **kwargs)


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

    def __call__(self, *args, **kwargs):
        return self.model.predict(*args, **kwargs)


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

    def train(self, *args, **kwargs):
        return self.backend.train(*args, **kwargs)

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
