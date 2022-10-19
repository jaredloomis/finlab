from typing import Any

import ta

import datastore as ds
from signals import Signal, SignalSet
from model import SignalExpr
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
            ) for sym in options.symbols
        }
    else:
        raise Exception('fetch_signal_set: no feature signals returned')


def fetch_signal(spec: SignalExpr, options: FetchOptions) -> dict[str, Signal]:
    if spec.signal_id in FETCHERS:
        datas = FETCHERS[spec.signal_id](options)
        sigs = {sym: Signal(spec.qualified_id(), data) for sym, data in datas.items()}
        if spec.select is not None:
            return {sym: sig[spec.select] for sym, sig in sigs.items()}
        else:
            return sigs
    elif spec.signal_id in COMPUTED:
        create = COMPUTED[spec.signal_id]['create']
        # Expand each SignalExpr args into dict[str, Signal]
        expanded_args = {}
        for name, val in spec.args.items():
            if isinstance(val, SignalExpr):
                expanded_args[name] = fetch_signal(val, options)
            else:
                expanded_args[name] = val
        if spec.select is not None:
            return create(expanded_args)[spec.select]
        else:
            return create(expanded_args)


example = SignalExpr('rsi', {'window': 14, 'base': SignalExpr('candles_5min', {}, select='close')})

example2 = SignalExpr('percent_price_osc', {'window': 14, 'base': SignalExpr('candles_5min', {}, select='close')})

FETCHERS = {
    'candles_1day': \
        lambda options: ds.get_daily_candlesticks(options.symbols, options.timerange[0], options.timerange[1]),
    'candles_5min': \
        lambda options: ds.get_candles(options.symbols, options.timerange[0], options.timerange[1]),
}


def rsi(args: dict[str, Any]) -> dict[str, Signal]:
    return {sym: Signal(
        f'rsi{args["window"]}({sig.column_name})',
        ta.momentum.RSIIndicator(sig.data, window=args["window"]).rsi()
    ) for sym, sig in args['base'].items()}


def kama(args: dict[str, Any]) -> Signal:
    return Signal(
        f'kama{args["window"]}({args["base"].column_name})',
        ta.momentum.KAMAIndicator(args['base'].data, window=args["window"]).kama()
    )


def percent_price_osc(args: dict[str, Any]) -> dict[str, Signal]:
    return {sym: Signal(
        f'percent_price_osc{args["window"]}({sig.column_name})',
        ta.momentum.PercentagePriceOscillator(sig.data).ppo()
    ) for sym, sig in args['base'].items()}


def avg_true_range(args: dict[str, Any]) -> dict[str, Signal]:
    return {sym: Signal(
        f'avg_true_range{args["window"]}({sig.column_name})',
        ta.volatility.AverageTrueRange(sig.data["high"], sig.data["low"], sig.data["close"]).average_true_range()
    ) for sym, sig in args['base'].items()}


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
        'create': percent_price_osc,
    },
    'avg_true_range': {
        'params': [{'window': int}],
        'create': avg_true_range,
    },
}
