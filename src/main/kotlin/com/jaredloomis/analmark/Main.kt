package com.jaredloomis.analmark

import com.jaredloomis.analmark.db.PostgresProductDBModel
import com.jaredloomis.analmark.model.CurrencyAmount
import com.jaredloomis.analmark.model.EbayRawPosting
import com.jaredloomis.analmark.model.RawPosting
import com.jaredloomis.analmark.scrape.Craigslist
import com.jaredloomis.analmark.scrape.EBay
import com.jaredloomis.analmark.scrape.SeleniumMarket
import java.util.stream.Collectors

fun main() {
  val query = "adidas"
  val batchCount = 3
  val buyPosts: MutableList<RawPosting> = ArrayList()

  val productDB = PostgresProductDBModel()

  val buyMarket: SeleniumMarket = EBay(productDB)
  buyMarket.headless = false
  val sellMarket: SeleniumMarket = Craigslist(productDB)
  sellMarket.headless = false

  //productDB.addProduct(Product("wasabi", Brand("Costco")))
  val matches = productDB.find(EbayRawPosting("", "Costco", "high quality", CurrencyAmount("$200.00"), emptyMap())).collect(Collectors.toList())
  print("MATCHES: $matches")

  /*
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
  }*/
}