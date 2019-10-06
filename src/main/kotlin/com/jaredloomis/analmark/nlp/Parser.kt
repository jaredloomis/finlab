package com.jaredloomis.analmark.nlp

import opennlp.tools.stemmer.PorterStemmer

private val stemmer = PorterStemmer()

fun wordsInCommon(reference: String, test: String): List<String> {
  val refToks = tokenize(reference)
  return tokenize(test)
    .filter {token ->
      val lcTok = token.toLowerCase()
      refToks.any {it.toLowerCase() == lcTok}
    }
}

fun tokenize(title: String): List<String> {
  return title.split(" ")
    .filter {token -> token.isNotEmpty()}
}

fun removeSubstring(subStr: String, str: String, caseSensitive: Boolean=true): String {
  var ret: String = str
  var startIndex = indexOfPrime(subStr, str, caseSensitive)
  while(startIndex != -1 && subStr.isNotEmpty()) {
    ret = ret.removeRange(startIndex, startIndex + subStr.length)
    startIndex = ret.indexOf(subStr)
  }
  return ret
}

fun List<String>.containsIgnoreCase(str: String): Boolean {
  val lowStr = str.toLowerCase()
  for(i in this.indices) {
    if(this[i].toLowerCase() == lowStr) {
      return true
    }
  }
  return false
}

fun stem(str: String): String {
  stemmer.stem(str)
  return stemmer.toString()
}

// A6000
fun parseTechnicalModelIDs(str: String): String? {
  val match = Regex("[a-zA-Z0-9]*[a-zA-Z][0-9\\-][a-zA-Z0-9\\-]*").find(str)
  return match?.value
}

private fun indexOfPrime(subStr: String, str: String, caseSensitive: Boolean=true): Int {
  if(caseSensitive) {
    return str.indexOf(subStr)
  } else if(subStr.isEmpty()) {
    return -1
  }

  val startChar = subStr[0].toLowerCase()
  var stringStartIndex: Int? = null
  var subStrIndex: Int? = null
  for(i in str.indices) {
    val c = str[i]
    // Continue checking the rest of the characters, and return start index if all match
    if(subStrIndex != null) {
      subStrIndex = when {
        subStrIndex > subStr.length-1                        -> return stringStartIndex!!
        c.toLowerCase() != subStr[subStrIndex].toLowerCase() -> null
        else                                                 -> subStrIndex + 1
      }
    }
    // When we see the first char of subStr, set tracking variables
    else if(c.toLowerCase() == startChar) {
      subStrIndex = 1
      stringStartIndex = i
    }
  }
  return -1
}