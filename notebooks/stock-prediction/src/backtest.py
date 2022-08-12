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
import datastore as ds


def comprehensive_backtest(strategy, tickers, start_date, end_date, start_cash=10000, train_test_ratio=0.3, plot=False, **kwargs):
    results = {}
    for ticker in tickers:
        try:
            print('Ticker:', ticker)
            
            # Download data to db
            ds.download_daily_candlesticks([ticker], start_date, end_date)
            # Load data from db
            data = ds.get_daily_candlesticks([ticker], start_date, end_date)[ticker]

            # Train/test split index
            split_index = int(np.floor(train_test_ratio * len(data.index)))
            
            # Run strategy
            actions = backtest(strategy, data, split_index=split_index, **kwargs)
            
            # Print results
            print(f"{len(actions)} buy/sells performed - {len(actions) / len(data.index) * 100}% of the time")
            bt_results = track_balance(start_cash, actions)
            price_change = (data.iloc[-1]["close"] - data.iloc[split_index]["close"]) / data.iloc[split_index]["close"] * 100
            print(f"Stock price change: {price_change}%")
            final_result = bt_results[list(bt_results.keys())[-1]]
            gain_loss = (final_result["total_value"] - start_cash) / start_cash * 100
            print(f"Total gain/loss: {gain_loss}%")
            print(final_result)
            
            # Plot results and asset price
            if plot:
                plot_backtest(bt_results, actions, data)

            # Collect results
            results[ticker] = final_result
        except Exception as ex:
            print("Exception:")
            print(ex)

    # Summarize results
    gainloss_summary = list(map(lambda res: res["total_value"] - start_cash, results.values()))
    average_gainloss = sum(gainloss_summary) / len(gainloss_summary)

    return {
        "average_gainloss": average_gainloss,
        "results": results
    }
    


def backtest(strategy, df, split_index=120, processes=cpu_count()//2):
    """
    Run a strategy once per day - buy, sell, or hold.

    strategy : function[date, row, state -> action, state]
    
    returns a list of actions taken, and resulting state.
    """
    with Pool(processes) as pool:
        strategy.train(df.head(split_index))
        def f(idx):
            return strategy.execute(df[df.index < idx])

        actions = pool.map(f, df.index[split_index:])
        actions = list(filter(lambda x: x is not None, actions))
        return actions

class Action:
    def __init__(self, date, action_type, quantity, price, params={}):
        self.action_type = action_type
        self.date = date
        self.price = price
        self.quantity = quantity
        self.params = params

    def __repr__(self):
        return f"Action(date={self.date}, action_type={self.action_type}, quantity={self.quantity}, price={self.price}, params={self.params})"

def track_balance(start_cash, actions):
    cash = start_cash
    assets = 0
    latest_price = None
    snapshots = {}
    for action in actions:
        latest_price = action.price
        if action.action_type == "sell":
            if assets > 0:
                cash += action.price * action.quantity
                assets -= action.quantity
        elif action.action_type == "buy":
            if cash > 0:
                cash -= action.price * action.quantity
                assets += action.quantity

        snapshots[action.date] = {
            "cash": cash,
            "assets": assets,
            "latest_price": latest_price,
            "total_value": None if latest_price is None else cash + assets * latest_price
        }
    return snapshots

def plot_backtest(snapshots: dict, actions, df):
    x = snapshots.keys()
    total = list(map(lambda v: v["total_value"], snapshots.values()))
    cash = list(map(lambda v: v["cash"], snapshots.values()))
    assets = list(map(lambda v: v["assets"] * v["latest_price"] if v["latest_price"] is not None else None, snapshots.values()))
    price = list(map(lambda v: v["latest_price"], snapshots.values()))
    plt.plot(x, total, label="Total Value")
    plt.plot(x, cash, label="Cash")
    plt.plot(x, assets, label="Value of Holdings")
    plt.legend()
    fig1 = plt.show()

    plt.plot(df["date"], df["close"], label="Asset Price")
    for action in actions:
        if action.action_type == "buy":
            plt.axvline(x=action.date, color='green')
        elif action.action_type == "sell":
            plt.axvline(x=action.date, color='red')
    fig2 = plt.show()

    # TODO background color https://matplotlib.org/stable/api/_as_gen/matplotlib.axes.Axes.axvspan.html
    plt.plot(df["date"], df["close"], label="Asset Price")
    buy_sell_spans = []
    half_span = None
    for action in actions:
        if half_span is None:
            half_span = action
        elif action.action_type != half_span.action_type:
            buy_sell_spans.append((half_span, action))
            half_span = action

    if half_span is not None:
        buy_sell_spans.append((half_span, Action(df.iloc[-1]["date"], "buy" if half_span.action_type == "sell" else "sell", -1, half_span.price)))
    
    for start_action, end_action in buy_sell_spans:
        color = {"buy": "lightgreen", "sell": "lightcoral"}.get(start_action.action_type) or "lightgray"
        plt.axvspan(start_action.date, end_action.date, color=color)
    fig2 = plt.show()

    return [fig1, fig2]
