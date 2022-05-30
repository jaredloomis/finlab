package com.jaredloomis.silk.model.realestate

import com.jaredloomis.silk.model.CurrencyAmount
import java.time.LocalDate

data class RealEstateListing(
  val address: String, val price: CurrencyAmount,
  val beds: Int, val baths: Int, val squareFootage: Int,
  val images: Set<String>, val listedDate: LocalDate
)
