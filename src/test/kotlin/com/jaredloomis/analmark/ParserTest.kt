package com.jaredloomis.analmark

import com.jaredloomis.analmark.nlp.removeSubstring
import com.jaredloomis.analmark.nlp.tokens
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ParserTest {
  @Test
  fun tokenizeSimple() {
    val tokens = tokens("wasabi hello world whatup")
    assert(tokens.size == 4)
    assert(tokens[2] == "world")
  }

  @Test
  fun removeSubstringSimple() {
    val ret1 = removeSubstring("hell", "HELLO, world", caseSensitive = false)
    assert(ret1 == "O, world") {"'$ret1' should be 'O, world'"}
    val ret2 = removeSubstring("", "HELLO, world", caseSensitive = false)
    assert(ret2 == "HELLO, world") {"'$ret2' should be 'HELLO, world'"}
    val ret3 = removeSubstring("asdf", "", caseSensitive = false)
    assert(ret3 == "") {"'$ret3' should be ''"}
  }
}