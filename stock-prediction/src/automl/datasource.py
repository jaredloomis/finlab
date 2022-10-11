from enum import Enum
from model_conf import SignalSpec
from util import daterange

#class DataSourceType:


#class DataSourceType(Enum):
#    API = 0


class DataSource:
    source_id: str
    # ex. postgresql, mongodb, csv
    source_type: str
    # Provided Signals
    provides: dict[SignalSpec, list[daterange]]

    def __init__(self, source_id, source_type, provides):
        self.source_id = source_id
        self.source_type = source_type
        self.provides = provides

    # ABSTRACT
    def get(self, signal_specs: list[SignalSpec], date_range: daterange) -> dict:
        raise 'DataSource.get: Not yet implemented!'

    def download(self, signal_specs: list[SignalSpec], date_range: daterange):
        raise 'DataSource.download: Not yet implemented!'


class MongoDataStore(DataSource):
    source_id = 'mongo_data_store'
    source_type = 'mongo'
    provides = {}
