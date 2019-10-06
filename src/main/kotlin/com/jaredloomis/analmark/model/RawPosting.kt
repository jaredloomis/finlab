package com.jaredloomis.analmark.model

import com.jaredloomis.analmark.scrape.MarketType
import com.jaredloomis.analmark.nlp.removeSubstring
import java.time.Instant

open class RawPosting(
  val market: MarketType, val url: String,
  val _title: String, private val _description: String, private val _price: CurrencyAmount, private val _specs: Map<String, String>
) {
  val title: String
    get() = cleanTitle()
  val description: String
    get() = cleanDescription()
  val price: CurrencyAmount
    get() = _price
  val specs: Map<String, String>
    get() = _specs
  val brand: String?
    get() = specs["brand"]
  val seen: Instant = Instant.now()

  open fun cleanTitle(): String {
    return _title
  }

  open fun cleanDescription(): String {
    return _description
  }

  open fun parsePrice(str: String): CurrencyAmount {
    return CurrencyAmount(str)
  }

  override fun toString(): String {
    return "RawPosting(market='$market' title='$title', description='$description', price='$price', url='$url' specs=$_specs)"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as RawPosting

    if (_title != other._title) return false
    if (_description != other._description) return false
    if (_price != other._price) return false
    if (_specs != other._specs) return false

    return true
  }

  override fun hashCode(): Int {
    var result = _title.hashCode()
    result = 31 * result + _description.hashCode()
    result = 31 * result + _price.hashCode()
    result = 31 * result + _specs.hashCode()
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
  override fun cleanTitle(): String {
    return if(brand != null)
      removeSubstring(brand!!, _title).trim()
    else
      _title
  }
}