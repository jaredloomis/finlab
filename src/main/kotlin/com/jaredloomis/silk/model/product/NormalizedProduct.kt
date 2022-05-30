package com.jaredloomis.silk.model.product

class NormalizedProduct {
  var upc: String? = null
  var brand: String? = null
  var model: String? = null

  var category: String? = null

  val key: ProductKey?
    get() =
      if(upc != null)                         ProductKey.UPC(upc!!)
      else if(brand != null && model != null) ProductKey.BrandModel(brand!!, model!!)
      else                                    null

  constructor(brand: String, model: String) {
    this.brand = brand
    this.model = model
  }

  constructor(upc: String) {
    this.upc = upc
  }
}