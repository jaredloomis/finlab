CREATE TABLE IF NOT EXISTS postings (
  id          SERIAL PRIMARY KEY,
  market      VARCHAR(10) NOT NULL,
  url         TEXT NOT NULL,
  product     BIGINT REFERENCES products(id) NOT NULL,
  title       TEXT NOT NULL,
  price       BIGINT NOT NULL,
  description TEXT,
  specs       TEXT,
  seen        TIMESTAMP,
  UNIQUE (title, seen, market)
);
