package com.jaredloomis.analmark.util

import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.logging.*
import kotlin.reflect.KClass

// A tag used to uniquely identify this program execution
val executionTag = Date().toString().replace(':', '.')

fun <T: Any> getLogger(klass: Class<T>): Logger {
  // Create log dir
  Files.createDirectories(Paths.get("logs"))

  // Create logger, and add handlers
  val logger = Logger.getLogger(klass.name)

  // Create formatters
  System.setProperty("java.util.logging.SimpleFormatter.format", "ANALMARK [%1\$tF %1\$tT] [%4\$-7s] %5\$s %n")
  val consoleFormatter = SimpleFormatter()
  System.setProperty("java.util.logging.SimpleFormatter.format", "ANALMARK [%1\$tF %1\$tT] [%2\$.%3\$] [%4\$-7s] %5\$s %n")
  val fileFormatter = SimpleFormatter()

  // Console
  val ch = ConsoleHandler()
  ch.formatter = consoleFormatter
  logger.addHandler(ch)
  // File
  val fh = FileHandler("logs/$executionTag.log")
  fh.formatter = fileFormatter
  logger.addHandler(fh)

  return logger
}

fun <T: Any> getLogger(klass: KClass<T>): Logger {
  return getLogger(klass.java)
}