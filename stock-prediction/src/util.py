import traceback
import datetime
import json
import dateutil.parser

def print_exception(ex):
    traceback.print_exception(type(ex), ex, ex.__traceback__)

def normalize_datetime(date):
    if isinstance(date, str):
        return dateutil.parser.isoparse(date)
    elif isinstance(date, datetime.date):
        return datetime.datetime.combine(date, datetime.time.min)
    else:
        return date

class DateTimeEncoder(json.JSONEncoder):
    def default(self, z):
        if isinstance(z, datetime.datetime):
            return (str(z))
        else:
            return super().default(z)
