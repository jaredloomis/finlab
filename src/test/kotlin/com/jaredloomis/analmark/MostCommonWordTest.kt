package com.jaredloomis.analmark

import com.jaredloomis.analmark.db.PostgresPostingDBModel
import com.jaredloomis.analmark.db.PostgresProductDBModel
import com.jaredloomis.analmark.nlp.tokens
import com.jaredloomis.analmark.util.BinarySearchTree
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.stream.Collectors

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MostCommonWordTest {
  val topWordCount = 300

  val wordCountTree = BinarySearchTree<String, Long>()

  @Test
  fun mostCommonWords() {
    val postingDB = PostgresPostingDBModel(PostgresProductDBModel())
    postingDB.all().forEach { productPosting ->
      tokens("${productPosting.posting.title} ${productPosting.posting.description}")
        .filter { word -> word.all { c -> c.isLetter() } }
        .forEach { word ->
          wordCountTree.put(word, 1 + (wordCountTree.search(word) ?: 0))
        }
    }

    // TODO something in here is null
    val wordsList = wordCountTree.ordered()?.collect(Collectors.toList())
    val revWordsList = wordsList
      ?.subList(Math.max(0, wordsList.size - topWordCount), wordsList.size)
      ?.sortedBy { it.value }

    revWordsList?.forEach { println(it.key); println(it.value); }
  }
}