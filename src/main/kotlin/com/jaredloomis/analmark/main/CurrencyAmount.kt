package com.jaredloomis.analmark.main

import java.util.regex.Matcher
import java.util.regex.Pattern

class CurrencyAmount {
  val pennies: Long

  constructor(pennies: Long) {
    this.pennies = pennies
  }

  constructor(str: String) {
    val pricePattern: Pattern = Pattern.compile("[^\\d]*([\\d,]+)")
    val m: Matcher = pricePattern.matcher(str)
    if (m.find()) {
      val dollars = m.group(1).replace(",", "")
      this.pennies = dollars.toLong() * 100
    } else {
      throw Exception("Improperly formatted CurrencyAmount")
    }
  }

  override fun toString(): String {
    return "CurrencyAmount(pennies=$pennies)"
  }
}