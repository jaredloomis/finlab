"""
A `ModelEnv` is:

- A prediction model (pytorch / sklearn)
  - For PyTorch:
    - Model weights
    - Model code
  - For sklearn
    - Pickled object
- An indication of the type of raw data needed, how to process that data into Signals
- How to scale features
- Model output format (number of outputs)
- How to scale labels

TODO
- output format (number of labels)
"""

import imp
import importlib
import pickle

from datetime import datetime

from predictive_model import PredictiveModel
#from signal_library import signal_specs_to_signal_set


class ModelEnv:
    def __init__(self, obj):
        #obj['signals'] = signal_specs_to_signal_set(obj['signals'])
        self.obj = obj

    def model(self):
        if self._model is None:
            # Create the underlying model
            print(self.obj['model']['model_code'])
            module = imp.new_module('mymodule')
            exec(self.obj['model']['model_code'], module.__dict__)
            # Create the wrapper PredictiveModel
            self._model = PredictiveModel.from_model_id(module.model, self.obj['model_id'])
        
        return self._model

    def to_dict(self):
        return self.obj

    @staticmethod
    def from_model(model, display_name, signals, X_scaler, y_scaler, model_code=None):
        return ModelEnv({
            'model_id': model.id,
            'display_name': display_name,
            'model': model.serialize(model_code),
            'signals': signals,
            'X_scaler': pickle.dumps(X_scaler),
            'y_scaler': pickle.dumps(y_scaler),
            'created': datetime.now(),
        })

    @staticmethod
    def from_object(obj):
        return ModelEnv(obj)
