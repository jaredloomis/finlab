package com.jaredloomis.analmark

import com.jaredloomis.analmark.db.DummyProductDBModel
import com.jaredloomis.analmark.legacy.ProductDB
import com.jaredloomis.analmark.model.product.RawPosting
import com.jaredloomis.analmark.scrape.product.ProductMarketType
import com.jaredloomis.analmark.scrape.product.SeleniumProductMarket
import com.jaredloomis.analmark.scrape.product.createMarket
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.random.Random

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductMarketTest {
  var marketCounter: Int = Random.nextInt(100)
  val maxBatchSize: Long = Random.nextLong(0, 20)
  val productDB = DummyProductDBModel()
  val market1 = randomMarket(productDB)
  val market2 = randomMarket(productDB)

  @BeforeEach
  fun setup() {
    market1.init()
    market1.headless = false

    market2.init()
    market2.headless = false
  }

  @AfterEach
  fun teardown() {
    market1.quit()
    market2.quit()
  }

  @Test
  fun fetchProductsFromTwoMarketplaces() {
    val batchCount = 4
    val posts: MutableList<RawPosting> = ArrayList()
    var failCount = 0

    market1.navigateToRandomProductList()
    repeat(batchCount) {
      val batch = market1.fetchProductBatch(maxSize = maxBatchSize)
      val batchSize = batch.size.toLong()
      assert(batchSize <= maxBatchSize) { "Batch size should be <= $maxBatchSize. $batchSize. Batch no.$it" }
      if (batchSize < 0) {
        ++failCount
      }
      posts.addAll(batch)
    }
    assert(failCount < batchCount / 4) { "Market successfully fetches a product batch >3/4 of the time $failCount / $batchCount" }

    val postsSet = HashSet<RawPosting>()
    // Assert posts are not duplicated
    posts.forEach { post ->
      assert(!postsSet.contains(post)) { "posts should be unique. ${market1.type} $post $postsSet" }
      postsSet.add(post)
    }
    // Assert post titles are not empty, and have valid price
    posts.forEach { post ->
      assert(post.title.isNotEmpty()) { "post titles should not be empty" }
      assert(post.price.pennies > 0) { "post prices should not be negative" }
    }
  }

  private fun randomMarket(productDB: ProductDB): SeleniumProductMarket {
    return createMarket(ProductMarketType.EBAY, productDB) as SeleniumProductMarket
    /*
    val type = when(marketCounter++ % 4) {
      0    -> MarketType.CRAIGSLIST
      1    -> MarketType.EBAY
      2    -> MarketType.OVERSTOCK
      3    -> MarketType.NEWEGG
      else -> MarketType.CRAIGSLIST
    }
    return createMarket(type, productDB)
     */
  }
}