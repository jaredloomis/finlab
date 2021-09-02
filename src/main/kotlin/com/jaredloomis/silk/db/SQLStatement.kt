package com.jaredloomis.silk.db

import com.jaredloomis.silk.util.getLogger
import java.lang.reflect.Type
import java.sql.*
import kotlin.reflect.KType
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.javaType
import kotlin.reflect.jvm.jvmErasure

/**
 * A SQL statement with references to a Java object's members. Values are filled in using a PreparedStatement.
 *
 * ex:
 * > val stmt = SQLStatement(con, "SELECT * FROM products WHERE upc = %upc")
 * >              .prepare(emptyMap().plus("upc", 12))
 * > val res = stmt.executeQuery()
 * > ...
 */
data class SQLStatement(val template: String) {
  val log = getLogger(this::class)

  /**
   * @return a ready-to-run PreparedStatment, with all values filled in.
   */
  fun prepare(con: Connection, model: Any?): PreparedStatement {
    val fields = if(model == null) emptyMap() else reflectToMap(model)
    // Find all variables (%varName)
    val varRegex = Regex("%([A-Za-z][A-Za-z0-9_.]+)")
    val variables = varRegex.findAll(template).mapNotNull { it.groups[1]?.value }
    // Replace with '?'
    val newTemplate = template.replace(varRegex, "?")
    // Create prepared statement
    val stmt = con.prepareStatement(newTemplate, Statement.RETURN_GENERATED_KEYS)
    // Fill in values
    variables.forEachIndexed { i, varName ->
      val prop = try {
        if (!varName.contains('.')) {
          fields[varName]!!
        } else {
          val path = varName.split('.')
          var obj = fields[path[0]]
          for (field in path.subList(1, path.size)) {
            obj = reflectToMap(obj!!.first!!)[field]
          }
          obj!!
        }
      } catch(ex: Exception) {
        ex.printStackTrace()
        log.warning("Couldn't find variable '$varName' referenced in SQL template:\n$template\nmodel: $model")
        Pair(null, null)
      }

      if(prop.first == null) {
        val propType = when(prop.second?.kotlin) {
          // TODO expand supported types
          String::class -> Types.VARCHAR
          Int::class    -> Types.INTEGER
          Long::class   -> Types.BIGINT
          Date::class   -> Types.DATE
          else          -> throw Exception("Unrecognized type (${prop.second}) referenced in a SQL template:\n$template\nmodel: $model")
        }
        stmt.setNull(i+1, propType)
      } else {
        when(prop.first) {
          // TODO expand supported types
          is String -> stmt.setString(i+1, prop.first as String)
          is Int    -> stmt.setInt(i+1, prop.first as Int)
          is Long   -> stmt.setLong(i+1, prop.first as Long)
          is Date   -> stmt.setDate(i+1, prop.first as Date)
          else      -> throw Exception("Unrecognized type referenced in a SQL template:\n$template\nmodel: $model")
        }
      }
    }

    return stmt
  }
}

fun reflectToMap(obj: Any): Map<String, Pair<Any?, Class<*>>> {
  val map: MutableMap<String, Pair<Any?, Class<*>>> = HashMap()
  /*
  for (field in obj.javaClass.declaredFields) {
    field.isAccessible = true
    try {
      map[field.name] = Pair(field[obj], field.type)
    } catch (e: Exception) {
    }
  }*/
  for (field in obj::class.memberProperties) {
    try {
      val v = field.call(obj)
      field.returnType.jvmErasure.java
      map[field.name] = Pair(v, /*if(v == null) null else v::class.java*/ field.returnType.jvmErasure.java)
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }
  return map
}
