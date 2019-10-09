package com.jaredloomis.analmark.model

import java.util.regex.Matcher
import java.util.regex.Pattern

class CurrencyAmount {
  val pennies: Long

  constructor(pennies: Long) {
    this.pennies = pennies
  }

  constructor(str: String) {
    val pricePattern: Pattern = Pattern.compile("[^\\d]*([\\d,]+(\\.\\d)?)")
    val m: Matcher = pricePattern.matcher(str)
    if(m.find()) {
      val dollars = m.group(1).replace(",", "")
      this.pennies = (dollars.toDouble() * 100).toLong()
    } else {
      throw Exception("Improperly formatted CurrencyAmount: $str")
    }
  }

  override fun toString(): String {
    return "CurrencyAmount(pennies=$pennies)"
  }
}