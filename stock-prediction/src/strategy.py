import math
import random

from sklearn.model_selection import train_test_split

from backtest import Action
from technical_signals import TechnicalSignalSet


class Strategy:
    def train(self, df):
        pass

    def execute(self, df):
        return None

class PretrainedModelStrategy(Strategy):
    def __init__(
        self, predict, df_to_signal_set, cutoff=2, bias=0
    ):
        self.predict = predict
        self.df_to_signal_set = df_to_signal_set
        self.cutoff = cutoff
        self.bias = bias
    
    def execute(self, df):
        # Predict
        X, _y, _Xy_date = self.df_to_signal_set(df).to_xy()
        y_predicted = self.predict(X[-1, :].reshape(1, -1))[-1]
        # Decide on action
        date = df["date"].to_numpy()[-1]
        share_count = self.share_count(y_predicted)
        if y_predicted > self.cutoff - self.bias:
            return Action(date, "buy", 1)
        elif y_predicted < -self.cutoff - self.bias:
            return Action(date, "sell", 1)
        return None

    def share_count(self, prediction):
        return abs(math.floor(prediction * 5))

class SignalModelStrategy(Strategy):
    def __init__(
        self, model, df_to_signal_set, cutoff=2, bias=0, pretrain_df=None, pretrain_sigs=None
    ):
        self.model = model
        self.df_to_signal_set = df_to_signal_set
        self.cutoff = cutoff
        self.bias = bias
        self.pretrain = pretrain_df is not None or pretrain_sigs is not None

        if self.pretrain:
            self.train(pretrain_df, sigs=pretrain_sigs, force_train=True)

    def train(self, df, sigs=None, force_train=None):
        if not self.pretrain or force_train == True:
            if not sigs:
                sigs = self.df_to_signal_set(df)
            X, y, Xy_date = sigs.to_xy()
            self.model.fit(X, y)

            if hasattr(self.model, "feature_importances_"):
                importances = list(
                    zip(self.model.feature_importances_, sigs.signals.keys())
                )
                importances.sort(key=lambda x: x[0], reverse=True)
                print(f"feature importance:")
                for imp, name in importances:
                    print(f"{name}: {imp}")

    def execute(self, df):
        # Predict
        X, _y, _Xy_date = self.df_to_signal_set(df).to_xy()
        y_predicted = self.model.predict(X[-1, :].reshape(1, -1))[-1]
        # Decide on action
        date = df["date"].to_numpy()[-1]
        share_count = self.share_count(y_predicted)
        if y_predicted > self.cutoff - self.bias:
            return Action(date, "buy", 1)
        elif y_predicted < -self.cutoff - self.bias:
            return Action(date, "sell", 1)
        return None

    def share_count(self, prediction):
        return abs(math.floor(prediction * 5))


class TechnicalIndicatorsModelStrategy(Strategy):
    def __init__(
        self, model, window=7, cutoff=3, bias=0, pretrain_df=None, pretrain_tsigs=None
    ):
        self.model = model
        self.window = window
        self.cutoff = cutoff
        self.bias = bias
        self.pretrain = pretrain_df is not None or pretrain_tsigs is not None

        if self.pretrain:
            self.train(pretrain_df, tsigs=pretrain_tsigs, force_train=True)

    def train(self, df, tsigs=None, force_train=None):
        if not self.pretrain or force_train == True:
            if not tsigs:
                tsigs = TechnicalSignalSet(df, predict_window=self.window)
            X, y, Xy_date = tsigs.to_xy()
            self.model.fit(X, y)

            if hasattr(self.model, "feature_importances_"):
                importances = list(
                    zip(self.model.feature_importances_, tsigs.signals.keys())
                )
                importances.sort(key=lambda x: x[0], reverse=True)
                print(f"feature importance:")
                for imp, name in importances:
                    print(f"{name}: {imp}")

    def execute(self, df):
        # Predict
        X, _y, _Xy_date = TechnicalSignalSet(df, predict_window=self.window).to_xy()
        y_predicted = self.model.predict(X[-1, :].reshape(1, -1))[-1]
        # Decide on action
        date = df["date"].to_numpy()[-1]
        share_count = self.share_count(y_predicted)
        if y_predicted > self.cutoff - self.bias:
            return Action(date, "buy", 1)
        elif y_predicted < -self.cutoff - self.bias:
            return Action(date, "sell", 1)
        return None

    def share_count(self, prediction):
        return abs(math.floor(prediction * 5))


class DummyStrategy(Strategy):
    def __init__(self, cutoff=0.7):
        self.cutoff = cutoff

    def execute(self, df):
        date = df["date"].to_numpy()[-1]

        rand = random.random() * 2 - 1
        # print(rand)
        if rand > self.cutoff:
            return Action(date, "buy", 1)
        elif rand < -self.cutoff:
            return Action(date, "sell", 1)
        return None
