UPDATE postings p
SET model = REPLACE(p.model, p.brand, '');