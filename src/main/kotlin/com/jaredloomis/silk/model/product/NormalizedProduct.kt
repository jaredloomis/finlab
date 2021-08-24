package com.jaredloomis.silk.model.product

class NormalizedProduct {
  var upc: String? = null
  var brand: String? = null
  var model: String? = null

  var category: String? = null

  val id: ProductID?
    get() =
      if(upc != null)                         ProductID.UPC(upc!!)
      else if(brand != null && model != null) ProductID.BrandModel(brand!!, model!!)
      else                                    null

  constructor(brand: String, model: String) {
    this.brand = brand
    this.model = model
  }

  constructor(upc: String) {
    this.upc = upc
  }
}