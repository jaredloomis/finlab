package com.jaredloomis.analmark

import com.jaredloomis.analmark.analysis.AWSComprehend
import com.jaredloomis.analmark.db.PostgresPostingDBModel
import com.jaredloomis.analmark.db.PostgresProductDBModel
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AWSComprehendTest {
  val postingDB = PostgresPostingDBModel(PostgresProductDBModel())

  @Test
  fun comprehendTest() {
    val post = postingDB.all().findAny().orElse(null)
    println("POST: ${post.posting}")
    println(AWSComprehend()
      .comprehendBrand("${post.posting.title}. ${post.posting.description}"))
  }
}
