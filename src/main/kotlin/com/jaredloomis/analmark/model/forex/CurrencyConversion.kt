package com.jaredloomis.analmark.model.forex

import java.util.*

data class CurrencyConversion(val from: Currency, val to: Currency, val rate: Double) {}