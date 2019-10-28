WITH prod_posts AS
(
SELECT a.*, b.product_name, COUNT(*) OVER(PARTITION BY b.id) cnt
FROM postings a
INNER JOIN products b ON a.product = b.id
)
SELECT * FROM prod_posts
WHERE cnt > 1 -- AND markets are different
ORDER BY cnt DESC;