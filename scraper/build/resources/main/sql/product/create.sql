CREATE TABLE IF NOT EXISTS products (
  id           SERIAL PRIMARY KEY,
  product_name TEXT UNIQUE NOT NULL,
  brand        TEXT NOT NULL,
  modelID      TEXT,
  upc          TEXT,
  category     TEXT
);