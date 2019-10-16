package com.jaredloomis.analmark

import com.jaredloomis.analmark.db.DBModel
import com.jaredloomis.analmark.db.PostgresPostingDBModel
import com.jaredloomis.analmark.db.PostgresProductDBModel
import com.jaredloomis.analmark.legacy.ProductDB
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
import kotlin.math.abs
import kotlin.random.Random

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RecognizeFromMarketTest {
  var marketCounter: Int = Random.nextInt(100)
  val productDB: DBModel<RawPosting, Product> = PostgresProductDBModel()
  val postingDB: DBModel<Product, ProductPosting> = PostgresPostingDBModel(productDB)
  val buyMarket   = EBay(productDB) //randomMarket(productDB)
  val sellMarket  = Craigslist(productDB) //randomMarket(productDB)
  val recognition = DBCachingProductRecognition(productDB, postingDB)
  val maxBatchSize = 5L
  val batchCount = 2

  val logger = getLogger(this::class)

  @BeforeAll
  fun init() {
    //buyMarket.headless = false
    sellMarket.headless = false
    buyMarket.init()
    sellMarket.init()
  }

  @AfterAll
  fun destroy() {
    buyMarket.quit()
    sellMarket.quit()
  }

  @Test
  fun fetchSellPostsMatchToBuyPosts() {
    val buyPosts  = ArrayList<RawPosting>()
    val sellPosts = ArrayList<RawPosting>()

    // Populate buyPosts
    buyMarket.search("shoes") //buyMarket.navigateToRandomProductList()
    repeat(batchCount) {
      val buyBatch = buyMarket.fetchProductBatch(maxSize=maxBatchSize)
      buyPosts.addAll(buyBatch)
    }
    buyMarket.quit()

    logger.info("Posts (check if they have categories) $buyPosts")
    logger.info("Successfully parsed ${buyPosts.size} posts")

    assert(buyPosts.isNotEmpty()) {"buyMarket retrieved 1 or more postings."}

    // Create Products from buy Postings, and add postings to db
    val buyProducts = buyPosts
      .mapNotNull {
        val rec = recognition.recognize(it, productDB.find(it))
        rec ?: recognition.create(it)
      }
      .map {postingDB.insert(it)}

    logger.info("Products (check if they have categories) $buyProducts")
    logger.info("Successfully parsed ${buyProducts.size} products")

    assert(buyProducts.isNotEmpty()) {"1 or more products were created from buyMarket postings. $buyPosts"}

    // Search for the products in db on sell market
    buyProducts.forEach {productPosting ->
      val product = productPosting.product
      sellMarket.search("${product.primaryBrand.name} ${product.modelID ?: product.canonicalName}")
      val sellBatch = sellMarket.fetchProductBatch(maxSize=maxBatchSize)
      sellPosts.addAll(sellBatch)
    }
    sellMarket.quit()

    assert(sellPosts.isNotEmpty()) {"sellMarket retrieved 1 or more postings."}

    // Look in productDB for matches for sellPosts
    val sellProducts = sellPosts
      .mapNotNull {post ->
        val matches = productDB.find(post)
        recognition.recognize(post, matches)
      }
      .map {postingDB.insert(it)}

    assert(sellProducts.isNotEmpty()) {"At least one of the posts found on buy site resulted in a query result on sell site."}

    val matchedPosts = sellProducts
      .mapNotNull {productPosting ->
        val match = buyProducts.find {it.product == productPosting.product}
        if(match != null) {
          Pair(productPosting.posting, match.posting)
        } else {
          null
        }
      }

    assert(sellProducts.isNotEmpty()) {"At least one of the posts found on buy site was matched to a post on sell site."}

    matchedPosts
      .sortedBy {productPostingPair ->
        abs(productPostingPair.first.price.pennies - productPostingPair.second.price.pennies)
      }
      .forEach {productPostingPair ->
        println("Pair(")
        println("${productPostingPair.first} ,")
        println(productPostingPair.second)
        println(")")
      }
  }

  private fun randomMarket(productDB: ProductDB): Market {
    val type = when(marketCounter++ % 2) {
      0    -> MarketType.EBAY
      1    -> MarketType.CRAIGSLIST
      2    -> MarketType.OVERSTOCK
      3    -> MarketType.NEWEGG
      else -> MarketType.CRAIGSLIST
    }
    val market = createMarket(type, productDB) as SeleniumMarket
    market.headless = false
    return market
  }
}
