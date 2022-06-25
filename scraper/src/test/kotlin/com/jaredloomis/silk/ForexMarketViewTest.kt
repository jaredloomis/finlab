package com.jaredloomis.silk

import com.jaredloomis.silk.scrape.forex.ExchangeRatesAPIMarketView
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ForexMarketViewTest {
  @BeforeAll
  fun init() {
  }

  @AfterAll
  fun destroy() {
  }

  @Test
  fun exchangeRatesAPIMarketTest() {
    val base = Currency.getInstance("USD")
    val market = ExchangeRatesAPIMarketView()
    market.init()
    val rates = market.fetchRates(base)
    market.quit()
    rates.forEach { rate ->
      println(rate)
    }
    assert(rates.isNotEmpty())
    assert(rates.all { it.from == base })
    assert(rates.filter { it.to == Currency.getInstance("MXN") }.size == 1)
  }
}
