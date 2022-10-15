from typing import Union, Any
import torch
import sklearn
import pickle
import imp

from signal_library import SignalSpec


class RawModel:
    def serialize(self) -> Union[dict[str, Any], bytes]:
        raise 'RawModel.serialize: not yet implemented!'

    # XXX: Must be updated when additional model backend are implemented
    @staticmethod
    def deserialize(model: Union[dict[str, Any], bytes]) -> Any:
        if 'model' in model and isinstance(model['model'], torch.nn.Module):
            return TorchModel.deserialize(model)
        elif 'model' in model and isinstance(model['model'], sklearn.base.BaseEstimator):
            return SklearnModel.deserialize(model)


class TorchModel(RawModel):
    model: torch.nn.Module
    model_code: str

    def __init__(self, model: torch.nn.Module, model_code: str):
        self.model = model
        self.model_code = model_code

    def serialize(self):
        return {
            'state_dict': {k: v.tolist() for k, v in self.model.state_dict().items()},
            # Serialize the Tensors (numpy or lists)
            'model_code': self.model_code,
        }

    @staticmethod
    def deserialize(model):
        # Create the underlying model
        print(model['model_code'])
        module = imp.new_module('mymodule')
        exec(model['model_code'], module.__dict__)
        # Create the wrapper PredictiveModel
        return TorchModel(module.model, model['model_code'])


class SklearnModel(RawModel):
    model: sklearn.base.BaseEstimator

    def __init__(self, model: sklearn.base.BaseEstimator):
        self.model = model

    def serialize(self):
        return {
            'model': pickle.dumps(self.model)
        }

    @staticmethod
    def deserialize(model):
        return pickle.loads(model['model'])


SerializedRawModel = Union[dict[str, Any], bytes]


def serialize_scaler(scaler: sklearn.base.BaseEstimator) -> bytes:
    return pickle.dumps(scaler)


def deserialize_scaler(scaler: bytes) -> sklearn.base.BaseEstimator:
    return pickle.loads(scaler)


class Model:
    model_id: str
    display_name: str
    model: RawModel
    signals: list[SignalSpec]
    X_scaler: sklearn.base.BaseEstimator
    y_scaler: sklearn.base.BaseEstimator

    def __init__(self,
                 model_id: str, display_name: str, model: RawModel, signals: list[SignalSpec],
                 X_scaler: sklearn.base.BaseEstimator, y_scaler: sklearn.base.BaseEstimator):
        self.model_id = model_id
        self.display_name = display_name
        self.model = model
        self.signals = signals
        self.X_scaler = X_scaler
        self.y_scaler = y_scaler

    def serialize(self) -> dict[str, Any]:
        return {
            'id': self.model_id,
            'display_name': self.display_name,
            'model': self.model.serialize(),
            'signals': self.signals,
            'X_scaler': serialize_scaler(self.X_scaler),
            'y_scaler': serialize_scaler(self.y_scaler),
        }

    @staticmethod
    def deserialize(obj: dict[str, Any]) -> Any:
        return Model(
            obj['id'], obj['display_name'], RawModel.deserialize(obj['model']),
            obj['signals'], obj['X_scaler'], obj['y_scaler']
        )
