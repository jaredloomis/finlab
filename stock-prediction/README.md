# Prediction Framework

## Pipeline

- Train new model
  - Get model configuration - input as json
    - `ModelConf`
      - Type flag (pytorch / sklearn)
      - List of feature `Signal`s
      - List of label `Signal`s
      - Model definition code
  - Data Prep
    - Data Sourcing - from network (or keep in sync with scheduled sync programs)
      - Determine what data is needed from `ModelConf`
    - Data Loading - from db
      - Determine what data is needed from `ModelConf`
    - Feature Extraction
      - Technical Signals
    - Deal with NaNs - fill or drop
    - Feature encoding - one-hot, to numpy
    - Shuffle
    - Sample
      - Train/test
  - Model training
    - CV, Grid search hyperparameters
    - Train model, stopping every n epochs to:
      - Generate predictions
      - Generate metrics for model
        - Backtest ROI on various assets / asset classes
    - Save best model as a `TrainedModel`, along with metrics
- Use Njord UI to make manual trades
  - Select models to compare results
  - View model metrics created after training
  - List of High-change assets
  - Configurable watchlist
- Auto-trade with alpaca or similar

## Data Sources

- 14+ day predictions
  - Daily candlesticks from Yahoo
- Intraday predictions
  - 5-min candlesticks from AlphaVantage
    - Rate limit 500 requests per day (1 month of data per request)