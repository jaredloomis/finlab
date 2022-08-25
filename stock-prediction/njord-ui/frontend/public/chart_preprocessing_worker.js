const ACTIONS = {
  preprocessCandlesticks
};

self.onmessage = event => {
  try {
    console.log("EVENT", event);
    if(event.data && event.data.action) {
      console.log("RUNNING", event.data.action);
      const ret = ACTIONS[event.data.action](event.data);
      console.log("RET", ret);
      self.postMessage(ret);
    }
  } catch(ex) {
    console.log(ex);
    self.postMessage(ex);
  }
}

function preprocessCandlesticks({ candlesticks }) {
  for(const ticker of Object.keys(candlesticks)) {
    const candle = candlesticks[ticker];
    const newDate = candle["date"] && candle["date"].map(d => new Date(d));
    candlesticks[ticker] = {...candle, date: newDate};
  }
  console.log(candlesticks);
  return candlesticks;
}
