package com.jaredloomis.analmark.util

import java.util.stream.Stream

class BinarySearchTree<K: Comparable<K>, V> {
  var root: BSTNode<K, V>? = null

  fun put(key: K, value: V) {
    if(root == null) {
      root = BSTNode(key, value, null, null)
    }

    root = root!!.put(key, value)
  }

  fun search(searchKey: K): V? {
    return root?.search(searchKey)
  }

  fun ordered(): Stream<BSTNode<K, V>>? {
    return root?.ordered()
  }
}

class BSTNode<K: Comparable<K>, V>(val key: K?, var value: V?, var left: BSTNode<K, V>?, var right: BSTNode<K, V>?) {
  constructor(): this(null, null, null, null) {}

  fun put(key: K, value: V): BSTNode<K, V> {
    return if(key == this.key) {
      BSTNode(key, value, left, right)
    } else if(this.key != null && key <= this.key) {
      BSTNode(this.key, this.value,
        left?.put(key, value) ?: BSTNode(key, value, null, null),
        right
      )
    } else {
      BSTNode(this.key, this.value,
        left,
        right?.put(key, value) ?: BSTNode(key, value, null, null)
      )
    }
  }

  fun search(searchKey: K): V? {
    return matches {key, _ -> key == searchKey}.findFirst().orElse(null)?.value
    //return searchNode(searchKey)?.value
  }

  /**
   * Travel least to greatest
   */
  fun ordered(): Stream<BSTNode<K, V>> {
    return matches {_, _ -> true}
    /*
    val ret = LinkedList<Pair<K, V>>()
    // Add values to left
    if(left != null) {
      ret.addAll(left!!.ordered())
    }
    // Add this entry
    if(this.key != null) {
      ret.add(Pair(this.key, this.value!!))
    }
    // Add values to the right
    if(right != null) {
      ret.addAll(right!!.ordered())
    }
    return ArrayList(ret)*/
  }

  fun matches(f: (key: K, value: V) -> Boolean): Stream<BSTNode<K, V>> {
    return Stream.concat(
      Stream.concat(
        left?.matches(f) ?: Stream.empty(),
        if(key != null && value != null && f(key, value!!)) Stream.of(this) else Stream.empty()
      ),
      right?.matches(f) ?: Stream.empty()
    )
  }

  fun map(f: (key: K, value: V) -> V): BSTNode<K, V> {
    val leftp    = left?.map(f)
    val newValue = if(key != null && value != null) f(key, value!!) else value
    val rightp   = right?.map(f)
    return BSTNode(key, newValue, leftp, rightp)
  }

  private fun searchNode(searchKey: K): BSTNode<K, V>? {
    return when {
      searchKey == this.key                     -> this
      this.key != null && searchKey <= this.key -> left?.searchNode(searchKey)
      else                                      -> right?.searchNode(searchKey)
    }
  }
}