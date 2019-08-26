package com.jaredloomis.analmark.main

fun tokenize(title: String): List<String> {
  return title.split("\n")
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

private fun indexOfPrime(subStr: String, str: String, caseSensitive: Boolean=true): Int {
  if(caseSensitive) {
    return str.indexOf(subStr)
  }

  var stringStartIndex: Int? = null
  var subStrIndex: Int? = null
  val chars: CharArray = str.toCharArray()
  for(i in (0 until chars.size)) {
    val c = chars[i]
    // When we see the first char of subStr, set tracking variables
    if(c == subStr[0]) {
      subStrIndex = 1
      stringStartIndex = i
    }
    // Continue checking the rest of the characters, and return start index if all match
    else if(subStrIndex != null) {
      if(subStrIndex > subStr.length-1)
        return stringStartIndex!!
      if(c != subStr[subStrIndex])
        subStrIndex = -1
      ++subStrIndex
    }
  }
  return -1
}