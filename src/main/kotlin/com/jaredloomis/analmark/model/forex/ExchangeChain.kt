package com.jaredloomis.analmark.model.forex

data class ExchangeChain(val chain: List<Exchange>) {
  val rate: Double
    get() = chain.fold(1.0, {acc, exch -> acc * exch.rate })
}