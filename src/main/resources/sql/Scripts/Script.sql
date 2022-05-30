update products
set modelid = REPLACE(modelid, brand, '')
where modelid ilike '%' || brand || '%';