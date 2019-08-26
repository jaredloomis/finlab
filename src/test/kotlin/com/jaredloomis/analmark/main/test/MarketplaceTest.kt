package com.jaredloomis.analmark.main.test

import org.junit.jupiter.api.*
import com.jaredloomis.analmark.main.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MarketplaceTest {
  @Test
  fun test() {
    val batchCount = 3
    val buyPosts: MutableList<RawPosting> = ArrayList()
    val query = "adidas"

    val productDB = DummyProductDB()

    val buyMarket: SeleniumMarketplace = NewEgg(productDB)
    buyMarket.headless = false
    val sellMarket: SeleniumMarketplace = Craigslist(productDB)
    sellMarket.headless = false

    try {
      buyMarket.init()
      buyMarket.search(query)
      repeat(batchCount) {
        val buyBatch = buyMarket.fetchProductBatch(maxSize=3)
        println("BATCH: $buyBatch")
        buyPosts.addAll(buyBatch)
      }
      buyMarket.quit()
/*
      sellMarket.init()
      val ebPosts = buyPosts.flatMap {post ->
        sellMarket.search(post.product.product)
        val sellBatch = sellMarket.fetchProductBatch(maxSize=3)
        sellBatch
      }
      sellMarket.quit()*/

      println(buyPosts)
      //println(ebPosts)
    } catch (ex: Throwable) {
      ex.printStackTrace()
      buyMarket.quit()
      sellMarket.quit()
    }
  }
}