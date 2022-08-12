import math
import random

from sklearn.model_selection import train_test_split

from backtest import Action
from technical_signals import TechnicalSignals

class Strategy:
    def train(self, df):
        pass

    def execute(self, df):
        return None

class TechnicalIndicatorsModelStrategy(Strategy):
    def __init__(self, model, window=7, cutoff=3, bias=0, pretrain_df=None, pretrain_tsigs=None):
        self.model = model
        self.window = window
        self.cutoff = cutoff
        self.bias = bias
        self.pretrain = pretrain_df is not None or pretrain_tsigs is not None

        if self.pretrain:
            self.train(pretrain_df, pretrain_tsigs, force_train=True)
    
    def train(self, df, tsigs=None, force_train=None):
        if not self.pretrain or force_train == True:
            if not tsigs:
                tsigs = TechnicalSignals(df, predict_window=self.window)
            X, y, Xy_date = tsigs.toXy()
            self.model.fit(X, y)

            if hasattr(self.model, 'feature_importances_'):
                importances = list(zip(self.model.feature_importances_, list(tsigs.signals)))
                importances.sort(key=lambda x: x[0], reverse=True)
                print(f"feature importance:")
                for imp, name in importances:
                    print(f"{name}: {imp}")

    def execute(self, df):
        # Predict
        X, _y, _Xy_date = TechnicalSignals(df, predict_window=self.window).toXy()
        y_predicted = self.model.predict(X[-1, :].reshape(1, -1))[-1]
        # Decide on action
        date = df["date"].to_numpy()[-1]
        price = df["close"].to_numpy()[-1]
        share_count = self.share_count(y_predicted)
        if y_predicted > self.cutoff - self.bias:
            return Action(date, "buy", 1, price)
        elif y_predicted < -self.cutoff - self.bias:
            return Action(date, "sell", 1, price)
        return None

    def share_count(self, prediction):
        return abs(math.floor(prediction * 5))

class DummyStrategy(Strategy):
    def __init__(self, cutoff=0.7):
        self.cutoff = cutoff

    def execute(self, df):
        date = df["date"].to_numpy()[-1]
        price = df["close"].to_numpy()[-1]

        rand = random.random() * 2 - 1
        #print(rand)
        if rand > self.cutoff:
            return Action(date, "buy", 1, price)
        elif rand < -self.cutoff:
            return Action(date, "sell", 1, price)
        return None
