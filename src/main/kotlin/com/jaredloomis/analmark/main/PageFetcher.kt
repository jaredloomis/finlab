package com.jaredloomis.analmark.main

abstract class PageFetcher {
  abstract fun init()
  abstract fun fetch(): String
  abstract fun quit()
}