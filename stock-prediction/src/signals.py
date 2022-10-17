import pandas as pd
from sklearn.preprocessing import StandardScaler, RobustScaler
from sklearn.preprocessing import OneHotEncoder


class SignalSet:
    """
    A container for processing raw data into a set of signals suitable as features for an ML model.

    time : pd.Series of datetimes
    signals : list[Signal] | pd.DataFrame
    label_keys : set[str] | list[str]
    """
    def __init__(self, date, signals, label_keys, X_scaler=None, y_scaler=None):
        self.date = date
        if isinstance(signals, pd.DataFrame):
            self.signals = signals
        else:
            # Concat signals into one big DataFrame
            self.signals = pd.DataFrame.from_dict({signal.column_name: signal.data for signal in signals})
        self.label_keys = label_keys

        self.X_scaler = X_scaler or StandardScaler()   # RobustScaler()?
        self.y_scaler = y_scaler or StandardScaler()

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
        features = features.dropna()
        date = (~features.isna()).any(axis=1).index

        # Split into X, y
        X = features[features.columns.difference(self.label_keys)] \
                .to_numpy()
        y = features[self.label_keys].to_numpy()

        # Scale
        X = self.X_scaler.fit_transform(X)
        y = self.y_scaler.fit_transform(y)[:, 0]
        return X, y, date

    @staticmethod
    def concat(a, b):
        # TODO take a list[SignalSet] as argument
        # XXX HOW TO HANDLE DATE? Combine?
        df = pd.merge(a.signals, b.signals, left_index=True, right_index=True)  # pd.concat([a.signals, b.signals])
        df.sort_index(inplace=True)
        return SignalSet(df.index, df, set(a.label_keys + b.label_keys))

    def __repr__(self):
        # TODO TMP
        return self.signals.__repr__()

    def __str__(self):
        # TODO TMP
        return str(self.signals)


class Signal:
    def __init__(self, column_name, data, one_hot=False):
        """
        column_name : str
        data : pd.Series
        one_hot : bool (not yet functional)
        scaling : "standard" | "minmax" | None (not yet functional)
        """
        self.column_name = column_name
        self.data = data
        self.one_hot = one_hot

    def as_series(self):
        return self.data

    def __getitem__(self, indices):
        if not isinstance(indices, str):
            raise Exception('Signal.__getitem__: only str key implemented')

        return Signal(self.column_name + '[' + indices + ']', self.data[indices])

    def __repr__(self):
        return self.column_name + ':\n' + self.data.__repr__()

    def __str__(self):
        return self.column_name + ':\n' + str(self.data)


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
