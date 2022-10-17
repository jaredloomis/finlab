from typing import Any

import ta

import datastore as ds
from signals import Signal, SignalSet
from model import SignalSpec
from util import TimeRange


class FetchOptions:
    symbols: set[str]
    timerange: TimeRange

    def __init__(self, symbols: set[str], timerange: TimeRange):
        self.symbols = symbols
        self.timerange = timerange


def fetch_signal_set(feature_specs: list[SignalSpec], label_specs: list[SignalSpec],
                  options: FetchOptions) -> dict[str, SignalSet]:
    feature_signals = [fetch_signal(feature, options) for feature in feature_specs]
    label_signals = [fetch_signal(label, options) for label in label_specs]
    signals = feature_signals + label_signals
    label_names = [spec.qualified_id() for spec in label_specs]
    if len(feature_signals) > 0 and len(feature_signals[0]) > 0:
        first_key = list(feature_signals[0].keys())[0]
        return {
            sym: SignalSet(
                feature_signals[0][first_key].data.index, list(map(lambda s: s[sym], signals)), label_names
            ) for sym in options.symbols
        }
    else:
        raise Exception('fetch_signal_set: no feature signals returned')


def fetch_signal(spec: SignalSpec, options: FetchOptions) -> dict[str, Signal]:
    if spec.signal_id in FETCHERS:
        datas = FETCHERS[spec.signal_id](options)
        sigs = {sym: Signal(spec.qualified_id(), data) for sym, data in datas.items()}
        if spec.select is not None:
            print(spec.select)
            return {sym: sig[spec.select] for sym, sig in sigs.items()}
        else:
            return sigs
    elif spec.signal_id in COMPUTED:
        create = COMPUTED[spec.signal_id]['create']
        rsi_sigs = fetch_signal(spec.base, options)
        rsi_sig = {sym: create(spec.args, sig) for sym, sig in rsi_sigs.items()}
        if spec.select is not None:
            return {sym: sig[spec.select] for sym, sig in rsi_sigs.items()}
        else:
            return rsi_sig


example = SignalSpec('rsi', {'window': 14}, SignalSpec('candles_5min', {}, select='open'))

#example2 = SignalSpec('rsi', {'window': 14}, SignalSpec('candles_5min', {}, select='open'))

FETCHERS = {
    'candles_1day': \
        lambda options: ds.get_daily_candlesticks(options.symbols, options.timerange[0], options.timerange[1]),
    'candles_5min': \
        lambda options: ds.get_candles(options.symbols, options.timerange[0], options.timerange[1]),
}


def rsi(args: dict[str, Any], base: Signal) -> Signal:
    return Signal(
        f'rsi{args["window"]}({base.column_name})',
        ta.momentum.RSIIndicator(base.data, window=args["window"]).rsi()
    )


def kama(args: dict[str, Any], base: Signal) -> Signal:
    return Signal(
        f'kama{args["window"]}({base.column_name})',
        ta.momentum.KAMAIndicator(base.data, window=args["window"]).kama()
    )


def percent_price_osc(args: dict[str, Any], base: Signal) -> Signal:
    return Signal(
        f'percent_price_osc{args["window"]}({base.column_name})',
        ta.momentum.PercentagePriceOscillator(base.data, window=args["window"]).kama()
    )


COMPUTED = {
    'rsi': {
        'params': [{'window': int}],
        'create': rsi,
    },
    'kama': {
        'params': [{'window': int}],
        'create': kama,
    },
    'percent_price_osc': {
        'params': [{'window': int}],
        'create': percent_price_osc,
    },
}
