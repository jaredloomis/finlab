from typing import Any, Optional, Callable, Union
import numpy as np
import pandas as pd
from datetime import datetime

import ta

import datastore as ds
from signals import Signal, SignalSet


class SignalSpec:
    signal_id: str
    args: dict[str, Any]
    base: Optional[Any]  # Optional[SignalSpec]
    # Select a specific value of output
    # ex. {'id': 'candles_1d', select: 'open'}
    select: Optional[str]

    def __init__(self, signal_id, args, base=None, select=None):
        self.signal_id = signal_id
        self.args = args
        self.base = base
        self.select = select


class FetchOptions:
    symbols: set[str]
    daterange: (datetime, datetime)


def fetch_signals(time: pd.Series, specs: list[SignalSpec], labels: list[str],
                  options: FetchOptions) -> SignalSet:
    signals = [fetch_signal(spec) for spec in specs]
    return SignalSet(time, signals, labels)


"""
def fetch_signal(spec: SignalSpec, options: FetchOptions) -> Signal:
    signal_builder = SIGNALS[spec.signal_id]
    # 1. Collect the root signal.
    root = spec
    while root.base is not None:
        root = root.base
    # 2. Fetch root signal.
    root_data = FETCHERS[root](options)
    # 3. Provide the atomics to the computed signals to create Signal.
    spec. # TODO
    pass
"""


def rsi(args: dict[str, Any], base: Optional[SignalSpec]) -> Callable[[np.array], Signal]:
    return lambda data: \
        Signal(
            f'rsi_{base}_{args["window"]}',
            ta.momentum.RSIIndicator(data, window=args["window"]).rsi()
        )


example = SignalSpec('rsi', {'window': 14}, SignalSpec('get', {'attr': 'open'}, 'candles_1d'))

FETCHERS = {
    'candles_1d': \
        lambda options: ds.get_daily_candlesticks(options.symbols, options.daterange[0], options.daterange[1]),
}

SIGNALS = {
    'rsi': {
        'params': [{'window': int}],
        'create': rsi
    },
    'get': {
        'params': [{'attr': Union[str, int]}],
        'create': lambda args: lambda data: data[args['attr']]
    },
}
