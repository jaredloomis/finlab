package com.jaredloomis.analmark.view.forex

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.jaredloomis.analmark.model.forex.Exchange
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.*


class ExchangeRatesAPIMarketView
  : ForexMarketView(ForexMarketID.EXCHANGE_RATES_API, "Exchange Rates API", "exchangeratesapi.io") {
  private var client: HttpClient? = null
  private val mapper = ObjectMapper()

  override fun init() {
    client = HttpClient.newBuilder().build()
  }

  override fun fetchRates(base: Currency): List<Exchange> {
    val request: HttpRequest = HttpRequest.newBuilder()
      .GET().uri(URI("https://api.exchangeratesapi.io/latest?base=${base.currencyCode}"))
      .build()

    val response: HttpResponse<String>? = client?.send(request, HttpResponse.BodyHandlers.ofString())
    return if(response == null) {
      emptyList()
    } else {
      val responseStr: String? = response?.body()
      val responseJson: JsonNode = mapper.readTree(responseStr)
      Currency.getAvailableCurrencies()
      .mapNotNull { cur ->
        val rate = responseJson["rates"][cur.currencyCode]
        if (rate != null && rate.isNumber) {
          Exchange(base, cur, rate.asDouble())
        } else {
          null
        }
      }
    }
  }

  override fun quit() {}
}