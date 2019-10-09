package com.jaredloomis.analmark.model

import com.jaredloomis.analmark.nlp.parseTechnicalModelIDs
import com.jaredloomis.analmark.nlp.removeSubstring

class Product(var canonicalName: String, val primaryBrand: Brand) {
  var id: Long? = null
  var upc: String? = null

  /**
   * A model ID is a manufacturer-defined string which can be considered a unique identifier for product,
   * when paired with brand name. ex. MDR-XB950B1, WH-1000XM3
   * ex.
   */
  var modelID: String? = null
  var category: String? = null
  val tags: MutableSet<String> = HashSet()
  val associatedBrands: MutableSet<String> = HashSet()

  constructor(id: Long, canonicalName: String, brand: Brand) : this(canonicalName, brand) {
    this.id = id
  }

  constructor(copy: Product): this(copy.canonicalName, copy.primaryBrand) {
    id  = copy.id
    upc      = copy.upc
    modelID  = copy.modelID
    category = copy.category
    tags.addAll(copy.tags)
    associatedBrands.addAll(copy.associatedBrands)
  }

  /**
   * XXX
   * TODO: EXPAND FUNCTIONALITY
   * Currenly just returns this, with name replaced by that.canonicalName if it is shorter than this.canonicalName.
   */
  fun merge(that: Product): Product {
    val name = if(canonicalName.length < that.canonicalName.length) {
      canonicalName
    } else {
      that.canonicalName
    }
    val product = Product(this)
    product.canonicalName = name
    return product
  }

  override fun toString(): String {
    return "Product(id='$id' canonicalName='$canonicalName', primaryBrand='$primaryBrand', upc='$upc', modelID='$modelID' tags='$tags' associatedBrands='$associatedBrands')"
  }
}