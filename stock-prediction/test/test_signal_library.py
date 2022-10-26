import sys
import os
sys.path.insert(0, os.path.dirname(os.path.realpath(__file__)) + '/../src')

import numpy as np
import pandas as pd
import random
from datetime import datetime
import signal_library as sl
from model import SignalExpr


def test_fetch_signal_set():
    # RSI
    expr = SignalExpr('rsi', {'window': 14, 'base': SignalExpr('candles_5min', {}, select='close')})
    sigs = sl.fetch_signal_set([expr], [], sl.FetchOptions(['AAPL'], (pd.to_datetime("2022-06-01"), datetime.now())))
    assert len(sigs.keys()) == 1
    assert len(list(sigs.values())[0].signals.index) > 100
    assert isinstance(list(sigs.values())[0].signals['rsi<window=14>(base=candles_5min[close])'][0], np.float64)

    # KAMA
    expr = SignalExpr('kama', {'window': 14, 'base': SignalExpr('candles_5min', {}, select='close')})
    sigs = sl.fetch_signal_set([expr], [], sl.FetchOptions(['AAPL'], (pd.to_datetime("2022-06-01"), datetime.now())))
    assert len(sigs.keys()) == 1
    assert len(list(sigs.values())[0].signals.index) > 100
    assert isinstance(list(sigs.values())[0].signals['kama<window=14>(base=candles_5min[close])'][0], np.float64)

    # Percent Price Oscillator
    expr = SignalExpr('percent_price_oscillator', {'window': 14, 'base': SignalExpr('candles_5min', {}, select='close')})
    sigs = sl.fetch_signal_set([expr], [], sl.FetchOptions(['AAPL'], (pd.to_datetime("2022-06-01"), datetime.now())))
    assert len(sigs.keys()) == 1
    assert len(list(sigs.values())[0].signals.index) > 100
    assert isinstance(list(sigs.values())[0].signals['percent_price_oscillator(base=candles_5min[close])'][0], np.float64)

    # Average True Range
    expr = SignalExpr('avg_true_range', {'window': 14, 'base': SignalExpr('candles_5min', {})})
    sigs = sl.fetch_signal_set([expr], [], sl.FetchOptions(['AAPL'], (pd.to_datetime("2022-06-01"), datetime.now())))
    assert len(sigs.keys()) == 1
    assert len(list(sigs.values())[0].signals.index) > 100
    assert isinstance(list(sigs.values())[0].signals['avg_true_range<window=14>(base=candles_5min)'][0], np.float64)

    # EMA
    expr = SignalExpr('ema', {'window': 14, 'base': SignalExpr('candles_5min', {}, select='close')})
    sigs = sl.fetch_signal_set([expr], [], sl.FetchOptions(['AAPL'], (pd.to_datetime("2022-06-01"), datetime.now())))
    assert len(sigs.keys()) == 1
    assert len(list(sigs.values())[0].signals.index) > 100
    assert isinstance(list(sigs.values())[0].signals['ema<window=14>(base=candles_5min[close])'][0], np.float64)

    # Ulcer Index
    expr = SignalExpr('ulcer_index', {'window': 14, 'base': SignalExpr('candles_5min', {}, select='close')})
    sigs = sl.fetch_signal_set([expr], [], sl.FetchOptions(['AAPL'], (pd.to_datetime("2022-06-01"), datetime.now())))
    assert len(sigs.keys()) == 1
    assert len(list(sigs.values())[0].signals.index) > 100
    assert isinstance(list(sigs.values())[0].signals['ulcer_index<window=14>(base=candles_5min[close])'][0], np.float64)

    # MACD
    expr = SignalExpr('macd', {'window': 14, 'base': SignalExpr('candles_5min', {}, select='close')})
    sigs = sl.fetch_signal_set([expr], [], sl.FetchOptions(['AAPL'], (pd.to_datetime("2022-06-01"), datetime.now())))
    assert len(sigs.keys()) == 1
    assert len(list(sigs.values())[0].signals.index) > 100
    assert isinstance(list(sigs.values())[0].signals['macd<window=14>(base=candles_5min[close])'][0], np.float64)

    # % Change
    expr = SignalExpr('percent_change', {'window': 14, 'base': SignalExpr('candles_5min', {}, select='close')})
    sigs = sl.fetch_signal_set([expr], [], sl.FetchOptions(['AAPL'], (pd.to_datetime("2022-06-01"), datetime.now())))
    assert len(sigs.keys()) == 1
    assert len(list(sigs.values())[0].signals.index) > 100
    assert isinstance(list(sigs.values())[0].signals['percent_change<window=14>(base=candles_5min[close])'][0], np.float64)


def test__percent_change_neg_window_pos_trend():
    for _ in range(20):
        x = []
        size = 20
        window = random.randrange(start=-size // 2, stop=-1)
        multiplier = random.random() * 20.
        expected_pchange = (np.power(multiplier, abs(window)) - 1.) * 100.

        for i in range(20):
            x.append(np.power(multiplier, i))
        x = pd.Series(x, dtype=np.float64)

        changes = sl._percent_change(x, window=window)

        assert np.all(np.isnan(changes[:abs(window)]))
        assert (abs(changes[window:] - expected_pchange) < 1).all()


def test__percent_change_pos_window_pos_trend():
    for _ in range(20):
        x = []
        size = 20
        window = random.randrange(start=1, stop=size // 2)
        multiplier = random.random() * 20.
        expected_pchange = (np.power(multiplier, window) - 1.) * 100.

        for i in range(20):
            x.append(np.power(multiplier, i))
        x = pd.Series(x, dtype=np.float64)

        changes = sl._percent_change(x, window=window)

        assert np.all(np.isnan(changes[-window:]))
        assert (abs(changes[:-window] - expected_pchange) < 1).all()
