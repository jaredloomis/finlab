package com.jaredloomis.silk.model.product

sealed class ProductKey(val id: String) {
  data class BrandModel(val brand: String, val model: String) : ProductKey("brandmodel:${brand}|${model}")
  data class UPC(val upc: String) : ProductKey("upc:${upc}")
}