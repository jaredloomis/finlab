package com.jaredloomis.analmark

import com.jaredloomis.analmark.view.forex.ExchangeRatesAPIMarketView
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ForexMarketViewModelTest {
  @BeforeAll
  fun init() {
  }

  @AfterAll
  fun destroy() {
  }

  @Test
  fun sortedForexMargins() {
    val market = ExchangeRatesAPIMarketView().fetch()
    val assets = setOf("USD", "CAD", "MXN").map {Currency.getInstance(it)}.toSet()
    val chains = market.sortedExchangeChains(assets)
    assert(chains.isNotEmpty())
    chains.forEach {println(it)}
  }
}
