package com.jaredloomis.silk.db

import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.util.stream.Stream

abstract class DBModel<Q, T> {
  abstract fun findByID(id: Long): T?
  abstract fun find(query: Q): Stream<T>
  abstract fun all(): Stream<T>
  abstract fun insert(entity: T): T
  abstract fun close()
  open fun init() {}

  open fun findOne(query: Q): T? {
    return find(query).findFirst().orElse(null)
  }
}

abstract class PostgresDBModel<Q, T>(val tableName: String) : DBModel<Q, T>() {
  var connection: Connection? = null

  /**
   * @return SQL CREATE IF NOT EXISTS table schema.
   */
  abstract fun createTableSQL(): SQLSource

  override fun init() {
    ensureTableExists()
  }

  override fun close() {
    connection?.close()
  }

  protected fun ensureTableExists() {
    val con = connect()
    val stmt1 = con.createStatement()
    stmt1.execute(SQLSource.toSQL(createTableSQL()))
    stmt1.close()
  }

  protected fun connect(): Connection {
    return if(connection != null) {
      connection!!
    } else {
      val con = DriverManager.getConnection("jdbc:postgresql://localhost/market_analysis", "postgres", "")
      connection = con
      con
    }
  }
}

sealed class SQLSource {
  data class Resource(val url: URL) : SQLSource()
  data class File(val path: Path) : SQLSource()
  data class Text(val text: String) : SQLSource()

  companion object {
    fun toSQL(src: SQLSource): String {
      return when (src) {
        is Resource -> src.url.readText()
        is File -> Files.readString(src.path)
        is Text -> src.text
      }
    }
  }
}

fun matcherString(input: String): String {
  if (input.isEmpty()) return input
  return "%" + input
    .replace("!", "!!")
    .replace("%", "!%")
    .replace("_", "!_")
    .replace("[", "![") + "%"
}

val dbModelModule = DI.Module("DBModel") {
  import(postingDBModule)
}
