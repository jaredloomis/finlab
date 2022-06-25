-- Removes doubled brand names like 'AppleApple'
UPDATE products
SET brand = substring(brand, 0, char_length(brand))
WHERE brand ~ '([A-Z][A-Za-z\- ]*)\1';
