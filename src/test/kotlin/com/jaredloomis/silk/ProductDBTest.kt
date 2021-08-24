package com.jaredloomis.silk

import com.jaredloomis.silk.legacy.DummyBrandDB
import com.jaredloomis.silk.legacy.PostgresProductDB
import com.jaredloomis.silk.model.CurrencyAmount
import com.jaredloomis.silk.model.product.Brand
import com.jaredloomis.silk.model.product.EbayRawPosting
import com.jaredloomis.silk.model.product.Product
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.stream.Collectors

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductDBTest {
  @Test
  fun addProductAndSearchForIt() {
    val productDB = PostgresProductDB(DummyBrandDB())
    productDB.addProduct(Product("wasabi", Brand("Costco")))
    val matches = productDB.findMatches(EbayRawPosting("", "Costco", "", CurrencyAmount("$200.00"), emptyMap()))
      .collect(Collectors.toList())

    print("MATCHES: $matches")
    assert(matches.size > 0)
  }
}