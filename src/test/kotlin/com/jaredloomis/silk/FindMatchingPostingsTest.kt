package com.jaredloomis.silk

import com.jaredloomis.silk.db.DBModel
import com.jaredloomis.silk.db.PostgresPostingDBModel
import com.jaredloomis.silk.db.ProductTable
import com.jaredloomis.silk.model.product.Product
import com.jaredloomis.silk.model.product.ProductPosting
import com.jaredloomis.silk.model.product.RawPosting
import com.jaredloomis.silk.nlp.DBCachingProductRecognition
import com.jaredloomis.silk.scrape.product.Craigslist
import com.jaredloomis.silk.util.getLogger
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.stream.Collectors

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FindMatchingPostingsTest {
  val productDB: DBModel<RawPosting, Product> = ProductTable()
  val postingDB: DBModel<Product, ProductPosting> = PostgresPostingDBModel(productDB)
  val market = Craigslist(productDB)
  val recognition = DBCachingProductRecognition(productDB, postingDB)

  val maxBatchSize = 999L

  val logger = getLogger(this::class)

  @BeforeAll
  fun init() {
    market.headless = false
    market.init()
  }

  @AfterAll
  fun destroy() {
    market.quit()
  }

  /**
   * Look at products currently in db, and try to find matches in markets
   */
  @Test
  fun findMatchingPostings() {
    val allProducts = productDB.all().collect(Collectors.toList())
    allProducts.shuffle()
    val productCount = 20000
    val batchCount = 1
    val products = allProducts.subList(0, Math.min(allProducts.size, productCount))

    logger.info("[FindMatchingPosts] matching ${products.size} products from database to postings from ${market.type}")

    val foundProdPosts = products.flatMap { product ->
      logger.info("[FindMatchingPosts] matching $product to postings from ${market.type}")
      val posts = ArrayList<RawPosting>()
      market.search("${product.primaryBrand.name} ${product.modelID ?: product.canonicalName}")
      repeat(batchCount) {
        val buyBatch = market.fetchProductBatch(maxBatchSize)
        posts.addAll(buyBatch)
      }

      posts
        .filter { recognition.recognize(it, productDB.find(it)) != null }
        .map { postingDB.insert(ProductPosting(product, it)) }
    }

    logger.info("[FindMatchingPosts] found $foundProdPosts")
  }
}
