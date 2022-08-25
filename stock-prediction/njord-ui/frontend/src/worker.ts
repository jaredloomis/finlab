const worker = new Worker("/chart_preprocessing_worker.js");

export function preprocessCandlesticks(candlesticks: any) {
  return new Promise(resolve => {
    worker.onmessage = event => {
        resolve(event.data)
    }
    worker.postMessage({
      action: 'preprocessCandlesticks',
      candlesticks
    });
  });
}
