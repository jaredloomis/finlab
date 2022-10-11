from typing import Callable

import pandas as pd
import ta

from signals import Signal


# XXX NEED TO REMOVE DATA MEMBER FROM `Signal` before implementing this.
# Otherwise API will be cumbersome and not as performant.
# Might as well do a full refactor before this.


def signal_specs_to_signal_set(specs):
    signals = [signal_spec_to_signal(spec) for spec in specs]


def signal_spec_to_signal(spec):
    return SIGNALS[spec['id']]({key: spec[key] for key in spec if key != 'id'})


def rsi(base_signal, window=14):
    return lambda data: \
        Signal(f'rsi_{base_signal}_{window}', ta.momentum.RSIIndicator(data['base_signal'], window=window).rsi())


class SignalSpec:
    create: Callable[[pd.DataFrame], Signal]
    requires: list[str]

SIGNALS = {
    'rsi': {
        'requires': ['candlestick'],
        'create': lambda spec: rsi(base_signal=spec['base_signal'], **spec)
    }
}
