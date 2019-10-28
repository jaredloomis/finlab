package com.jaredloomis.analmark.model

data class ProductPosting(val product: Product, val posting: RawPosting) {
  var id: Long? = null

  constructor(id: Long?, product: Product, posting: RawPosting) : this(product, posting) {
    this.id = id
  }

  /**
   * TODO: expand functionality. Should merge two ProductPostings as much as possible, to
   * result in a new posting which contains more information that either of the operands.
   */
  fun merge(that: ProductPosting): ProductPosting {
    return ProductPosting(id, product.merge(that.product), posting)
  }

  override fun toString(): String {
    return "ProductPosting(product=$product, posting=$posting)"
  }
}