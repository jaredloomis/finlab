package com.jaredloomis.silk

import com.jaredloomis.silk.db.PostgresPostingDBModel
import com.jaredloomis.silk.db.PostgresProductDBModel
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.stream.Collectors

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresDumpTest {
  val productModel = PostgresProductDBModel()
  val postingModel = PostgresPostingDBModel(productModel)

  @Test
  fun pgPosting() {
    val products = productModel.all().collect(Collectors.toList())
    println("Total Products: ${products.size}")
    products.forEach { println(it) }
    val postings = postingModel.all().collect(Collectors.toList())
    println("Total ProductPostings: ${postings.size}")
    postings.forEach { println(it) }
  }
}
