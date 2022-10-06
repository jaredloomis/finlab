import numpy as np
from signals import Signal, SignalSet
import datastore as ds


class FinancialsReportedSignalSet(SignalSet):
    def __init__(self, date, reports):
        def get_concept_on_day(concept, day):
            """
            Get value of specified concept on a specific date.
            TODO faster
            """
            applicable_reports = reports[(reports["startDate"] < day) & (reports["endDate"] > day)]
            if applicable_reports.shape[0] > 0:
                try:
                    value = list(filter(lambda x: x["concept"] == concept, applicable_reports.iloc[0]["report"]["bs"]))[0]["value"]
                    if np.isnan(value):
                        return None
                    else:
                        return value
                except:
                    return np.nan
            else:
                return np.nan

        # Collect a list of all concepts stored in reports
        concepts = set()
        for i in range(0, len(reports.index)):
            concept_batch = map(lambda r: r["concept"], reports.iloc[i]["report"]["bs"])
            concepts.update(concept_batch)

        # Create a signal for each concept
        signals = list(map(lambda concept: Signal(concept, date.apply(lambda d: get_concept_on_day(concept, d))), concepts))

        super().__init__(date, signals, [])

    @staticmethod
    def from_datastore(date, ticker):
        reports = ds.get_financials_reported([ticker], date.iloc[0], date.iloc[-1])[ticker]
        return FinancialsReportedSignalSet(date, reports)
