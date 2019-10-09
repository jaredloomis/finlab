package com.jaredloomis.analmark.nlp

import com.jaredloomis.analmark.db.DBModel
import com.jaredloomis.analmark.model.Brand
import com.jaredloomis.analmark.model.Product
import com.jaredloomis.analmark.model.ProductPosting
import com.jaredloomis.analmark.model.RawPosting
import java.lang.UnsupportedOperationException
import java.util.stream.Stream

abstract class ProductRecognition {
  open fun recognize(posting: RawPosting, matches: Stream<Product>): ProductPosting? {
    return try {
      val product = matches
        .sorted {a, b -> affinity(posting, a).compareTo(affinity(posting, b))}
        .findFirst()
        .orElse(null)

      if(product != null) {
        ProductPosting(product, posting)
      } else {
        null
      }
    } catch(ex: Exception) {
      null
    }
  }

  abstract fun create(posting: RawPosting): ProductPosting?
  abstract fun affinity(posting: RawPosting, product: Product): Long
}

open class SimpleProductRecognition: ProductRecognition() {
  override fun create(posting: RawPosting): ProductPosting? {
    return if(posting.brand != null) {
      val cleanTitle   = removeSubstring(posting.brand!!, posting.title, caseSensitive=false).trim()
      val product      = Product(cleanTitle, Brand(posting.brand!!))
      product.modelID  = parseModelID(posting)
      product.category = posting.category
      product.tags.addAll(posting.tags)
      product.upc      = posting.specs["upc"]
      return ProductPosting(product, posting)
    } else {
      // TODO parse brand if not explicitly given,
      //  possibly in new class which depends on a DBModel<_, Product>
      null
    }
  }

  private fun parseModelID(posting: RawPosting): String? {
    val cleanTitle = removeSubstring(posting.brand!!, posting.title, caseSensitive=false).trim()
    return posting.specs["mpn"] ?: parseTechnicalModelIDs(cleanTitle) ?: cleanTitle
  }

  override fun affinity(posting: RawPosting, product: Product): Long {
    return try {
      val attrValues = posting.specs.entries
        .map {entry -> entry.value}
        .reduce {a, b -> "$a $b"}
      val postingWords = "${posting.title} $attrValues"
      val productWords = "${product.primaryBrand} ${product.canonicalName}"
      wordsInCommon(productWords, postingWords).size.toLong()
    } catch(ex: UnsupportedOperationException) {
      // Empty entries
      0
    }
  }
}

class DBCachingProductRecognition(val productDB: DBModel<RawPosting, Product>, val postingDB: DBModel<Product, ProductPosting>): SimpleProductRecognition() {
  override fun create(posting: RawPosting): ProductPosting? {
    val ret = super.create(posting)
    if(ret != null) {
      try {
        productDB.insert(ret.product)
        postingDB.insert(ret)
      } catch(ex: Exception) {
        ex.printStackTrace()
      }
    }
    return ret
  }
}
