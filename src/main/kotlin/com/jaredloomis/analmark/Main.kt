package com.jaredloomis.analmark

import com.jaredloomis.analmark.db.PostgresPostingDBModel
import com.jaredloomis.analmark.db.PostgresProductDBModel
import com.jaredloomis.analmark.model.product.Brand
import com.jaredloomis.analmark.model.product.Product
import com.jaredloomis.analmark.model.product.ProductPosting
import com.jaredloomis.analmark.scrape.product.EBay
import com.jaredloomis.analmark.scrape.product.SeleniumProductMarket
import java.util.concurrent.TimeUnit
import java.util.logging.Logger
import kotlin.system.exitProcess

fun main() {
  val logger = Logger.getLogger("Main")
  val productDB = PostgresProductDBModel()
  productDB.init()
  val postingDB = PostgresPostingDBModel(productDB)
  postingDB.init()

  val buyMarket: SeleniumProductMarket = EBay(productDB)
  buyMarket.headless = false
  buyMarket.backend = "chrome"
  buyMarket.takeScreenshots = false
  buyMarket.init()

  val shutdown = {
    buyMarket.quit()
    productDB.close()
    postingDB.close()
    ProcessBuilder(listOf("bash", "./scripts/kill-hanging-processes")).start().waitFor(10, TimeUnit.SECONDS)
    Unit
  }

  // Add shutdown hook (called when Ctrl-c)
  Runtime.getRuntime().addShutdownHook(Thread(shutdown))

  try {
    val batchCount = 12
    val batchSize = 40L
    repeat(batchCount) {
      buyMarket.navigateToRandomProductList()
      val buyBatch = buyMarket.fetchProductBatch(maxSize=batchSize)
      logger.info("BATCH: $buyBatch")
      buyBatch.forEach { rawPost ->
        if (rawPost.productID != null) {
          val product = Product(rawPost.model ?: "SEE UPC", Brand(rawPost.brand ?: "SEE UPC"))
          product.modelID = rawPost.model
          product.upc = rawPost.upc
          productDB.insert(product)
          postingDB.insert(ProductPosting(product, rawPost))
        }
      }
    }
  } catch (ex: Throwable) {
    ex.printStackTrace()
  } finally {
    shutdown()
  }
  exitProcess(0)
}