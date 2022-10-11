from typing import Any, Optional


class SignalSpec:
    id: str
    args: dict[str, Any]
    # Select a specific value of output
    # ex. {'id': 'candles_1d', select: 'open'}
    select: Optional[str]


class ModelConf:
    model_type: str
    features: list[SignalSpec]
    labels: list[SignalSpec]
    model_code: str
    hyperparameters: dict[str, Any]

    def __init__(self, model_type, features, labels, model_code, hyperparameters={}):
        self.model_type = model_type
        self.features = features
        self.labels = labels
        self.model_code = model_code
        self.hyperparameters = hyperparameters
