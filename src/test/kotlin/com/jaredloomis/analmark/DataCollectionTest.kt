package com.jaredloomis.analmark

import com.jaredloomis.analmark.db.DBModel
import com.jaredloomis.analmark.db.PostgresPostingDBModel
import com.jaredloomis.analmark.db.PostgresProductDBModel
import com.jaredloomis.analmark.model.Product
import com.jaredloomis.analmark.model.ProductPosting
import com.jaredloomis.analmark.model.RawPosting
import com.jaredloomis.analmark.nlp.DBCachingProductRecognition
import com.jaredloomis.analmark.scrape.EBay
import com.jaredloomis.analmark.util.getLogger
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataCollectionTest {
  val productDB: DBModel<RawPosting, Product> = PostgresProductDBModel()
  val postingDB: DBModel<Product, ProductPosting> = PostgresPostingDBModel(productDB)
  val buyMarket   = EBay(productDB) //randomMarket(productDB)
  val recognition = DBCachingProductRecognition(productDB, postingDB)
  val maxBatchSize = 999L//5L
  val batchCount = 5
  val logger = getLogger(this::class)

  @BeforeAll
  fun init() {
    //buyMarket.headless = false
    buyMarket.init()
  }

  @AfterAll
  fun destroy() {
    buyMarket.quit()
  }

  @Test
  fun collectData() {
    while(true) {
      val buyPosts = ArrayList<RawPosting>()

      // Populate buyPosts
      buyMarket.navigateToRandomProductList()
      repeat(batchCount) {
        val buyBatch = buyMarket.fetchProductBatch(maxSize = maxBatchSize)
        buyPosts.addAll(buyBatch)
      }

      //assert(buyPosts.isNotEmpty()) {"buyMarket retrieved 1 or more postings."}

      // Create Products from buy Postings, and add postings to db
      val buyProducts = buyPosts
        .mapNotNull {
          val rec = recognition.recognize(it, productDB.find(it))
          rec ?: recognition.create(it)
        }
        .map {postingDB.insert(it)}

      logger.info("Retrieved products $buyProducts")

      //assert(buyProducts.isNotEmpty()) {"1 or more products were created from buyMarket postings. $buyPosts"}
    }
  }
}
