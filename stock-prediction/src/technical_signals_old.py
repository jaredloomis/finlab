class TechnicalSignals:
    """
    A container for storing data related to training models on technical signals.

    Intended features:
    - Easy shortcuts to common data preparation (to pandas, drop na, etc).
    - Allows some way to get the date associated with a specific row of the prepared data.
    """

    def __init__(self, data, predict_window):
        self.predict_window = predict_window
        self.X_scaler = StandardScaler()
        self.y_scaler = StandardScaler()
        self.label_key = label_key(predict_window)
        self.date = data["date"]

        # Feature columns
        signals = pd.DataFrame()
        signals["close"] = data["close"]
        # TODO vary features based on predict_window
        # Technical indicators
        # Mostly momentum indicators
        # signals["rsi5"] = ta.momentum.RSIIndicator(data["close"], window=5).rsi()
        # signals["rsi7"] = ta.momentum.RSIIndicator(data["close"], window=7).rsi()
        signals["rsi14"] = ta.momentum.RSIIndicator(data["close"], window=14).rsi()
        signals["rsi30"] = ta.momentum.RSIIndicator(data["close"], window=30).rsi()
        signals["rsi60"] = ta.momentum.RSIIndicator(data["close"], window=60).rsi()
        signals["rsi120"] = ta.momentum.RSIIndicator(data["close"], window=120).rsi()
        signals["rsi240"] = ta.momentum.RSIIndicator(data["close"], window=240).rsi()
        signals["kama"] = ta.momentum.KAMAIndicator(data["close"]).kama()
        signals["kama60"] = ta.momentum.KAMAIndicator(data["close"], window=60).kama()
        signals["kama120"] = ta.momentum.KAMAIndicator(data["close"], window=120).kama()
        signals["kama240"] = ta.momentum.KAMAIndicator(data["close"], window=240).kama()
        signals["percent_price_osc"] = ta.momentum.PercentagePriceOscillator(
            data["close"]
        ).ppo()
        signals["ema14"] = ta.trend.EMAIndicator(data["close"]).ema_indicator()
        signals["ema30"] = ta.trend.EMAIndicator(
            data["close"], window=30
        ).ema_indicator()
        signals["ema60"] = ta.trend.EMAIndicator(
            data["close"], window=60
        ).ema_indicator()
        signals["ema120"] = ta.trend.EMAIndicator(
            data["close"], window=120
        ).ema_indicator()
        # Volatility
        signals["avg_true_range"] = ta.volatility.AverageTrueRange(
            data["high"], data["low"], data["close"]
        ).average_true_range()
        signals["avg_true_range60"] = ta.volatility.AverageTrueRange(
            data["high"], data["low"], data["close"], window=60
        ).average_true_range()
        signals["avg_true_range120"] = ta.volatility.AverageTrueRange(
            data["high"], data["low"], data["close"], window=120
        ).average_true_range()
        signals["bollinger_wband"] = ta.volatility.BollingerBands(
            data["close"]
        ).bollinger_wband()
        donchian40 = ta.volatility.DonchianChannel(
            data["high"], data["low"], data["close"], window=40
        )
        signals["donchian40_mband"] = donchian40.donchian_channel_hband()
        signals["donchian40_pband"] = donchian40.donchian_channel_pband()
        signals["donchian40_wband"] = donchian40.donchian_channel_wband()
        donchian = ta.volatility.DonchianChannel(
            data["high"], data["low"], data["close"]
        )
        signals["donchian20_mband"] = donchian.donchian_channel_hband()
        signals["donchian20_pband"] = donchian.donchian_channel_pband()
        signals["donchian20_wband"] = donchian.donchian_channel_wband()
        signals["ulcer14"] = ta.volatility.UlcerIndex(data["close"]).ulcer_index()
        signals["ulcer40"] = ta.volatility.UlcerIndex(
            data["close"], window=40
        ).ulcer_index()
        signals["ulcer80"] = ta.volatility.UlcerIndex(
            data["close"], window=80
        ).ulcer_index()
        signals["ulcer120"] = ta.volatility.UlcerIndex(
            data["close"], window=120
        ).ulcer_index()
        # Trend indicators
        adx = ta.trend.ADXIndicator(data["high"], data["low"], data["close"], window=14)
        signals["adx"] = adx.adx()
        signals["adx_neg"] = adx.adx_neg()
        signals["adx_pos"] = adx.adx_pos()
        adx30 = ta.trend.ADXIndicator(
            data["high"], data["low"], data["close"], window=30
        )
        signals["adx30"] = adx.adx()
        signals["adx_neg30"] = adx.adx_neg()
        signals["adx_pos30"] = adx.adx_pos()
        aroon = ta.trend.AroonIndicator(data["close"])
        signals["aroon_down"] = aroon.aroon_down()
        signals["aroon_indicator"] = aroon.aroon_indicator()
        signals["aroon_up"] = aroon.aroon_up()
        signals["cci"] = ta.trend.CCIIndicator(
            data["high"], data["low"], data["close"]
        ).cci()
        kst = ta.trend.KSTIndicator(data["close"])
        signals["kst"] = kst.kst()
        signals["kst_diff"] = kst.kst_diff()
        signals["kst_sig"] = kst.kst_sig()
        macd = ta.trend.MACD(data["close"])
        signals["macd"] = macd.macd()
        signals["macd_diff"] = macd.macd_diff()
        signals["macd_signal"] = macd.macd_signal()
        # % change history
        signals["pchange_-1day"] = percent_change(data, window=-1)
        signals["pchange_-2day"] = percent_change(data, window=-2)
        signals["pchange_-3day"] = percent_change(data, window=-3)
        signals["pchange_-4day"] = percent_change(data, window=-4)
        signals["pchange_-5day"] = percent_change(data, window=-5)
        signals["pchange_-7day"] = percent_change(data, window=-7)
        signals["pchange_-14day"] = percent_change(data, window=-14)
        signals["pchange_-30day"] = percent_change(data, window=-30)
        signals["pchange_-60day"] = percent_change(data, window=-60)
        signals["pchange_-120day"] = percent_change(data, window=-120)
        signals["pchange_-240day"] = percent_change(data, window=-240)
        signals["pchange_-480day"] = percent_change(data, window=-480)
        signals["pchange_-960day"] = percent_change(data, window=-960)
        signals["pchange_-1920day"] = percent_change(data, window=-1920)
        # Day of the week
        # signals["day_of_week"] = data["date"].apply(lambda date: date.weekday())
        # one_hot_encode(signals, "day_of_week")
        # del signals["day_of_week"]
        # Label column
        signals[self.label_key] = percent_change(data, window=predict_window)
        self.signals = signals

    def toX(self):
        # TODO DONT SCALE ONE-HOT VARIABLES
        # Compute associated dates
        date = self.date[(~self.signals.isna()).any(axis=1).index]
        # Remove label, remove rows with nans, to numpy
        # TODO TRY FILLING?
        X = (
            self.signals.loc[:, self.signals.columns != self.label_key]
            .dropna()
            .to_numpy()
        )
        # Scale
        X = self.X_scaler.fit_transform(X)
        return X, date

    def toXy(self):
        # TODO DONT SCALE ONE-HOT VARIABLES
        # Compute associated dates
        date = self.date[(~self.signals.isna()).any(axis=1).index]
        # Remove nans
        # TODO TRY FILLING?
        features = self.signals.dropna()
        # Pull out labels and features
        y = features[self.label_key].to_numpy().reshape(-1, 1)
        X = features.loc[:, features.columns != self.label_key].to_numpy()
        # Scale
        X = self.X_scaler.fit_transform(X)
        y = self.y_scaler.fit_transform(y)[:, 0]
        return X, y, date

    def append(self, other):
        self.signals = pd.concat([self.signals, other.signals], axis=0)
        self.date = pd.concat([self.date, other.date], axis=0)
        return self
