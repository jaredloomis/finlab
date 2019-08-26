package com.jaredloomis.analmark.main

import kotlin.collections.HashSet

interface ProductDB {
  fun addProduct(product: Product)
  fun findMatch(search: String): Product?
}

class DummyProductDB: ProductDB {
  var products: Set<Product> = HashSet()

  override fun addProduct(product: Product) {
    products = products.plus(product)
  }

  override fun findMatch(search: String): Product? {
    val searchTokens = tokenize(search)
    var maxScore = 0
    var bestMatch: Product? = null

    for(product in products) {
      val productTokens = tokenize(product.brand + " " + product.product)
      // Score = number of tokens which are common across both search product and search
      val score = productTokens.filter {
        searchTokens.contains(it)
      }.size

      if(score > maxScore) {
        maxScore  = score
        bestMatch = product
      }
    }

    return bestMatch
  }
}

/*
class PostgresProductDB {
  fun addProduct(product: Product) {

  }

  fun findMatch(title: String): Product? {

  }
}
*/