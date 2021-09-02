package com.jaredloomis.silk

import com.jaredloomis.silk.model.product.Brand
import com.jaredloomis.silk.model.product.Product
import com.jaredloomis.silk.model.product.ProductPosting
import com.jaredloomis.silk.scrape.product.SeleniumProductMarket
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
import com.jaredloomis.silk.model.CurrencyAmount
import com.jaredloomis.silk.model.SimpleProductMatcher
import com.jaredloomis.silk.model.product.RawPosting
import com.jaredloomis.silk.scrape.product.ProductMarketType
import com.jaredloomis.silk.scrape.product.createMarket
import kotlin.reflect.full.memberProperties

fun main(args: Array<String>) = Silk().subcommands(MarginCheck(), ScrapeRandom(), PriceUpdates(), Test()).main(args)

class Silk : CliktCommand(help = "...") {
  override fun run() {}
}

class Test : CliktCommand() {
  override fun run() {
    val post = RawPosting(ProductMarketType.EBAY, "ebay.com", "Hello, world", "wasabi", CurrencyAmount(123), mapOf(
      Pair("upc", "12345"),
      Pair("brand", "mac"),
      Pair("model", "helo")
    ))
    val map = reflectToMap(post)
    println(map)
  }
}

class PriceUpdates : CliktCommand() {
  val market by option().enum<ProductMarketType>().default(ProductMarketType.EBAY)
  val showBrowser by option().flag()

  val productDB by di.instance<PostgresProductDBModel>()
  val postingDB by di.instance<PostgresPostingDBModel>()

  val productTable by di.instance<Table<Product>>()

  override fun run() {
    val matcher = SimpleProductMatcher()
    val marketScraper = createMarket(market, productDB) as SeleniumProductMarket
    marketScraper.headless = !showBrowser
    marketScraper.init()

    postingDB.all().forEach { prodPost ->
      val prod = prodPost.product
      marketScraper.search(prod.upc ?: "${prod.primaryBrand} ${prod.modelID}")
      marketScraper.fetchProductBatch(5).forEach { post ->
        val product: Product? = matcher.matches(post).findAny().orElse(null)
        if(product != null) {
          println("IT FUCKIN WARKS!!!!\n\n\n\n\n\n")
          postingDB.insert(ProductPosting(product, post))
        }
      }
    }

    marketScraper.quit()
  }
}

class MarginCheck : CliktCommand() {
  val postingDB by di.instance<PostgresPostingDBModel>()

  override fun run() {
    postingDB.all().flatMap { refPost ->
      postingDB.find(refPost.product)
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
  val market by option().enum<ProductMarketType>().default(ProductMarketType.EBAY)

  val productDB by di.instance<PostgresProductDBModel>()
  val postingDB by di.instance<PostgresPostingDBModel>()

  val executor by di.instance<ExecutorService>()

  override fun run() {
    val postQueue = LinkedBlockingQueue<ProductPosting>()
    val logger = Logger.getLogger("Main")
    productDB.init()
    postingDB.init()

    val buyMarket = createMarket(market, productDB) as SeleniumProductMarket
    buyMarket.headless = !showBrowser
    buyMarket.init()

    executor.submit {
      while(!executor.isShutdown) {
        val post = postQueue.take()
        postingDB.insert(post)
      }
    }

    val shutdown = {
      buyMarket.quit()
      executor.shutdown()
      productDB.close()
      postingDB.close()
      ProcessBuilder(listOf("bash", "./scripts/kill-hanging-processes")).start().waitFor(3, TimeUnit.SECONDS)
      Unit
    }

    // Add shutdown hook (called when Ctrl-c)
    Runtime.getRuntime().addShutdownHook(Thread(shutdown))

    try {
      repeat(batchCount) {
        buyMarket.navigateToRandomProductList()
        val buyBatch = buyMarket.fetchProductBatch(maxSize=batchSize)
        logger.info("BATCH: $buyBatch")
        buyBatch.forEach { rawPost ->
          if (rawPost.productID != null) {
            val product = Product(rawPost.model ?: "SEE UPC", Brand(rawPost.brand ?: "SEE UPC"))
            product.modelID = rawPost.model?.replace(product.primaryBrand.name, "")
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
    exitProcess(0)
  }
}
