package com.jaredloomis.silk

import com.jaredloomis.silk.model.product.Brand
import com.jaredloomis.silk.model.product.Product
import com.jaredloomis.silk.model.product.ProductPosting
import org.kodein.di.instance
import java.util.concurrent.*
import java.util.logging.Logger
import kotlin.system.exitProcess
import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import com.jaredloomis.silk.db.*
import com.jaredloomis.silk.model.SimpleProductMatcher
import com.jaredloomis.silk.scrape.product.*
import com.jaredloomis.silk.scrape.realestate.ZillowScraper
import java.time.Duration
import java.time.Instant

fun main(args: Array<String>) = Silk().subcommands(MarginCheck(), ScrapeRandom(), PriceUpdates(), Test()).main(args)

class Silk : CliktCommand(help = "...") {
  override fun run() {}
}

class Test : CliktCommand() {
  val executor by di.instance<ExecutorService>()

  override fun run() {
    val scraper = ZillowScraper()
    scraper.headless = false
    scraper.open()
    Runtime.getRuntime().addShutdownHook(Thread { scraper.close() })
    scraper.search("Incline Village, NV").forEachRemaining {
      println(it)
    }
    /*
    val startTime = Instant.now()
    val scraper1 = WalmartProductScraper()
    scraper1.headless = false
    scraper1.open()
    Runtime.getRuntime().addShutdownHook(Thread { scraper1.close() })
    val supplier = scraper1.randomProductList()
    repeat(10) {
      println(supplier.next())
    }
    val endTime = Instant.now()
    println("\n\nTook ${Duration.between(startTime, endTime).toMillis() / 10}ms per product")
    scraper1.close()
     */
  }
}

class PriceUpdates : CliktCommand() {
  val market by option().enum<ProductMarketType>().default(ProductMarketType.EBAY)
  val showBrowser by option().flag()

  val productTable by di.instance<Table<Product>>()
  val postingTable by di.instance<Table<ProductPosting>>()

  override fun run() {
    val matcher = SimpleProductMatcher()
    val marketScraper = createMarket(market) as SeleniumProductMarket
    marketScraper.headless = !showBrowser
    marketScraper.init()

    postingTable.query("SELECT * FROM ${postingTable.tableName}").forEach { prodPost ->
      val prod = prodPost.product
      marketScraper.search(prod.upc ?: "${prod.primaryBrand.name} ${prod.modelID ?: ""}")
      marketScraper.fetchProductBatch(5).forEach { post ->
        val product: Product? = matcher.matches(post).findAny().orElse(null)
        if(product != null) {
          postingTable.insert(ProductPosting(product, post))
        }
      }
    }

    marketScraper.quit()
  }
}

class MarginCheck : CliktCommand() {
  val postingTable by di.instance<Table<ProductPosting>>()

  override fun run() {
    postingTable.query("SELECT * FROM ${postingTable.tableName}").flatMap { refPost ->
      postingTable.query("SELECT * FROM ${postingTable.tableName} WHERE product = %product.id", refPost)
        .filter { it.posting.price != refPost.posting.price }
        .map { Pair(refPost, it) }
    }
    .forEach {
      println("Buy/Sell Opportunity for ${it.first.product.prettyName}:")
      println("${it.first.posting.price} ${it.first.posting.url} (id: ${it.first.id})")
      println("${it.second.posting.price} ${it.second.posting.url} (id: ${it.second.id})")
      println("Margin: ${(it.first.posting.price - it.second.posting.price).abs()} (${(it.first.posting.price - it.second.posting.price).abs().pennies.toDouble() / minOf(it.first.posting.price, it.second.posting.price).pennies.toDouble() * 100}%)")
      println("\n")
    }
  }
}

class ScrapeRandom : CliktCommand() {
  val batchCount by option().int().default(Int.MAX_VALUE).help("Number of batches of posts to scrape")
  val batchSize by option().long().default(10L).help("Number of posts per batch")
  val showBrowser by option().flag()
  val market by option().enum<ProductMarketType>().default(ProductMarketType.NEWEGG)

  val productTable by di.instance<Table<Product>>()
  val postingTable by di.instance<Table<ProductPosting>>()

  val executor by di.instance<ExecutorService>()

  override fun run() {
    val matcher = SimpleProductMatcher()
    val postQueue = LinkedBlockingQueue<ProductPosting>()
    val logger = Logger.getLogger("Main")
    productTable.open()
    postingTable.open()

    val buyMarket = createMarket(market) as SeleniumProductMarket
    buyMarket.headless = !showBrowser
    buyMarket.init()

    executor.submit {
      while(!executor.isShutdown) {
        val post = postQueue.take()
        postingTable.insert(post)
      }
    }

    val shutdown = {
      buyMarket.quit()
      executor.shutdown()
      productTable.close()
      postingTable.close()
      ProcessBuilder(listOf("bash", "./scripts/kill-hanging-processes")).start().waitFor(3, TimeUnit.SECONDS)
      Unit
    }

    // Add shutdown hook (called when Ctrl-c)
    Runtime.getRuntime().addShutdownHook(Thread(shutdown))

    try {
      repeat(batchCount) {
        // Get a batch of posts
        buyMarket.navigateToRandomProductList()
        val buyBatch = buyMarket.fetchProductBatch(maxSize=batchSize)
        logger.info("BATCH: $buyBatch")
        // For each post with a valid product key
        buyBatch.forEach { rawPost ->
          if (rawPost.productKey != null) {
            // Find any matching Products in db, or create a new product
            val match = matcher.matches(rawPost).findAny().orElse(null)
            val product = if(match != null) {
                match
              } else {
                val p = Product(rawPost.model ?: "SEE UPC", Brand(rawPost.brand ?: "SEE UPC"))
                p.modelID = rawPost.model?.replace(p.primaryBrand.name, "")
                p.upc = rawPost.upc
                p
              }
            // Send post to processing queue
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
    exitProcess(0)
  }
}
