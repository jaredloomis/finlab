package com.jaredloomis.analmark

import com.jaredloomis.analmark.legacy.DummyBrandDB
import com.jaredloomis.analmark.legacy.PostgresProductDB
import com.jaredloomis.analmark.model.Brand
import com.jaredloomis.analmark.model.CurrencyAmount
import com.jaredloomis.analmark.model.EbayRawPosting
import com.jaredloomis.analmark.model.Product
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