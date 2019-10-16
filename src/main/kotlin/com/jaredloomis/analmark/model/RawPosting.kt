package com.jaredloomis.analmark.model

import com.jaredloomis.analmark.scrape.MarketType
import com.jaredloomis.analmark.nlp.removeSubstring
import java.time.Instant

open class RawPosting(
  val market: MarketType, val url: String,
  val title: String, val description: String, val price: CurrencyAmount,
  // Specs keys should all be lower-case
  _specs: Map<String, String>
) {
  val brand: String?
    get() = specs["brand"] ?: specs["maker"] ?: specs["manufacturer"] ?: specs["make"]
  val model: String?
    get() = specs["model"] ?: specs["model id"] ?: specs["model no"] ?: specs["modelid"]
  val seen: Instant = Instant.now()
  var category: String? = null
  val tags: MutableSet<String> = HashSet()
  val specs = _specs
    .mapValues {
      when(it.value) {
        "does not apply" -> null
        else             -> it.value
      }
    }
    .filter {it.value != null}

  open fun parsePrice(str: String): CurrencyAmount {
    return CurrencyAmount(str)
  }

  override fun toString(): String {
    return "RawPosting(market='$market' title='$title', description='$description', price='$price', category='$category' seen='$seen' url='$url' specs=$specs, tags=$tags)"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as RawPosting

    if (title != other.title) return false
    if (description != other.description) return false
    if (price != other.price) return false
    if (specs != other.specs) return false
    if(category != other.category) return false
    if(tags != other.tags) return false

    return true
  }

  override fun hashCode(): Int {
    var result = title.hashCode()
    result = 31 * result + description.hashCode()
    result = 31 * result + price.hashCode()
    result = 31 * result + specs.hashCode()
    return result
  }
}

class OverstockRawPosting(url: String, title: String, description: String, price: CurrencyAmount, specs: Map<String, String>)
  : RawPosting(MarketType.OVERSTOCK, url, title, description, price, specs) {
  override fun parsePrice(str: String): CurrencyAmount {
    val len     = str.length
    val cents   = str.substring(len - 2)
    val dollars = str.substring(0, len - 2)
    return CurrencyAmount("$dollars.$cents")
  }
}

class EbayRawPosting(url: String, title: String, description: String, price: CurrencyAmount, specs: Map<String, String>)
  : RawPosting(MarketType.EBAY, url, title, description, price, specs) {
}