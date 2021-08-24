package com.jaredloomis.silk.util

fun retry(n: Int, f: () -> Unit) {
  repeat(n) {
    try {
      return f()
    } catch (ex: Throwable) { }
  }
}