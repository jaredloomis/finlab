class DataSpec:
    id: str
    args: dict[str, Any]
    # Select a specific value of output
    # ex. {'id': 'candles_1d', select: 'open'}
    select: Optional[str]

    def __init__(self, id, args, select=None):
        self.id = id
        self.args = args
        self.select = select
