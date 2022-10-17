from typing import Optional

import numpy as np
import traceback
import datetime
import re
import json
import dateutil.parser

TimeRange = (datetime.datetime, datetime.datetime)


def today():
    return datetime.datetime.now().date()


def parse_interval(interval: str) -> (datetime.timedelta, str):
    """
    Parse an interval string, and get the timedelta plus the normalized string.
    """
    m = re.search(r"[a-zA-Z]", interval)
    if m:
        ix = m.start()
        num = int(interval[:ix])
        unit = interval[ix:].lower()
        if unit.startswith('m'):
            return datetime.timedelta(minutes=num), f'{num}min'
        elif unit.startswith('h'):
            return datetime.timedelta(hours=num), f'{num}hour'
        elif unit.startswith('d'):
            return datetime.timedelta(days=num), f'{num}day'
    else:
        raise f'util.parse_interval: couldn\'t parse interval string: {interval}'


def round_batch_size(sample_count, approximately, leeway=None):
    """
    Round batch size to a more suitable value. This helps to avoid a
    problem where the final batch has a lot of samples, but not enough for
    a full batch, leading to many samples being thrown out.

    approximately: int, leeway: int
      decide on a chunk size around a number, with specified leeway
      (leeway defaults to `approximately // 10`).
    """
    if leeway is None:
        leeway = approximately // 10
    
    # Get the number of leftover samples if we use the suggested batch size
    best_chunk_count = approximately
    best_leftover = sample_count - np.floor(sample_count / approximately) * approximately

    # Brute-force search for the value that yields the fewest leftovers
    # within the given leeway range.
    for offset in range(-leeway, leeway):
        chunk_size = approximately + offset
        leftover = sample_count - np.floor(sample_count / chunk_size) * chunk_size
        if leftover < best_leftover:
            best_leftover = leftover
            best_chunk_count = chunk_size
    return best_chunk_count


def print_exception(ex):
    traceback.print_exception(type(ex), ex, ex.__traceback__)


def normalize_datetime(date):
    if isinstance(date, str):
        return dateutil.parser.isoparse(date)
    elif isinstance(date, datetime.date):
        return datetime.datetime.combine(date, datetime.datetime.min.time())
    elif isinstance(date, datetime.datetime):
        return date
    else:
        raise f"Unrecognized datetime obj: {date}"


class DateTimeEncoder(json.JSONEncoder):
    def default(self, z):
        if isinstance(z, datetime.datetime):
            return str(z)
        else:
            return super().default(z)
