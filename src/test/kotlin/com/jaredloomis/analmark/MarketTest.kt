package com.jaredloomis.analmark

import com.jaredloomis.analmark.db.DummyProductDBModel
import com.jaredloomis.analmark.legacy.ProductDB
import com.jaredloomis.analmark.model.RawPosting
import com.jaredloomis.analmark.scrape.Market
import com.jaredloomis.analmark.scrape.MarketType
import com.jaredloomis.analmark.scrape.SeleniumMarket
import com.jaredloomis.analmark.scrape.createMarket
import org.junit.jupiter.api.*
import kotlin.random.Random

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MarketTest {
  var marketCounter: Int = Random.nextInt(100)
  val maxBatchSize: Long = 3

  @Test
  fun fetchProductsFromTwoMarketplaces() {
    val query = "sony"
    val batchCount = 2
    val buyPosts: MutableList<RawPosting> = ArrayList()

    val productDB = DummyProductDBModel()//DummyProductDB(DummyBrandDB())

    val buyMarket: SeleniumMarket = randomMarket(productDB) as SeleniumMarket
    buyMarket.headless = false
    val sellMarket: SeleniumMarket = randomMarket(productDB) as SeleniumMarket
    sellMarket.headless = false
    println("Markets: ${buyMarket.type} --> ${sellMarket.type}")

    buyMarket.init()
    buyMarket.search(query)
    repeat(batchCount) {
      val buyBatch = buyMarket.fetchProductBatch(maxSize=maxBatchSize)
      val batchSize = buyBatch.size.toLong()
      assert(batchSize == maxBatchSize) {"Batch size should be $maxBatchSize. $batchSize"}
      buyPosts.addAll(buyBatch)
    }
    buyMarket.quit()

    sellMarket.init()
    val sellPosts = buyPosts.flatMap {post ->
      sellMarket.search(post.brand?:"" + " " + post.title)
      val sellBatch = sellMarket.fetchProductBatch(maxSize=maxBatchSize)
      val batchSize = sellBatch.size.toLong()
      assert(batchSize <= maxBatchSize) {"Batch size should be less than $maxBatchSize. $batchSize"}
      sellBatch
    }
    sellMarket.quit()

    val allPosts = HashSet<RawPosting>()
    // Assert posts are not duplicated
    buyPosts.forEach {post ->
      assert(!allPosts.contains(post)) {"posts should be unique. ${buyMarket.type} $post $allPosts"}
      allPosts.add(post)
    }
    val sellPostsSet = HashSet<RawPosting>()
    sellPosts.forEach {post ->
      assert(!sellPostsSet.contains(post)) {"posts should be unique. ${sellMarket.type} $post $sellPostsSet"}
      sellPostsSet.add(post)
      allPosts.add(post)
    }
    // Assert post titles are not empty, and have valid price
    allPosts.forEach {post ->
      assert(post.title.isNotEmpty()) {"post titles should not be empty"}
      assert(post.price.pennies > 0) {"post prices should not be negative"}
    }
  }

  private fun randomMarket(productDB: ProductDB): Market {
    println(marketCounter)
    val type = when(marketCounter++ % 4) {
      0    -> MarketType.CRAIGSLIST
      1    -> MarketType.EBAY
      2    -> MarketType.OVERSTOCK
      3    -> MarketType.NEWEGG
      else -> MarketType.CRAIGSLIST
    }
    return createMarket(type, productDB)
  }
}