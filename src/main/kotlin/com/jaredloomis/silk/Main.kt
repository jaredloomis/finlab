package com.jaredloomis.silk

import com.jaredloomis.silk.db.PostgresPostingDBModel
import com.jaredloomis.silk.db.PostgresProductDBModel
import com.jaredloomis.silk.db.dbModelModule
import com.jaredloomis.silk.model.product.Brand
import com.jaredloomis.silk.model.product.Product
import com.jaredloomis.silk.model.product.ProductPosting
import com.jaredloomis.silk.scrape.product.EBay
import com.jaredloomis.silk.scrape.product.SeleniumProductMarket
import org.kodein.di.DI
import org.kodein.di.instance
import java.util.concurrent.*
import java.util.logging.Logger
import java.util.stream.Collectors
import java.util.stream.Stream
import kotlin.system.exitProcess

val di = DI {
  import(dbModelModule)
}

val productDB by di.instance<PostgresProductDBModel>()
val postingDB by di.instance<PostgresPostingDBModel>()

fun main() {
  val postQueue = LinkedBlockingQueue<ProductPosting>()
  val logger = Logger.getLogger("Main")
  productDB.init()
  postingDB.init()

  val buyMarket: SeleniumProductMarket = EBay(productDB)
  //buyMarket.headless = false
  buyMarket.backend = "chrome"
  buyMarket.init()

  val shutdown = {
    buyMarket.quit()
    productDB.close()
    postingDB.close()
    ProcessBuilder(listOf("bash", "./scripts/kill-hanging-processes")).start().waitFor(3, TimeUnit.SECONDS)
    Unit
  }

  // Add shutdown hook (called when Ctrl-c)
  Runtime.getRuntime().addShutdownHook(Thread(shutdown))

  val executor = Executors.newCachedThreadPool()

  executor.submit {
    while(!executor.isShutdown) {
      val post = postQueue.take()
      val postPrime = postingDB.insert(post)
      checkForBuySell(postPrime).forEach {
        println("FOUND BUY/SELL OPPORTUNITY: $postPrime <--> $it\n\n\n\n\n\n\n\n")
      }
    }
  }

  try {
    val batchCount = 200
    val batchSize = 3L
    repeat(batchCount) {
      buyMarket.navigateToRandomProductList()
      val buyBatch = buyMarket.fetchProductBatch(maxSize=batchSize)
      logger.info("BATCH: $buyBatch")
      buyBatch.forEach { rawPost ->
        if (rawPost.productID != null) {
          println("FOUND VALID PRODUCT\n\n\n\n")
          val product = Product(rawPost.model ?: "SEE UPC", Brand(rawPost.brand ?: "SEE UPC"))
          product.modelID = rawPost.model
          product.upc = rawPost.upc
          val posting = ProductPosting(product, rawPost)
          postQueue.put(posting)
        }
      }
    }
  } catch (ex: Throwable) {
    ex.printStackTrace()
  } finally {
    shutdown()
  }
  executor.shutdown()
  exitProcess(0)
}

private fun checkForBuySell(prodPost: ProductPosting): Stream<ProductPosting> {
  return postingDB.find(prodPost.product)
    .filter { it.posting.price != prodPost.posting.price }
}
