from datetime import date, timedelta

from prediction import Prediction
import datastore as ds
import util

def predict_price_change(model, mk_signal_set, tickers, predict_from_date=None, min_samples=1000):
    predict_from_date = predict_from_date if predict_from_date is not None else date.today()
    lookback_date = predict_from_date - timedelta(days=min_samples)

    candles = ds.get_daily_candlesticks(tickers, lookback_date, predict_from_date)

    predictions = {}
    for ticker in tickers:
        try:
            signals = mk_signal_set(candles[ticker])
            if signals is not None:
                X, y, _ = signals.to_xy()
                y_pred = signals.y_scaler.inverse_transform(model(X[-1, :]).reshape(-1, 1))[:, 0]

                predictions[ticker] = Prediction(model.id, date.today(), predict_from_date, model.window, ticker, y_pred)
            else:
                print('Signals is none!')
        except Exception as ex:
            print('Exception on', ticker)
            util.print_exception(ex)
        except KeyError as ex:
            print('Exception on', ticker)
            util.print_exception(ex)
        except:
            print('Exception on', ticker)

    return predictions

def predict_price_change_raw(model, signals, predict_from_date):
    try:
        if signals is not None:
            X, y, _ = signals.to_xy()
            y_pred = model(X[-1, :])

            return Prediction(model.id, date.today(), predict_from_date, model.window, ticker, y_pred)
        else:
            print('Signals is none!')
    except Exception as ex:
        print('Exception on', ticker)
        util.print_exception(ex)
    except KeyError as ex:
        print('Exception on', ticker)
        util.print_exception(ex)
    except:
        print('Exception on', ticker)
