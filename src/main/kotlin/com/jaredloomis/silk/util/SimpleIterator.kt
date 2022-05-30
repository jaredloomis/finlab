package com.jaredloomis.silk.util

class SimpleIterator<T>(private val hasNextV: () -> Boolean, private val nextV: () -> T) : Iterator<T> {
  override fun hasNext(): Boolean {
    return hasNextV()
  }

  override fun next(): T {
    return nextV()
  }
}