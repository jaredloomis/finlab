package com.jaredloomis.silk.db

import java.sql.*

/**
 * A SQL statement with references to an object's members. Values are filled in using a PreparedStatement.
 *
 * ex:
 * > val stmt = SQLStatement(con, "SELECT * FROM products WHERE upc = %upc")
 * >              .prepare(emptyMap().plus("upc", 12))
 * > val res = stmt.executeQuery()
 * > ...
 */
data class SQLStatement(val con: Connection, val template: String) {
  /**
   * @return a ready-to-run PreparedStatment, with all values filled in.
   */
  fun prepare(model: Any): PreparedStatement {
    val fields = reflectToMap(model)
    // Find all variables
    val varRegex = Regex("%([A-Za-z][A-Za-z0-9_.]+)")
    val variables = varRegex.findAll(template).mapNotNull { it.groups[1]?.value }
    // Replace with '?'
    val newTemplate = template.replace(varRegex, "?")
    // Create prepared statement
    val stmt = con.prepareStatement(newTemplate, Statement.RETURN_GENERATED_KEYS)
    // Fill in values
    variables.forEachIndexed { i, varName ->
      val prop = if(!varName.contains('.')) {
        fields[varName]!!
      } else {
        val path = varName.split('.')
        var obj = fields[path[0]]
        for(field in path.subList(1, path.size)) {
          obj = reflectToMap(obj!!.first!!)[field]
        }
        obj!!
      }

      if(prop.first == null) {
        val propType = when(prop.second) {
          // TODO expand supported types
          String::class -> Types.VARCHAR
          Int::class -> Types.INTEGER
          Long::class -> Types.BIGINT
          Date::class -> Types.DATE
          else -> Types.VARCHAR
        }
        stmt.setNull(i+1, propType)
      } else {
        when(prop.first) {
          // TODO expand supported types
          is String -> stmt.setString(i+1, prop.first as String)
          is Int    -> stmt.setInt(i+1, prop.first as Int)
          is Long   -> stmt.setLong(i+1, prop.first as Long)
          is Date   -> stmt.setDate(i+1, prop.first as Date)
        }
      }
    }

    return stmt
  }
}

private fun reflectToMap(obj: Any): Map<String, Pair<Any?, Class<*>>> {
  val map: MutableMap<String, Pair<Any?, Class<*>>> = HashMap()
  for (field in obj.javaClass.declaredFields) {
    field.isAccessible = true
    try {
      map[field.name] = Pair(field[obj], field.type)
    } catch (e: Exception) {
    }
  }
  return map
}
