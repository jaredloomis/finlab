package com.jaredloomis.silk.model.product

class Product(var canonicalName: String, var primaryBrand: Brand) {
  var id: Long? = null
  var upc: String? = null

  /**
   * A model ID is a manufacturer-defined string which can be considered a unique identifier for product,
   * when paired with brand name. ex. MDR-XB950B1, WH-1000XM3
   * ex.
   */
  var modelID: String? = null
  var category: String? = null
  val specs: MutableMap<String, String> = HashMap()
  val tags: MutableSet<String> = HashSet()
  val associatedBrands: MutableSet<String> = HashSet()

  val prettyName: String
    get() = "${primaryBrand.name} ${modelID ?: canonicalName}"

  val productID: ProductID?
    get() = when {
        upc != null     -> ProductID.UPC(upc!!)
        modelID != null -> ProductID.BrandModel(primaryBrand.name, modelID!!)
        else            -> null
      }

  constructor(id: Long, canonicalName: String, brand: Brand) : this(canonicalName, brand) {
    this.id = id
  }

  constructor(copy: Product) : this(copy.canonicalName, copy.primaryBrand) {
    id = copy.id
    upc = copy.upc
    modelID = copy.modelID
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
    val product = Product(this)
    product.canonicalName = if (canonicalName.length < that.canonicalName.length) {
      canonicalName
    } else {
      that.canonicalName
    }
    product.primaryBrand = if (primaryBrand.name.length < that.primaryBrand.name.length) {
      primaryBrand
    } else {
      that.primaryBrand
    }
    product.id = id ?: that.id
    product.category = category ?: that.category
    product.upc = upc ?: that.upc
    product.modelID = modelID ?: that.modelID
    product.tags.addAll(that.tags)
    product.associatedBrands.addAll(that.associatedBrands)
    return product
  }

  override fun toString(): String {
    return "Product(id='$id' canonicalName='$canonicalName', primaryBrand='$primaryBrand', upc='$upc', modelID='$modelID', category='$category', tags='$tags', associatedBrands='$associatedBrands')"
  }
}