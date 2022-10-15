import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
import random
from datetime import datetime

from model import Model

import datastore as ds


class TrainingConfig:
    timerange: (datetime, datetime)
    tickers: set[str]


class TrainingMetric:
    pass


class TrainingResult:
    config: TrainingConfig
    metrics: list[TrainingMetric]


def train(model: Model, config: TrainingConfig) -> TrainingResult:
    return None
