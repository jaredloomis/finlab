package com.jaredloomis.analmark.main

class Product {
  val product: String
  val brand: Brand

  constructor(product: String, brand: Brand) {
    this.product = product
    this.brand = brand
  }

  // XXX temporary - need to parse product & brand
  constructor(str: String) {
    this.product = str
    this.brand = Brand("")
  }

  override fun toString(): String {
    return "Product(product='$product', brand='$brand')"
  }
}

data class ProductPosting(val product: Product, val price: CurrencyAmount) {
  override fun toString(): String {
    return "ProductPosting(product=$product, price=$price)"
  }
}