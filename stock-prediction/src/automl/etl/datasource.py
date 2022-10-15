from dataspec import DataSpec


class SourceType(Enum):
    API = 1
    LOCAL_MONGO = 2
    CSV = 3


class DataSource:
    source_id: str
    source_type: SourceType
    provides: dict[DataSpec, list[daterange]]

    def __init__(self, source_id, source_type, provides):
        self.source_id = source_id
        self.source_type = source_type
        self.provides = provides

    # ABSTRACT
    def get(self, signal_specs: list[SignalSpec], date_range: daterange) -> dict:
        raise 'DataSource.get: Not yet implemented!'

    # ABSTRACT
    def provides(self, signal_spec: SignalSpec, date_range: daterange) -> bool:
        raise 'DataSource.provides: Not yet implemented!'