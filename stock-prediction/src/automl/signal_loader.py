from typing import Optional

from model_conf import ModelConf


class SignalLoader:
    def __init__(self):
        """
        """
        pass

    @staticmethod
    def for_model(model_conf: ModelConf, **kwargs):
        # Collect a list of DataStores needed
        # Collect a list of computed Signals needed
        return SignalLoader(**kwargs)
