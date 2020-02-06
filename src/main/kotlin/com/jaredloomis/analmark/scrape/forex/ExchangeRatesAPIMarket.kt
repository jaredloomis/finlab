package com.jaredloomis.analmark.scrape.forex

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.jaredloomis.analmark.model.forex.CurrencyConversion
import com.jaredloomis.analmark.scrape.ForexMarket
import com.jaredloomis.analmark.scrape.ForexMarketID
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.*


class ExchangeRatesAPIMarket
    : ForexMarket(ForexMarketID.EXCHANGE_RATES_API, "Exchange Rates API", "exchangeratesapi.io") {
    private var client: HttpClient? = null
    private val mapper = ObjectMapper()

    override fun init() {
        client = HttpClient.newBuilder().build()
    }

    override fun fetchRates(base: Currency): List<CurrencyConversion> {
        val request: HttpRequest = HttpRequest.newBuilder()
                .GET().uri(URI("https://api.exchangeratesapi.io/latest?base=${base.currencyCode}"))
                .build()

        val response: HttpResponse<String>? = client?.send(request, HttpResponse.BodyHandlers.ofString())
        val responseStr: String = response!!.body()
        val responseJson: JsonNode = mapper.readTree(responseStr)
        return Currency.getAvailableCurrencies()
                .mapNotNull {cur ->
                    val rate = responseJson["rates"][cur.currencyCode]
                    if(rate != null && rate.isNumber) {
                        CurrencyConversion(base, cur, rate.asDouble())
                    } else {
                        null
                    }
                }
    }

    override fun quit() {}
}