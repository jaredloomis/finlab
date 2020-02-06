package com.jaredloomis.analmark.scrape

import com.jaredloomis.analmark.model.forex.CurrencyConversion
import java.util.*

enum class ForexMarketID {
    XE,
    EXCHANGE_RATES_API  // exchangeratesapi.io
}

abstract class ForexMarket constructor(val type: ForexMarketID, val name: String, val url: String) {
    abstract fun init()
    abstract fun fetchRates(base: Currency): List<CurrencyConversion>
    abstract fun quit()
}
