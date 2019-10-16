package com.jaredloomis.analmark

import com.jaredloomis.analmark.db.DBModel
import com.jaredloomis.analmark.db.PostgresPostingDBModel
import com.jaredloomis.analmark.db.PostgresProductDBModel
import com.jaredloomis.analmark.model.Product
import com.jaredloomis.analmark.model.ProductPosting
import com.jaredloomis.analmark.model.RawPosting
import com.jaredloomis.analmark.nlp.DBCachingProductRecognition
import com.jaredloomis.analmark.scrape.*
import com.jaredloomis.analmark.util.getLogger
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.stream.Collectors
import kotlin.random.Random

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FindMatchingPostingsTest {
  val productDB: DBModel<RawPosting, Product> = PostgresProductDBModel()
  val postingDB: DBModel<Product, ProductPosting> = PostgresPostingDBModel(productDB)
  val market   = Craigslist(productDB)
  val recognition = DBCachingProductRecognition(productDB, postingDB)

  val productCount = 9999
  val batchCount = 1
  val maxBatchSize = 999L

  val logger = getLogger(this::class)

  @BeforeAll
  fun init() {
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
    val productCount = 20
    val randIndex = Random.nextInt(0, Math.max(0, allProducts.size - productCount))
    val products = allProducts.subList(randIndex, randIndex + productCount)

    logger.info("[FindMatchingPosts] matching ${products.size} products from database to postings from ${market.type}")

    val foundProdPosts = products.flatMap {product ->
      val posts = ArrayList<RawPosting>()
      market.search("${product.primaryBrand.name} ${product.modelID ?: product.canonicalName}")
      repeat(batchCount) {
        val buyBatch = market.fetchProductBatch(maxBatchSize)
        posts.addAll(buyBatch)
      }

      posts.map {postingDB.insert(ProductPosting(product, it))}
    }

    logger.info("[FindMatchingPosts] found $foundProdPosts")
  }
}
