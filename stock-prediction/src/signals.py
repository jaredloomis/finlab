import numpy as np
import pandas as pd
import ta
from sklearn.preprocessing import StandardScaler, RobustScaler
from sklearn.preprocessing import OneHotEncoder


class SignalSet:
    """
    A container for processing raw data into a set of signals suitable as features for an ML model.

    date : pd.Series of dates
    signals : list[Signal]
    label_keys : set[str] | list[str]
    """
    def __init__(self, date, signals, label_keys, signals_df=None):
        self.date = date
        if signals_df is None:
            # Concat signals into one big DataFrame
            self.signals = pd.DataFrame.from_dict({signal.column_name: signal.data for signal in signals})
        else:
            self.signals = signals_df
        self.label_keys = label_keys

        self.X_scaler = StandardScaler()   # RobustScaler()
        self.y_scaler = StandardScaler()

    def to_x(self):
        X, y, date = self.to_xy()
        return X, date

    def to_xy(self):
        # TODO one-hot
        # TODO custom scaling, per feature
        features = self.signals

        # Fill in any nans with the most recently seen value
        features = features.fillna(method="ffill")

        # Fill in any remaining nans with 0.0
        #features = features.fillna(0.0)

        # Remove all rows containing a nan, get the associated dates
        date = self.date[(~features.isna()).any(axis=1).index]
        features = features.dropna()

        # Split into X, y
        X = features[features.columns.difference(self.label_keys)] \
                .to_numpy()
        y = features[self.label_keys].to_numpy() #.reshape(-1, 1)

        # Scale
        X = self.X_scaler.fit_transform(X)
        y = self.y_scaler.fit_transform(y)[:, 0]
        return X, y, date

    @staticmethod
    def concat(a, b):
        # TODO take a list[SignalSet] as argument
        # XXX HOW TO HANDLE DATE? Combine?
        return SignalSet(b.date, None, set(a.label_keys + b.label_keys), signals_df=pd.concat([a.signals, b.signals]))


class Signal:
    def __init__(self, column_name, data, one_hot=False, scaling="standard"):
        """
        column_name : str
        data : pd.Series
        one_hot : bool (not yet functional)
        scaling : "standard" | "minmax" | None (not yet functional)
        """
        self.column_name = column_name
        self.data = data
        self.one_hot = one_hot
        self.scaling = scaling

    def as_series(self):
        return self.data


def one_hot_encode(df, column):
    # TODO use pandas built-in (forget the name)
    encoder = OneHotEncoder()
    onehotarray = encoder.fit_transform(df[[column]]).toarray()
    columns = [f"{column}_{item}" for item in encoder.categories_[0]]
    df[columns] = onehotarray
    return df


def label_key(predict_window):
    return f"pchange_{predict_window}day"


def percent_change(data, window=1, track_feature="close"):
    """
    Note: Includes `window` nans at the beginning of output.
    """
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
