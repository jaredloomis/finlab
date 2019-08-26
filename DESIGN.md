# Marketplace Analysis Design

## High-level Pipeline

- Pull `text` from the page using selenium
- Parse `html` from raw text using jsoup
- Parse `RawPosting` from html
- Match posting to a known `Product` in db, or create new entry
- Create `ProductPosting` - aforementioned product, plus price

**Implementation**

- Marketplace  `: () -> ProductPosting`
  - PageFetcher `: () -> String` *Currently in Marketplace*
  - Jsoup      `: String -> <Document>` *Currently in Marketplace*
  - PostParser `: <Document> -> RawPosting`
  - ProductDB  `: RawPosting -> Product`
  - Implicit from previous info `Product -> (price: String) -> ProductPosting`