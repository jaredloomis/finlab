package com.jaredloomis.silk.model

import java.lang.RuntimeException
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.math.absoluteValue

class CurrencyAmount : Comparable<CurrencyAmount> {
  val pennies: Long
  val currency: Currency

  constructor(pennies: Long) {
    this.pennies = pennies
    this.currency = Currency.getInstance("USD")
  }

  constructor(pennies: Long, currency: Currency) {
    this.pennies = pennies
    this.currency = currency
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

  operator fun plus(other: CurrencyAmount): CurrencyAmount {
    if(currency == other.currency) {
      return CurrencyAmount(pennies + other.pennies, currency)
    } else {
      throw Exception("Cannot add a CurrencyAmount by another CurrencyAmount with a different denomination.")
    }
  }

  operator fun minus(other: CurrencyAmount): CurrencyAmount {
    if(currency == other.currency) {
      return CurrencyAmount(pennies - other.pennies, currency)
    } else {
      throw Exception("Cannot subtract a CurrencyAmount by another CurrencyAmount with a different denomination.")
    }
  }

  operator fun times(other: CurrencyAmount): CurrencyAmount {
    if(currency == other.currency) {
      return CurrencyAmount(pennies * other.pennies, currency)
    } else {
      throw Exception("Cannot add a CurrencyAmount by another CurrencyAmount with a different denomination.")
    }
  }

  operator fun div(other: CurrencyAmount): CurrencyAmount {
    if(currency == other.currency) {
      return CurrencyAmount(pennies / other.pennies, currency)
    } else {
      throw Exception("Cannot divide a CurrencyAmount by another CurrencyAmount with a different denomination.")
    }
  }

  operator fun div(other: Long): CurrencyAmount {
    return CurrencyAmount(pennies / other, currency)
  }

  fun abs(): CurrencyAmount {
    return CurrencyAmount(pennies.absoluteValue, currency)
  }

  override fun compareTo(other: CurrencyAmount): Int {
    if(currency == other.currency) {
      return pennies.compareTo(other.pennies)
    } else {
      throw Exception("Cannot compare a CurrencyAmount to another CurrencyAmount with a different denomination.")
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
    return return if(currency.currencyCode == "USD") {
      "\$${pennies.toDouble() / 100}"
    } else {
      "CurrencyAmount(pennies=$pennies, currency=${currency.currencyCode})"
    }
  }

  override fun hashCode(): Int {
    var result = pennies.hashCode()
    result = 31 * result + currency.hashCode()
    return result
  }
}