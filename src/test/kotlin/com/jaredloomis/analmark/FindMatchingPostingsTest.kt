package com.jaredloomis.analmark

import com.jaredloomis.analmark.db.DBModel
import com.jaredloomis.analmark.db.PostgresPostingDBModel
import com.jaredloomis.analmark.db.PostgresProductDBModel
import com.jaredloomis.analmark.model.product.Product
import com.jaredloomis.analmark.model.product.ProductPosting
import com.jaredloomis.analmark.model.product.RawPosting
import com.jaredloomis.analmark.nlp.DBCachingProductRecognition
import com.jaredloomis.analmark.view.product.Craigslist
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
  val market = Craigslist(productDB)
  val recognition = DBCachingProductRecognition(productDB, postingDB)

  val productCount = 9999
  val batchCount = 1
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
    val productCount = 20
    val maxIndex = Math.max(0, allProducts.size - productCount)
    val randIndex = if (maxIndex == 0) {
      0
    } else {
      Random.nextInt(0, maxIndex)
    }
    val products = allProducts.subList(0, Math.min(allProducts.size, productCount)) //allProducts.subList(randIndex, Math.min(allProducts.size, randIndex + productCount))

    logger.info("[FindMatchingPosts] matching ${products.size} products from database to postings from ${market.type}")

    val foundProdPosts = products.flatMap { product ->
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
