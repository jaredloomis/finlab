package com.jaredloomis.silk.nlp

import com.jaredloomis.silk.model.product.Brand
import com.jaredloomis.silk.model.product.Product
import com.jaredloomis.silk.model.product.ProductPosting
import com.jaredloomis.silk.model.product.RawPosting
import com.jaredloomis.silk.util.getLogger
import java.util.stream.Stream

/**
 * ProductRecognition handles the assigning of RawPosting to Products, either by
 * matching to an existing product, or by creating a new one.
 */
abstract class ProductRecognition {
  val logger = getLogger(this::class)

  private val MIN_AFFINITY: Double = 0.9

  abstract fun create(posting: RawPosting): ProductPosting?
  abstract fun affinity(posting: RawPosting, product: Product): Double

  open fun recognize(posting: RawPosting, matches: Stream<Product>): ProductPosting? {
    return try {
      val product = matches
        .sorted { a, b -> affinity(posting, a).compareTo(affinity(posting, b)) }
        .findFirst()
        .orElse(null)

      logger.info("Product Affinity: ${affinity(posting, product)}")
      if (product != null && affinity(posting, product) >= MIN_AFFINITY) {
        logger.info("Recognized product from query '$posting': $product")
        ProductPosting(product, posting)
      } else {
        null
      }
    } catch (ex: Exception) {
      logger.info("Possibly no matches from db. check stacktrace")
      ex.printStackTrace()
      null
    }
  }
}

open class SimpleProductRecognition : ProductRecognition() {
  override fun create(posting: RawPosting): ProductPosting? {
    return if (posting.brand != null) {
      val titleTokens = tokens(removeSubstring(posting.brand!!, posting.title, caseSensitive = false).trim())
      val title = removeStopTokens(titleTokens).joinToString(" ")
      val product = Product(title, Brand(posting.brand!!))
      product.modelID = parseModelID(posting)
      product.category = posting.category
      product.tags.addAll(posting.tags)
      product.upc = posting.specs["upc"]
      return ProductPosting(product, posting)
    } else {
      // TODO parse brand if not explicitly given,
      //  possibly in new ProductRecognition class which depends on a DBModel<_, Product>
      null
    }
  }

  private fun parseModelID(posting: RawPosting): String? {
    val cleanTitle = removeSubstring(posting.brand!!, posting.title, caseSensitive = false).trim()
    return posting.specs["mpn"] ?: parseTechnicalModelIDs(cleanTitle) ?: cleanTitle
  }

  override fun affinity(posting: RawPosting, product: Product): Double {
    return try {
      val attrValuesStr = posting.specs.entries
        .map { entry -> entry.value }
        .reduce { a, b -> "$a $b" }
      val postingWords = removeStopTokens(tokens("${posting.brand} ${posting.title} $attrValuesStr"))
      val brandWords = tokens(product.primaryBrand.name)
      val productWords = tokens(product.canonicalName)
      val brandMatches = posting.brand?.toLowerCase() == product.primaryBrand.name || postingWords.containsIgnoreCase(product.primaryBrand.name)
      // If brand name doesn't match at all, affinity is 0
      if (!brandMatches) {
        0.0
      } else {
        val modelIDTokens = if (product.modelID == null) {
          ArrayList()
        } else {
          tokens(product.modelID!!)
        }
        val maxPoints = productWords.size + (product.modelID?.length ?: 0)
        val productPoints = wordsInCommon(tokens(product.canonicalName), postingWords).size.toLong()
        val modelPoints = wordsInCommon(modelIDTokens, postingWords).size.toLong()
        val points = productPoints.toDouble() + modelPoints

        // If model ID matches, it's a match!
        if (product.modelID != null && product.modelID == posting.model) {
          1.0
        } else {
          points / maxPoints
        }
      }
    } catch (ex: UnsupportedOperationException) {
      // Empty entries
      0.0
    }
  }
}
