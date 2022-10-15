from enum import Enum
from model_conf import SignalSpec
from datetime import timedelta
from util import daterange


class SourceType(Enum):
    API = 1
    LOCAL_MONGO = 2
    CSV = 3


class SignalSource:
    source_id: str
    source_type: SourceType
    provides: dict[SignalSpec, list[daterange]]

    def __init__(self, source_id, source_type, provides):
        self.source_id = source_id
        self.source_type = source_type
        self.provides = provides

    # ABSTRACT
    def get(self, signal_specs: list[SignalSpec], date_range: daterange) -> dict:
        raise 'DataSource.get: Not yet implemented!'

    def provides(self, signal_spec: SignalSpec, date_range: daterange) -> bool:
        # TODO
        raise 'DataSource.provides: Not yet implemented!'


class StoreType(Enum):
    LOCAL_MONGO = 1


class SignalStore:
    store_id: str
    store_type: StoreType


class MongoCandlesSignalSource(SignalSource):
    def __init__(self, interval='5min'):
        super().__init__(f'mongo_candles_{interval}', SourceType.LOCAL_MONGO, {
                SignalSpec('candles', {'interval': interval}): []
            })
