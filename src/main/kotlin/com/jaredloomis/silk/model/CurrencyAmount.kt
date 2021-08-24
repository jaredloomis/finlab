package com.jaredloomis.silk.model

import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern

class CurrencyAmount {
  val pennies: Long
  val currency: Currency

  constructor(pennies: Long) {
    this.pennies = pennies
    this.currency = Currency.getInstance("USD")
  }

  constructor(str: String) {
    val pricePattern: Pattern = Pattern.compile("[^\\d]*([\\d,]+(\\.\\d)?)")
    val m: Matcher = pricePattern.matcher(str)
    if (m.find()) {
      val dollars = m.group(1).replace(",", "")
      this.pennies = (dollars.toDouble() * 100).toLong()
      // TODO correctly interpret currency
      this.currency = Currency.getInstance("USD")
    } else {
      throw Exception("Improperly formatted CurrencyAmount: '$str'")
    }
  }

  override fun equals(other: Any?): Boolean {
    return if(other == null || other !is CurrencyAmount) {
      false
    } else if(other === this) {
      true
    } else {
      pennies == other.pennies && currency == other.currency
    }
  }

  override fun toString(): String {
    return "CurrencyAmount(pennies=$pennies, currency=${currency.currencyCode})"
  }
}