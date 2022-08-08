from sklearn.model_selection import train_test_split

from technical_signals import TechnicalSignals

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