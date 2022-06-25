package com.jaredloomis.silk.util

fun retry(n: Int, f: () -> Unit) {
  repeat(n) {
    try {
      return f()
    } catch (ex: Throwable) { }
  }
}

fun <T> printExceptions(f: () -> T): T? {
  return try {
    f()
  } catch(ex: Exception) {
    System.err.println("PRINTING CAUGHT EXCEPTION:")
    ex.printStackTrace()
    null
  }
}