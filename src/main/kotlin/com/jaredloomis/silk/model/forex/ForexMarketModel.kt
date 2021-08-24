package com.jaredloomis.silk.model.forex

import com.jaredloomis.silk.model.EdgeGraph
import java.util.*
import java.util.stream.Collectors

data class ForexMarketModel(val exchanges: Collection<Exchange>) {
  val currencyGraph: EdgeGraph<Currency, Exchange> = exchanges.fold(EdgeGraph(), {graph, conv ->
    graph.insert(conv.from, conv.to, conv)
  })

  val buyCurrencies = exchanges.map {it.from}.toSet()
  val sellCurrencies = exchanges.map {it.to}.toSet()
  val currencies = buyCurrencies.plus(sellCurrencies)


  fun sortedExchangeChains(assets: Set<Currency>): List<ExchangeChain> {
    return assets.flatMap {source ->
      currencies.flatMap {dest ->
        currencyGraph.paths(source, dest)
        .limit(100)
        .map {ExchangeChain(it)}
        .collect(Collectors.toList())
        .toList()
      }
    }
    .sortedBy {it.rate}
  }
}