import ta

from signals import Signal


# XXX NEED TO REMOVE DATA MEMBER FROM `Signal` before implementing this.
# Otherwise API will be cumbersome and not as performant.
# Might as well do a full refactor before this.


def signal_specs_to_signal_set(specs):
    signals = [signal_spec_to_signal(spec) for spec in specs]


def signal_spec_to_signal(spec):
    return SIGNALS[spec['id']]({key: spec[key] for key in spec if key != 'id'})


SIGNALS = {
    'rsi': lambda spec, data:
        Signal("rsi14", ta.momentum.RSIIndicator(data["close"], window=14).rsi())
}
