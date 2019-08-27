package com.jaredloomis.analmark.main

import kotlin.collections.HashSet

interface ProductDB {
  fun addProduct(product: Product)
  fun findMatches(post: RawPosting, brand: Brand?=null): List<Product>
}

class DummyProductDB: ProductDB {
  var products: Set<Product> = HashSet()

  override fun addProduct(product: Product) {
    products = products.plus(product)
  }

  // TODO return matches in order, instead of just best match
  override fun findMatches(post: RawPosting, brand: Brand?): List<Product> {
    val searchTokens = tokenize(post.title)
    var maxScore = 0
    var bestMatch: Product? = null

    for(product in products.filter {it.brand == brand}) {
      val productTokens = tokenize(product.brand.name + " " + product.product)
      // Score = number of tokens which are common across both search product and search
      val score = productTokens.filter {
        searchTokens.contains(it)
      }.size

      if(score > maxScore) {
        maxScore  = score
        bestMatch = product
      }
    }

    val ret = ArrayList<Product>()
    if(bestMatch != null) {
      ret.add(bestMatch)
    }
    return ret
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