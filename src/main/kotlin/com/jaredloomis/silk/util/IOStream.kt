package com.jaredloomis.silk.util

import com.jaredloomis.silk.di
import org.kodein.di.instance
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue

class IOStream<T> {
  val executor by di.instance<ExecutorService>()
  private val queue = LinkedBlockingQueue<T>()
  private val sources: MutableList<IOSource<T>> = LinkedList()
  private val subscriptions: MutableList<(T) -> Unit> = LinkedList()

  fun source(src: IOSource<T>) {
    sources.add(src)
  }

  fun subscribe(cb: (T) -> Unit) {
    subscriptions.add(cb)
  }

  fun run(limit: Int=Int.MAX_VALUE) {
    try {
      for (source in sources) {
        source.open()
      }

      executor.submit {
        while (!executor.isShutdown) {
          val item = queue.take()
          for (sub in subscriptions) {
            sub(item)
          }
        }
      }

      repeat(limit) {
        for (source in sources) {
          for (item in source.fetch()) {
            queue.add(item)
          }
        }
      }
    } catch(ex: Exception) {
      ex.printStackTrace()
    } finally {
      for (source in sources) {
        source.close()
      }
    }
  }
}

class IOSource<T>(val open: () -> Unit, val fetch: () -> List<T>, val close: () -> Unit)