package com.jaredloomis.analmark

import com.jaredloomis.analmark.model.CurrencyAmount
import com.jaredloomis.analmark.model.product.Brand
import com.jaredloomis.analmark.model.product.Product
import com.jaredloomis.analmark.model.product.RawPosting
import com.jaredloomis.analmark.nlp.SimpleProductRecognition
import com.jaredloomis.analmark.view.product.ProductMarketType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductRecognitionTest {
  val recognition = SimpleProductRecognition()

  @Test
  fun simpleRecognition() {
    val posting = RawPosting(
      ProductMarketType.EBAY,
      "", "Sony Xav-ax5000 7 Carplay/android Auto Media Receiver With Bluetooth", "", CurrencyAmount("$299"),
      emptyMap<String, String>().plus(Pair("brand", "Sony"))
    )
    val product1 = Product("Carplay", Brand("Sony"))
    val product2 = Product("Walkman", Brand("Sony"))
    val ret = recognition.recognize(posting, listOf(product1, product2).stream())
    assert(ret != null && ret.product.canonicalName == "Carplay") { "Very Simple recognition $ret" }
  }
}
