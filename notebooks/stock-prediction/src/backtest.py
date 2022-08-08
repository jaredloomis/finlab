import numpy as np
import pandas as pd
import itertools
#from numba import jit
from sklearn.model_selection import train_test_split
from sklearn.ensemble import RandomForestRegressor
from multiprocessing import cpu_count
import matplotlib.pyplot as plt

from pathos.multiprocessing import ProcessingPool as Pool

from technical_signals import TechnicalSignals


def backtest(strategy, df, skip_index=120, processes=cpu_count()//2):
    """
    Run a strategy once per day - buy, sell, or hold.

    strategy : function[date, row, state -> action, state]
    
    returns a list of actions taken, and resulting state.
    """
    with Pool(processes) as pool:
        def f(idx):
            return strategy.execute(df[df.index < idx])
        actions = pool.map(f, df.index[skip_index:])
        actions = list(filter(lambda x: x is not None, actions))
        return actions

class Action:
    def __init__(self, date, action_type, price, params={}):
        self.action_type = action_type
        self.date = date
        self.price = price
        self.params = params

    def __repr__(self):
        return f"Action(date={self.date}, action_type={self.action_type}, price={self.price}, params={self.params})"

def track_balance(start_cash, actions):
    cash = start_cash
    assets = 0
    latest_price = None
    snapshots = {}
    for action in actions:
        latest_price = action.price
        if action.action_type == "sell":
            if assets > 0:
                cash += action.price
                assets -= 1
        elif action.action_type == "buy":
            if cash > 0:
                cash -= action.price
                assets += 1

        snapshots[action.date] = {
            "cash": cash,
            "assets": assets,
            "latest_price": latest_price,
            "total_value": None if latest_price is None else cash + assets * latest_price
        }
    return snapshots

def plot_backtest(snapshots: dict):
    x = snapshots.keys()
    total = list(map(lambda v: v["total_value"], snapshots.values()))
    cash = list(map(lambda v: v["cash"], snapshots.values()))
    assets = list(map(lambda v: v["assets"] * v["latest_price"] if v["latest_price"] is not None else None, snapshots.values()))
    price = list(map(lambda v: v["latest_price"], snapshots.values()))
    plt.plot(x, total, label="Total Value")
    plt.plot(x, cash, label="Cash")
    plt.plot(x, assets, label="Assets")
    plt.plot(x, price, label="Asset Price")
    plt.xlabel("Date")
    plt.ylabel("Total Value")
    plt.legend()
    plt.show()
    return plt
    

class Strategy:
    def execute(self, df):
        return None

class TechnicalIndicatorsModelStrategy(Strategy):
    def __init__(self, model, window=7, cutoff=3, pretrain_df=None):
        self.model = model
        self.window = window
        self.cutoff = cutoff
        self.pretrain = pretrain_df is not None

        if self.pretrain:
            tsigs = TechnicalSignals(pretrain_df, predict_window=self.window)
            X, y, Xy_date = tsigs.toXy()
            self.model.fit(X, y)

    def execute(self, df):
        if not self.pretrain:
            tsigs = TechnicalSignals(df, predict_window=self.window)
            X, y, Xy_date = tsigs.toXy()
            X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.1)
            Xy_test_date = Xy_date.iloc[-y_test.shape[0]:]
            # Train model
            self.model.fit(X_train, y_train)
        # Predict
        X, _y, _Xy_date = TechnicalSignals(df, predict_window=self.window).toXy()
        y_predicted = self.model.predict(X[-1, :].reshape(1, -1))[-1]
        # Decide on action
        date = df["date"].to_numpy()[-1]
        price = df["close"].to_numpy()[-1]
        if y_predicted > self.cutoff:
            return Action(date, "buy", price)
        elif y_predicted < -self.cutoff:
            return Action(date, "sell", price)
        return None
