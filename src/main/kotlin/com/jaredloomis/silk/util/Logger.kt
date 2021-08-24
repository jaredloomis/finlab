package com.jaredloomis.silk.util

import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Paths
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import java.util.logging.*
import java.util.logging.Formatter
import kotlin.reflect.KClass

// A tag used to uniquely identify this program execution
val executionTag = Date().toString().replace(':', '.')

// Formatting of log messages
val formatter = CustomFormatter("ANALMARK [%1\$tF %1\$tT] [%4\$-7s] %5\$s %n")

// Console
val ch = {
  val c = ConsoleHandler()
  c.formatter = formatter
  c
}()
//logger.addHandler(ch)
// File
val fh = {
  val h = FileHandler("logs/$executionTag.log")
  h.encoding = "UTF-8"
  h.formatter = formatter
  h
}()

fun <T : Any> getLogger(klass: Class<T>): Logger {
  // Create log dir
  Files.createDirectories(Paths.get("logs"))

  // Create logger, remove existing handlers
  val logger = Logger.getLogger(klass.name)
  logger.handlers.forEach { logger.removeHandler(it) }
  logger.level = Level.ALL

  logger.addHandler(ch)
  logger.addHandler(fh)

  return logger
}

fun <T : Any> getLogger(klass: KClass<T>): Logger {
  return getLogger(klass.java)
}

open class CustomFormatter(val format: String) : Formatter() {
  override fun format(record: LogRecord): String {
    val zdt = ZonedDateTime.ofInstant(record.instant, ZoneId.systemDefault())
    var source: String
    if (record.sourceClassName != null) {
      source = record.sourceClassName
      if (record.sourceMethodName != null) {
        source = source + " " + record.sourceMethodName
      }
    } else {
      source = record.loggerName
    }

    val message = this.formatMessage(record)
    var throwable = ""
    if (record.thrown != null) {
      val sw = StringWriter()
      val pw = PrintWriter(sw)
      pw.println()
      record.thrown.printStackTrace(pw)
      pw.close()
      throwable = sw.toString()
    }

    return String.format(this.format, zdt, source, record.loggerName, record.level.localizedName, message, throwable)
  }
}