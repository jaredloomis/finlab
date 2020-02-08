package com.jaredloomis.analmark.view.forex

import com.jaredloomis.analmark.model.forex.Exchange
import com.jaredloomis.analmark.model.forex.ForexMarketModel
import java.util.*

enum class ForexMarketID {
  XE,
  EXCHANGE_RATES_API  // exchangeratesapi.io
}

abstract class ForexMarketView constructor(val type: ForexMarketID, val name: String, val url: String) {
  abstract fun init()
  abstract fun fetchRates(base: Currency): List<Exchange>
  abstract fun quit()

  fun fetch(): ForexMarketModel {
    val allRates = Currency.getAvailableCurrencies()
      .flatMap {cur -> fetchRates(cur)}

    return ForexMarketModel(allRates)
  }
}
