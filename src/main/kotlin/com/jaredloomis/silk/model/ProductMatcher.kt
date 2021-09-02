package com.jaredloomis.silk.model

import com.jaredloomis.silk.db.SQLStatement
import com.jaredloomis.silk.db.Table
import com.jaredloomis.silk.di
import com.jaredloomis.silk.model.product.Product
import com.jaredloomis.silk.model.product.RawPosting
import org.kodein.di.instance
import java.util.stream.Stream

interface ProductMatcher {
  fun matches(post: RawPosting): Stream<Product>
}

class SimpleProductMatcher : ProductMatcher {
  private val productTable by di.instance<Table<Product>>()

  override fun matches(post: RawPosting): Stream<Product> {
    val querySQL = """
      SELECT * FROM ${productTable.tableName}
      WHERE upc = %upc OR (brand ILIKE '%' || %brand || '%' AND modelid ILIKE '%' || %model || '%')
    """
    return productTable.query(SQLStatement(querySQL), post)
  }
}