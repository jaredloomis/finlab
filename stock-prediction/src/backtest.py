import numpy as np
import traceback

# from numba import jit
from multiprocessing import cpu_count
import matplotlib.pyplot as plt
from datetime import timedelta

from pathos.multiprocessing import ProcessPool as Pool

import datastore as ds
import util
from signal_library import FetchOptions, fetch_signal_set
from util import TimeRange


def comprehensive_backtest(
    strategy,
    tickers,
    timerange,
    interval='5min',
    start_cash=10000,
    train_test_ratio=0.3,
    plot=True,
    log=True,
    **kwargs,
):
    if isinstance(tickers, str):
        tickers = [tickers]

    _, interval = util.parse_interval(interval)

    results = {}
    for ticker in tickers:
        print("Ticker:", ticker)

        # Run strategy
        actions = backtest(strategy, ticker, timerange, **kwargs)

        # Pull data from db
        # TODO caching?
        data = ds.get_candles([ticker], timerange[0], timerange[1], interval=interval)[ticker]
        print(data)

        # Print results
        bt_results = track_balance(start_cash, actions, data)

        # TODO
        """
        price_change = (
            (data.iloc[-1]["close"] - data.iloc[split_index]["close"])
            / data.iloc[split_index]["close"]
            * 100
        )
        """
        if len(bt_results.keys()) > 0:
            final_result = bt_results[list(bt_results.keys())[-1]]
            gain_loss = (
                (final_result["total_value"] - start_cash) / start_cash * 100
            )
            roi = bt_results[list(bt_results.keys())[-1]]["roi"]
        else:
            gain_loss = 0
            final_result = None
            roi = 0
        if log:
            print(
                f"{len(actions)} buy/sells performed - {len(actions) / len(data.index) * 100}% of the time"
            )
            # TODO
            #print(f"Stock price change: {price_change}%")
            print(f"ROI: {roi * 100}%")
            # TODO
            #print(f"Relative ROI: {roi * 100 / price_change}%")
            print(final_result)

        # Plot results and asset price
        if plot:
            plot_backtest(bt_results, actions, data)

        # Collect results
        results[ticker] = final_result

    # Summarize results
    # TODO average ROI
    gainloss_summary = list(
        map(lambda res: ((res and res["total_value"]) or start_cash) - start_cash, results.values())
    )
    if len(gainloss_summary) > 0:
        average_gainloss = sum(gainloss_summary) / len(gainloss_summary)
    else:
        average_gainloss = 0

    return {"average_gainloss": average_gainloss, "results": results}


def backtest(strategy, ticker: str, timerange: TimeRange, min_sample_dur=timedelta(days=10), processes=cpu_count() // 2):
    """
    Run a strategy once per day - buy, sell, or hold.

    strategy : function[date, row, state -> action, state]

    returns a list of actions taken.
    """
    try:
        signals = fetch_signal_set(strategy.model.features, strategy.model.labels, FetchOptions({ticker}, timerange))[ticker]
    except Exception as ex:
        util.print_exception(ex)
        return []
    earliest_time = signals.date[0]

    if processes > 1:
        with Pool(processes) as pool:
            def f(cur_time):
                if cur_time - earliest_time > min_sample_dur:
                    return strategy.execute(signals.subset((earliest_time, cur_time)))
                else:
                    return None

            actions = pool.map(f, signals.date[signals.date < timerange[1]])
            actions = list(filter(lambda x: x is not None, actions))
            return actions
    else:
        actions = []
        for cur_time in signals.date[signals.date < timerange[1]]:
            if cur_time - earliest_time > min_sample_dur:
                #print((earliest_time, cur_time))
                #print(signals.subset((earliest_time, cur_time)).signals.shape)
                #print(signals.subset((earliest_time, cur_time)))
                #print(signals.subset((earliest_time, cur_time)))
                actions.append(strategy.execute(signals.subset((earliest_time, cur_time))))
        actions = list(filter(lambda x: x is not None, actions))
        return actions


class Action:
    def __init__(self, date, action_type, quantity, params={}):
        self.action_type = action_type
        self.date = date
        self.quantity = quantity
        self.params = params

    def __repr__(self):
        return f"Action(date={self.date}, action_type={self.action_type}, quantity={self.quantity}, params={self.params})"


def track_balance(start_cash, actions, candlesticks):
    cash = start_cash
    assets = 0
    total_invested = 0
    total_return = 0
    snapshots = {}
    for action in actions:
        latest_price = candlesticks[candlesticks["time"] == action.date]["open"].to_numpy()[0]
        if action.action_type == "sell":
            if assets > 0:
                cash += latest_price * action.quantity
                assets -= action.quantity
                total_return += latest_price * action.quantity
        elif action.action_type == "buy":
            if cash - latest_price * action.quantity > 0:
                cash -= latest_price * action.quantity
                assets += action.quantity
                total_invested += latest_price * action.quantity

        snapshots[action.date] = {
            "cash": cash,
            "assets": assets,
            "latest_price": latest_price,
            "total_value": cash + assets * latest_price,
            "roi": ((total_return - total_invested) + assets * latest_price) / total_invested,
        }

    # Add a final snapshot
    final_date = candlesticks["time"].to_numpy()[-1]
    latest_price = candlesticks[candlesticks["time"] == final_date]["open"].to_numpy()[0]
    snapshots[final_date] = {
        "cash": cash,
        "assets": assets,
        "latest_price": latest_price,
        "total_value": None
        if latest_price is None
        else cash + assets * latest_price,
        "roi": ((total_return - total_invested) + assets * latest_price) / total_invested,
    }
    return snapshots


def plot_backtest(snapshots: dict, actions, df):
    # Plot of snapshots of asset and cash balances over trading session
    snapshots = sorted(snapshots.items(), key=lambda x: x[0])
    filtered_snapshots = list(
        map(lambda kv:
            (kv[0],
            kv[1]["total_value"],
            kv[1]["cash"],
            kv[1]["assets"] * kv[1]["latest_price"] if kv[1]["latest_price"] is not None else None
            ),
            snapshots
        )
    )
    x, total, cash, assets = zip(*filtered_snapshots)
    """
    x = snapshots.keys() # df['date]
    total = list(map(lambda v: v["total_value"], snapshots.values()))
    cash = list(map(lambda v: v["cash"], snapshots.values()))
    assets = list(
        map(
            lambda v: v["assets"] * v["latest_price"]
            if v["latest_price"] is not None
            else None,
            snapshots.values(),
        )
    )
    """
    plt.plot(x, total, label="Total Value")
    plt.plot(x, cash, label="Cash")
    plt.plot(x, assets, label="Value of Holdings")
    plt.xticks(rotation=45)
    plt.legend()
    fig1 = plt.show()

    # Plot of asset price and vertical lines indicating buy/sell signals
    plt.plot(df["time"], df["close"], label="Asset Price")
    for action in actions:
        if action.action_type == "buy":
            plt.axvline(x=action.date, color="green")
        elif action.action_type == "sell":
            plt.axvline(x=action.date, color="red")
    plt.xticks(rotation=45)
    fig2 = plt.show()

    # Plot of asset price and shaded buy/sell regions
    plt.plot(df["time"], df["close"], label="Asset Price")
    buy_sell_spans = []
    half_span = None
    for action in actions:
        if half_span is None:
            half_span = action
        elif action.action_type != half_span.action_type:
            buy_sell_spans.append((half_span, action))
            half_span = action
    if half_span is not None:
        buy_sell_spans.append(
            (
                half_span,
                Action(
                    df.iloc[-1]["time"],
                    "buy" if half_span.action_type == "sell" else "sell",
                    -1
                ),
            )
        )

    for start_action, end_action in buy_sell_spans:
        color = {"buy": "lightgreen", "sell": "lightcoral"}.get(
            start_action.action_type
        ) or "lightgray"
        plt.axvspan(start_action.date, end_action.date, color=color)
    plt.xticks(rotation=45)
    fig3 = plt.show()

    return [fig1, fig2, fig3]
