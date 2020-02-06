# Product Marketplace Analysis Design

## Data Extraction Pipeline

- Pull `text` from the page using selenium.
- Parse `html` from raw text using jsoup.
- Parse `RawPosting` from html.
- Match posting to a known `Product` in db, or create new entry.
- Create `ProductPosting` - aforementioned product, plus price.

**Implementation**

- Marketplace  `: () -> ProductPosting`.
  - PageFetcher `: () -> String` *Currently in Marketplace*.
  - Jsoup      `: String -> Document` *Currently in Marketplace*.
  - PostParser `: <Document> -> RawPosting` *Currently in Marketplace*
  - ProductDB  `: RawPosting -> [Product]` pull potential matches from db.
  - ProductRecognition `: RawPosting -> [Product] -> Product` choose most relevant option given by db.

# Forex Marketplace Analysis Design

## Data Extraction Pipeline

- Pull `text` from the page using selenium.
- Parse `CurrencyConversion` from text.

## Margin Analysis

- Exhaustively parse `CurrencyConversion`s from a variety of forex markets.
- Given a maximum number of conversions to chain (1 may be optimal), determine if there is an
  opportunity to buy currency X on forex market A, and sell it on forex market B for a better price.
