package com.jaredloomis.analmark.model.product

sealed class ProductID(val id: String) {
  data class BrandModel(val brand: String, val model: String) : ProductID("brandmodel:${brand}|${model}")
  data class UPC(val upc: String) : ProductID("upc:${upc}")
}