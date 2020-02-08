package com.jaredloomis.analmark.model

import java.util.stream.Stream

data class EdgeGraph<Node, T>(private val graphMap: Map<Node, Map<Node, T>>) {
  constructor() : this(HashMap())

  fun path(start: Node, end: Node): List<T>? {
    return paths(start, end)
      .sorted { a, b -> a.size.compareTo(b.size) }
      .findFirst()
      .orElse(null)
  }

  fun paths(start: Node, end: Node): Stream<List<T>> {
    return _paths(start, end, HashSet())
  }

  fun insert(start: Node, end: Node, edgeValue: T): EdgeGraph<Node, T> {
    return EdgeGraph(graphMap.plus(Pair(start, getDestMap(start).plus(Pair(end, edgeValue)))))
  }

  private fun _paths(start: Node, end: Node, visited: Set<Node>): Stream<List<T>> {
    return getDestMap(start).entries.stream()
      .filter { visited.contains(it.key) }
      .flatMap { entry ->
        if (entry.key == end) {
          Stream.of(listOf<T>().plus(entry.value))
        } else {
          _paths(entry.key, end, visited.plus(start))
        }
      }
  }

  private fun getDestMap(start: Node): Map<Node, T> {
    return graphMap[start] ?: HashMap()
  }
}