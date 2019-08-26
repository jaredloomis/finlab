package com.jaredloomis.analmark.main

typealias PostingParser<T> = (source: T) -> RawPosting

open class RawPosting(
  val title: String, val description: String, val priceStr: String, val specs: Map<String, String>
) {
  var brand: String? = null

  constructor(brand: String, title: String, description: String, priceStr: String, specs: Map<String, String>)
    : this(title, description, priceStr, specs) {
    this.brand = brand
  }

  fun parse(): ProductPosting? {
    return if(brand != null) {
      val title   = removeSubstring(brand!!, title, caseSensitive=false)
      val product = Product(title, brand!!)
      val price   = parsePrice(priceStr)
      ProductPosting(product, price)
    } else {
      null
    }
  }

  open fun parsePrice(str: String): CurrencyAmount {
    return CurrencyAmount(str)
  }

  override fun toString(): String {
    return "RawPosting(title='$title', description='$description', priceStr='$priceStr', specs=$specs, brand=$brand)"
  }


}

class OverstockRawPosting(brand: String, title: String, description: String, priceStr: String, specs: Map<String, String>)
  : RawPosting(brand, title, description, priceStr, specs) {
  override fun parsePrice(str: String): CurrencyAmount {
    val len     = str.length
    val cents   = str.substring(len - 2)
    val dollars = str.substring(0, len - 2)
    return CurrencyAmount("$dollars.$cents")
  }
}