import numpy as np
import pandas as pd
import ta
from sklearn.preprocessing import StandardScaler
from sklearn.preprocessing import OneHotEncoder


class TechnicalSignals():
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
		# TODO vary features based on predict_window
		# Technical indicators
		# Mostly momentum indicators
		signals["rsi5"] = ta.momentum.RSIIndicator(data["close"], window=5).rsi()
		signals["rsi7"] = ta.momentum.RSIIndicator(data["close"], window=7).rsi()
		signals["rsi14"] = ta.momentum.RSIIndicator(data["close"], window=14).rsi()
		signals["rsi30"] = ta.momentum.RSIIndicator(data["close"], window=30).rsi()
		signals["kama"] = ta.momentum.KAMAIndicator(data["close"]).kama()
		signals["percent_price_osc"] = ta.momentum.PercentagePriceOscillator(data["close"]).ppo()
		signals["ema"] = ta.trend.EMAIndicator(data["close"]).ema_indicator()
		signals["stoch_rsi"] = ta.momentum.StochRSIIndicator(data["close"]).stochrsi()
		signals["avg_true_range"] = ta.volatility.AverageTrueRange(data["high"], data["low"], data["close"]).average_true_range()
		bollinger = ta.volatility.BollingerBands(data["close"])
		signals["bollinger_high"] = bollinger.bollinger_hband()
		signals["bollinger_low"] = bollinger.bollinger_lband()
		signals["bollinger_avg"] = bollinger.bollinger_mavg()
		# Trend indicators
		adx = ta.trend.ADXIndicator(data["high"], data["low"], data["close"])
		signals["adx"] = adx.adx()
		signals["adx_neg"] = adx.adx_neg()
		signals["adx_pos"] = adx.adx_pos()
		aroon = ta.trend.AroonIndicator(data["close"])
		signals["aroon_down"] = aroon.aroon_down()
		signals["aroon_indicator"] = aroon.aroon_indicator()
		signals["aroon_up"] = aroon.aroon_up()
		signals["cci"] = ta.trend.CCIIndicator(data["high"], data["low"], data["close"]).cci()
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
		# Day of the week
		signals["day_of_week"] = data["date"].apply(lambda date: date.weekday())
		one_hot_encode(signals, "day_of_week")
		del signals["day_of_week"]
		# TODO more features
		# Label column
		signals[self.label_key] = percent_change(data, window=predict_window)
		self.signals = signals

	def toX(self):
		# TODO DONT SCALE ONE-HOT VARIABLES
		# Compute associated dates
		date = self.date[(~self.signals.isna()).any(axis=1).index]
		# Remove label, remove rows with nans, to numpy
		# TODO TRY FILLING?
		X = self.signals.loc[:, self.signals.columns != self.label_key].dropna().to_numpy()
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


def one_hot_encode(df, column):
	encoder = OneHotEncoder()
	onehotarray = encoder.fit_transform(df[[column]]).toarray()
	columns = [f'{column}_{item}' for item in encoder.categories_[0]]
	df[columns] = onehotarray
	return df

def label_key(predict_window):
	return f'pchange_{predict_window}day'

def percent_change(data, window=1, track_feature="close"):
	"""
	Note: Includes a single nan at the beginning of output.
	"""
	#if window < 0:
	#	return data[track_feature].pct_change(periods=-window) * 100
	#else:
	if window < 0:
		return -(data[track_feature].diff(periods=window) / data[track_feature] * 100).shift(-window)
	else:
		return -((data[track_feature] - data[track_feature].shift(-window)) / data[track_feature] * 100)