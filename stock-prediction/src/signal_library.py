import pandas as pd
from typing import Any, Callable
from numba import jit

import ta

import datastore as ds
from signals import Signal, SignalSet
from signal_expr import SignalExpr
from util import TimeRange


class FetchOptions:
    symbols: set[str]
    timerange: TimeRange

    def __init__(self, symbols: set[str], timerange: TimeRange):
        self.symbols = symbols
        self.timerange = timerange


def fetch_signal_set(feature_specs: list[SignalExpr], label_specs: list[SignalExpr],
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
            ) for sym in options.symbols if sym in signals[0]
        }
    else:
        raise Exception(f'fetch_signal_set: no feature signals returned {feature_signals}')


# TODO profile whether the jit helps
@jit(forceobj=True)
def fetch_signal(spec: SignalExpr, options: FetchOptions) -> dict[str, Signal]:
    if spec.signal_id in FETCHERS:
        datas = FETCHERS[spec.signal_id](options)
        sigs = {sym: Signal(spec.qualified_id(), data) for sym, data in datas.items() if not data.empty}
        if spec.select is not None:
            return {sym: sig[spec.select] for sym, sig in sigs.items()}
        else:
            return sigs
    elif spec.signal_id in COMPUTED:
        create = COMPUTED[spec.signal_id]['create']
        # Expand each SignalExpr args into dict[str, Signal]
        expanded_args: dict[str, dict[str, Signal]] = {}
        for name, val in spec.args.items():
            if isinstance(val, SignalExpr):
                expanded_args[name] = fetch_signal(val, options)
            else:
                expanded_args[name] = val
        if spec.select is not None:
            return create(expanded_args)[spec.select]
        else:
            return create(expanded_args)
    else:
        raise Exception(f'Signal id not recognized: {spec.signal_id}')


example = SignalExpr('rsi', {'window': 14, 'base': SignalExpr('candles_5min', {}, select='close')})

example2 = SignalExpr('avg_true_range', {'window': 14, 'base': SignalExpr('candles_5min', {})})

FETCHERS = {
    'candles_1day': \
        lambda options: ds.get_daily_candlesticks(options.symbols, options.timerange[0], options.timerange[1]),
    'candles_5min': \
        lambda options: ds.get_candles(options.symbols, options.timerange[0], options.timerange[1]),
}


def rsi(args: dict[str, Any]) -> dict[str, Signal]:
    return {sym: Signal(
        f'rsi<window={args["window"]}>(base={base_sig.column_name})',
        ta.momentum.RSIIndicator(base_sig.data, window=args["window"]).rsi()
    ) for sym, base_sig in args['base'].items()}


def kama(args: dict[str, Any]) -> dict[str, Signal]:
    return {sym: Signal(
        f'kama<window={args["window"]}>(base={base_sig.column_name})',
        ta.momentum.KAMAIndicator(base_sig.data, window=args["window"]).kama()
    ) for sym, base_sig in args['base'].items()}


def percent_price_oscillator(args: dict[str, Any]) -> dict[str, Signal]:
    return {sym: Signal(
        f'percent_price_oscillator(base={base_sig.column_name})',
        ta.momentum.PercentagePriceOscillator(base_sig.data).ppo()
    ) for sym, base_sig in args['base'].items()}


def avg_true_range(args: dict[str, Any]) -> dict[str, Signal]:
    return {sym: Signal(
        f'avg_true_range<window={args["window"]}>(base={base_sig.column_name})',
        ta.volatility.AverageTrueRange(base_sig.data["high"], base_sig.data["low"], base_sig.data["close"]).average_true_range()
    ) for sym, base_sig in args['base'].items()}


def ema(args: dict[str, Any]) -> dict[str, Signal]:
    return {sym: Signal(
        f'ema<window={args["window"]}>(base={sig.column_name})',
        ta.trend.EMAIndicator(sig.data).ema_indicator()
    ) for sym, sig in args['base'].items()}


def ulcer_index(args: dict[str, Any]) -> dict[str, Signal]:
    return {sym: Signal(
        f'ulcer_index<window={args["window"]}>(base={sig.column_name})',
        ta.volatility.UlcerIndex(sig.data).ulcer_index()
    ) for sym, sig in args['base'].items()}


def macd(args: dict[str, Any]) -> dict[str, Signal]:
    return {sym: Signal(
        f'macd<window={args["window"]}>(base={sig.column_name})',
        ta.trend.MACD(sig.data).macd()
    ) for sym, sig in args['base'].items()}


def percent_change(args: dict[str, Any]) -> dict[str, Signal]:
    """
    NEGATIVE WINDOW INDICATES BACKWARDS IN HISTORY!!
    """
    return {sym: Signal(
        f'percent_change<window={args["window"]}>(base={sig.column_name})',
        _percent_change(sig.data)
    ) for sym, sig in args['base'].items()}


def _percent_change(data, window=1):
    """
    NEGATIVE WINDOW INDICATES BACKWARDS IN HISTORY!!
    """
    if window < 0:
        return -(
            data.diff(periods=window) / data * 100
        ).shift(-window)
    else:
        return -(
            (data - data.shift(-window))
            / data
            * 100
        )


COMPUTED = {
    'rsi': {
        'params': {'base': Signal, 'window': int},
        'create': rsi,
    },
    'kama': {
        'params': {'base': Signal, 'window': int},
        'create': kama,
    },
    'percent_price_oscillator': {
        'params': {'base': Signal},
        'create': percent_price_oscillator,
    },
    'avg_true_range': {
        'params': {'base': Signal, 'window': int},
        'create': avg_true_range,
    },
    'ema': {
        'params': {'base': Signal, 'window': int},
        'create': ema,
    },
    'percent_change': {
        'params': {'base': Signal, 'window': int},
        'create': percent_change,
    },
    'ulcer_index': {
        'params': {'base': Signal, 'window': int},
        'create': ulcer_index,
    },
    'macd': {
        'params': {'base': Signal, 'window': int},
        'create': macd,
    },
}
