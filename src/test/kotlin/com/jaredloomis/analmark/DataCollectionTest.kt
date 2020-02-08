package com.jaredloomis.analmark

import com.jaredloomis.analmark.db.DBModel
import com.jaredloomis.analmark.db.PostgresPostingDBModel
import com.jaredloomis.analmark.db.PostgresProductDBModel
import com.jaredloomis.analmark.model.product.Product
import com.jaredloomis.analmark.model.product.ProductPosting
import com.jaredloomis.analmark.model.product.RawPosting
import com.jaredloomis.analmark.nlp.DBCachingProductRecognition
import com.jaredloomis.analmark.view.product.EBay
import com.jaredloomis.analmark.util.getLogger
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataCollectionTest {
  val productDB: DBModel<RawPosting, Product> = PostgresProductDBModel()
  val postingDB: DBModel<Product, ProductPosting> = PostgresPostingDBModel(productDB)
  val market = EBay(productDB) //randomMarket(productDB)
  val recognition = DBCachingProductRecognition(productDB, postingDB)
  val maxBatchSize = 999L//5L
  val batchCount = 5
  val logger = getLogger(this::class)

  @BeforeAll
  fun init() {
    market.init()
  }

  @AfterAll
  fun destroy() {
    market.quit()
  }

  @Test
  fun collectData() {
    while (true) {
      // Go to a random product list
      market.navigateToRandomProductList()

      // Send n batches of posting through the recognition -> db pipeline
      repeat(batchCount) {
        // Fetch a batch of postings
        val posts = market.fetchProductBatch(maxSize = maxBatchSize)

        // Create products from postings, add both to db
        val products = posts
          .mapNotNull {
            val rec = recognition.recognize(it, productDB.find(it))
            rec ?: recognition.create(it)
          }
          .map {
            if (it.product.category == null) {
              logger.info("Product category is null")
            }
            postingDB.insert(it)
          }

        logger.info("Retrieved product batch $products")
      }
    }
  }
}
