package com.jaredloomis.analmark.model

class Product(val canonicalName: String, val primaryBrand: Brand) {
  var id: Long? = null
  var upc: String? = null

  /**
   * A model ID is a manufacturer-defined string which can be considered a unique identifier for product,
   * when paired with brand name. ex. MDR-XB950B1, WH-1000XM3
   * ex.
   */
  var modelID: String? = null

  val tags: MutableSet<String> = HashSet()
  val associatedBrands: MutableSet<String> = HashSet()

  constructor(id: Long, canonicalName: String, brand: Brand) : this(canonicalName, brand) {
    this.id = id
  }

  /**
   * XXX
   * TODO: EXPAND FUNCTIONALITY
   * Currenly just returns this, with name replaced by that.canonicalName if it is longer than this.canonicalName.
   */
  fun merge(that: Product): Product {
    return if(id == that.id) {
      val name = if(canonicalName.length > that.canonicalName.length) {
        canonicalName
      } else {
        that.canonicalName
      }
      val product = Product(name, primaryBrand)
      product.id  = id
      product.upc = upc
      product
    } else {
      this
    }
  }

  override fun toString(): String {
    return "Product(id='$id' canonicalName='$canonicalName', primaryBrand='$primaryBrand', upc='$upc', modelID='$modelID' tags='$tags' associatedBrands='$associatedBrands')"
  }
}