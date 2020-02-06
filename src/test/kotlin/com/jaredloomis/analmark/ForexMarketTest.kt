package com.jaredloomis.analmark

import com.jaredloomis.analmark.scrape.forex.ExchangeRatesAPIMarket
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ForexMarketTest {
    @BeforeAll
    fun init() {
    }

    @AfterAll
    fun destroy() {
    }

    @Test
    fun exchangeRatesAPIMarketTest() {
        val base = Currency.getInstance("USD")
        val market = ExchangeRatesAPIMarket()
        market.init()
        val rates = market.fetchRates(base)
        market.quit()
        rates.forEach {rate ->
            print(rate)
        }
        assert(rates.isNotEmpty())
        assert(rates.all    {it.from == base})
        assert(rates.filter {it.to   == Currency.getInstance("MXN")}.size == 1)
    }
}
