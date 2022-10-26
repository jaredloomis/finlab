from typing import Any, Optional


class SignalExpr:
    signal_id: str
    args: dict[str, Any]
    # Select a specific value of output
    # ex. {'id': 'candles_1d', select: 'open'}
    select: Optional[str]

    def __init__(self, signal_id, args, select=None):
        self.signal_id = signal_id
        self.args = args
        self.select = select

    def qualified_id(self):
        ret = self.signal_id
        val_args = [name + '=' + str(val) for name, val in self.args.items() if not isinstance(val, SignalExpr)]
        if len(val_args) > 0:
            ret += '<'
            ret += ','.join(val_args)
            ret += '>'
        sig_args = [name + '=' + val.qualified_id() for name, val in self.args.items() if isinstance(val, SignalExpr)]
        if len(sig_args) > 0:
            ret += '('
            ret += ','.join(sig_args)
            ret += ')'
        if self.select is not None:
            ret += '[' + self.select + ']'
        return ret

    def __str__(self):
        return f'SignalExpr(signal_id={self.signal_id}, args={self.args}, select={self.select})'

    def __repr__(self):
        return str(self)