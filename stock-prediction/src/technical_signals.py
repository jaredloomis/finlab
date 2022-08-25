import numpy as np
import pandas as pd
import ta
from sklearn.preprocessing import StandardScaler
from sklearn.preprocessing import OneHotEncoder

from signals import Signal, SignalSet

class TechnicalSignalSet(SignalSet):
    def __init__(self, data, predict_window):
        label_keys = [label_key(predict_window)]
        donchian = ta.volatility.DonchianChannel(data["high"], data["low"], data["close"])
        donchian40 = ta.volatility.DonchianChannel(data["high"], data["low"], data["close"], window=40)
        adx = ta.trend.ADXIndicator(data["high"], data["low"], data["close"], window=14)
        adx30 = ta.trend.ADXIndicator(data["high"], data["low"], data["close"], window=30)
        aroon = ta.trend.AroonIndicator(data["close"])
        kst = ta.trend.KSTIndicator(data["close"])
        macd = ta.trend.MACD(data["close"])
        signals = [
            Signal("close", data["close"]),
            Signal("volume", data["volume"]),
            # TODO vary features based on predict_window
            # Technical indicators
            # Mostly momentum indicators
            # Signal("rsi5", ta.momentum.RSIIndicator(data["close"]) window=5).rsi(),
            # Signal("rsi7", ta.momentum.RSIIndicator(data["close"]) window=7).rsi(),
            Signal("rsi14", ta.momentum.RSIIndicator(data["close"], window=14).rsi()),
            Signal("rsi30", ta.momentum.RSIIndicator(data["close"], window=30).rsi()),
            Signal("rsi60", ta.momentum.RSIIndicator(data["close"], window=60).rsi()),
            Signal("rsi120", ta.momentum.RSIIndicator(data["close"], window=120).rsi()),
            Signal("rsi240", ta.momentum.RSIIndicator(data["close"], window=240).rsi()),
            Signal("kama", ta.momentum.KAMAIndicator(data["close"]).kama()),
            Signal("kama60", ta.momentum.KAMAIndicator(data["close"], window=60).kama()),
            Signal("kama120", ta.momentum.KAMAIndicator(data["close"], window=120).kama()),
            Signal("kama240", ta.momentum.KAMAIndicator(data["close"], window=240).kama()),
            Signal("percent_price_osc", ta.momentum.PercentagePriceOscillator(data["close"]).ppo()),
            Signal("ema14", ta.trend.EMAIndicator(data["close"]).ema_indicator()),
            Signal("ema30", ta.trend.EMAIndicator(data["close"], window=30).ema_indicator()),
            Signal("ema60", ta.trend.EMAIndicator(data["close"], window=60).ema_indicator()),
            Signal("ema120", ta.trend.EMAIndicator(data["close"], window=120).ema_indicator()),
            # Volatility
            Signal("avg_true_range", ta.volatility.AverageTrueRange(data["high"], data["low"], data["close"]).average_true_range()),
            Signal("avg_true_range60", ta.volatility.AverageTrueRange(data["high"], data["low"], data["close"], window=60).average_true_range()),
            Signal("avg_true_range120", ta.volatility.AverageTrueRange(data["high"], data["low"], data["close"], window=120).average_true_range()),
            Signal("bollinger_wband", ta.volatility.BollingerBands(data["close"]).bollinger_wband()),
            Signal("donchian40_mband", donchian40.donchian_channel_hband()),
            Signal("donchian40_pband", donchian40.donchian_channel_pband()),
            Signal("donchian40_wband", donchian40.donchian_channel_wband()),
            Signal("donchian20_mband", donchian.donchian_channel_hband()),
            Signal("donchian20_pband", donchian.donchian_channel_pband()),
            Signal("donchian20_wband", donchian.donchian_channel_wband()),
            Signal("ulcer14", ta.volatility.UlcerIndex(data["close"]).ulcer_index()),
            Signal("ulcer40", ta.volatility.UlcerIndex(data["close"], window=40).ulcer_index()),
            Signal("ulcer80", ta.volatility.UlcerIndex(data["close"], window=80).ulcer_index()),
            Signal("ulcer120", ta.volatility.UlcerIndex(data["close"], window=120).ulcer_index()),
            # Trend indicators
            Signal("adx", adx.adx()),
            Signal("adx_neg", adx.adx_neg()),
            Signal("adx_pos", adx.adx_pos()),
            Signal("adx30", adx.adx()),
            Signal("adx_neg30", adx.adx_neg()),
            Signal("adx_pos30", adx.adx_pos()),
            Signal("aroon_down", aroon.aroon_down()),
            Signal("aroon_indicator", aroon.aroon_indicator()),
            Signal("aroon_up", aroon.aroon_up()),
            Signal("cci", ta.trend.CCIIndicator(data["high"], data["low"], data["close"]).cci()),
            Signal("kst", kst.kst()),
            Signal("kst_diff", kst.kst_diff()),
            Signal("kst_sig", kst.kst_sig()),
            Signal("macd", macd.macd()),
            Signal("macd_diff", macd.macd_diff()),
            Signal("macd_signal", macd.macd_signal()),
            # % change history
            Signal("pchange_-1day", percent_change(data, window=-1)),
            Signal("pchange_-2day", percent_change(data, window=-2)),
            Signal("pchange_-3day", percent_change(data, window=-3)),
            Signal("pchange_-4day", percent_change(data, window=-4)),
            Signal("pchange_-5day", percent_change(data, window=-5)),
            Signal("pchange_-7day", percent_change(data, window=-7)),
            Signal("pchange_-14day", percent_change(data, window=-14)),
            Signal("pchange_-30day", percent_change(data, window=-30)),
            Signal("pchange_-60day", percent_change(data, window=-60)),
            Signal("pchange_-120day", percent_change(data, window=-120)),
            Signal("pchange_-240day", percent_change(data, window=-240)),
            Signal("pchange_-480day", percent_change(data, window=-480)),
            #Signal("pchange_-960day", percent_change(data, window=-960)),
            #Signal("pchange_-1920day", percent_change(data, window=-1920)),
            # Labels
            Signal(label_keys[0], percent_change(data, window=predict_window)),
        ]
        super().__init__(data["date"], signals, label_keys)


def one_hot_encode(df, column):
    encoder = OneHotEncoder()
    onehotarray = encoder.fit_transform(df[[column]]).toarray()
    columns = [f"{column}_{item}" for item in encoder.categories_[0]]
    df[columns] = onehotarray
    return df


def label_key(predict_window):
    return f"pchange_{predict_window}day"


def percent_change(data, window=1, track_feature="close"):
    if window < 0:
        return -(
            data[track_feature].diff(periods=window) / data[track_feature] * 100
        ).shift(-window)
    else:
        return -(
            (data[track_feature] - data[track_feature].shift(-window))
            / data[track_feature]
            * 100
        )