package com.jaredloomis.analmark.main

typealias PostingParser<T> = (source: T) -> RawPosting

open class RawPosting(
  val _title: String, val _description: String, val _priceStr: String, val _specs: Map<String, String>
) {
  var brand: String? = null

  constructor(brand: String, title: String, description: String, priceStr: String, specs: Map<String, String>)
    : this(title, description, priceStr, specs) {
    this.brand = brand
  }

  val title: String
    get() = cleanTitle()
  val description: String
    get() = cleanDescription()
  val price: CurrencyAmount
    get() = cleanPrice()

  fun parse(): ProductPosting? {
    return if(brand != null) {
      val title   = removeSubstring(brand!!, _title, caseSensitive=false)
      val product = Product(title, Brand(brand!!))
      val price   = parsePrice(_priceStr)
      ProductPosting(product, price)
    } else {
      null
    }
  }

  open fun cleanTitle(): String {
    return _title
  }

  open fun cleanDescription(): String {
    return _description
  }

  open fun cleanPriceStr(): String {
    return _priceStr
  }

  open fun cleanPrice(): CurrencyAmount {
    println("${this.cleanPriceStr()}, ${this._priceStr}")
    return CurrencyAmount(this.cleanPriceStr())
  }

  open fun parsePrice(str: String): CurrencyAmount {
    return CurrencyAmount(str)
  }

  override fun toString(): String {
    return "RawPosting(title='$title', description='$description', price='$price', specs=$_specs, brand=$brand)"
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

class EbayRawPosting(title: String, description: String, priceStr: String, specs: Map<String, String>)
  : RawPosting(title, description, priceStr, specs) {
  init {
    if(specs.containsKey("brand")) {
      brand = specs["brand"]
    }
  }

  override fun cleanTitle(): String {
    return if(brand != null)
      removeSubstring(brand!!, _title).trim()
    else
      _title
  }
}