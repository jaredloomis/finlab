package com.jaredloomis.analmark.main

fun main() {
  val query = "adidas"
  val batchCount = 3
  val buyPosts: MutableList<RawPosting> = ArrayList()

  val productDB = DummyProductDB()
  val brandDB   = DummyBrandDB()

  val buyMarket: SeleniumMarketplace = EBay(productDB, brandDB)
  buyMarket.headless = false
  val sellMarket: SeleniumMarketplace = Craigslist(productDB, brandDB)
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

    sellMarket.init()
    val sellPosts = buyPosts.flatMap {post ->
      sellMarket.search(post.brand?:"" + " " + post.title)
      val sellBatch = sellMarket.fetchProductBatch(maxSize=3)
      sellBatch
    }
    sellMarket.quit()

    println(buyPosts)
    //println(ebPosts)
  } catch (ex: Throwable) {
    ex.printStackTrace()
    buyMarket.quit()
    sellMarket.quit()
  }
}