CREATE TABLE IF NOT EXISTS real_estate_listings (
  id            SERIAL PRIMARY KEY,
  url           TEXT NOT NULL,
  address       TEXT NOT NULL,
  price         BIGINT NOT NULL,
  propertyType  TEXT,
  beds          SMALLINT,
  baths         SMALLINT,
  squareFootage INTEGER,
  images        TEXT,
  listedDate    DATE,
  attrs         TEXT
);
