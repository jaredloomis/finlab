package com.jaredloomis.silk.db

import com.jaredloomis.silk.model.product.Brand
import com.jaredloomis.silk.model.product.Product
import com.jaredloomis.silk.model.product.ProductID
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import org.postgresql.util.PSQLException
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.util.*
import java.util.logging.Logger
import java.util.stream.Stream

abstract class Table<T>(val tableName: String) {
  protected var connection: Connection? = null

  open fun open() {
    ensureTableExists()
  }

  open fun close() {
    connection?.close()
  }

  open fun query(sql: SQLStatement, model: Any?=null): Stream<T> {
    val stmt = sql.prepare(connect(), model)
    stmt.use { stmt ->
      val results = stmt.executeQuery()
      val ret: MutableList<T> = LinkedList()
      while(results.next()) {
        val item = parseItem(results)
        ret.add(item)
      }
      results.close()
      stmt.close()
      return ret.stream()
    }
  }

  fun query(sql: String, model: Any?=null): Stream<T> {
    return query(SQLStatement(sql), model)
  }

  open fun update(sql: SQLStatement, model: Any?=null): Int {
    val stmt = sql.prepare(connect(), model)
    try {
      val updatedCount = stmt.executeUpdate()
      stmt.close()
      return updatedCount
    } catch (ex: PSQLException) {
      throw ex
    } finally {
      stmt.close()
    }
  }

  fun update(sql: String, model: Any?=null): Int {
    return update(SQLStatement(sql), model)
  }

  abstract fun insert(item: T): T?

  protected abstract fun parseItem(results: ResultSet): T
  protected abstract fun createTableSQL(): SQLSource

  protected fun connect(): Connection {
    return if(connection != null) {
      connection!!
    } else {
      val con = DriverManager.getConnection("jdbc:postgresql://localhost/market_analysis", "postgres", "")
      connection = con
      con
    }
  }

  protected fun ensureTableExists() {
    val con = connect()
    val stmt1 = con.createStatement()
    stmt1.execute(SQLSource.toSQL(createTableSQL()))
    stmt1.close()
  }
}

class ProductTable(tableName: String) : Table<Product>(tableName) {
  private val logger = Logger.getLogger(this::class.simpleName)

  constructor() : this("products") {}

  override fun insert(item: Product): Product? {
    try {
      logger.info("inserting product: $item")
      // Check if product already exists
      val found = when {
        item.upc != null     -> findByProductID(ProductID.UPC(item.upc!!))
        item.modelID != null -> findByProductID(ProductID.BrandModel(item.primaryBrand.name, item.modelID!!))
        else                   -> null
      }
      logger.info("FOUND EXISTING ENTRY $found")
      if (found != null) {
        val ret = found.merge(item)

        logger.info("PostgresProductDBModel.insert: merging products into $ret")

        val updateSql = """
          UPDATE $tableName
          SET product_name = %first.canonicalName, brand = %first.primaryBrand.name, modelID = %first.modelID, upc = %first.upc, category = %first.category
          WHERE id = %second.id
        """
        update(SQLStatement(updateSql))
        return item
      }

      val insertSQL = """
        INSERT INTO $tableName (id, product_name, brand, modelid, upc, category)
        VALUES (DEFAULT, %canonicalName, %primaryBrand.name, %modelID, %upc, %category);
      """
      update(SQLStatement(insertSQL))
      return item
    } catch (ex: PSQLException) {
      if (ex.message?.contains("duplicate key value violates unique constraint") == true) {
        logger.info("Ignoring duplicate name error. Throwing away entry $item")
        return null
      } else {
        throw ex
      }
    }
  }

  private fun findByProductID(id: ProductID): Product? {
    val con = connect()
    val stmt = when(id) {
      is ProductID.BrandModel -> {
        val stmt = con.prepareStatement("SELECT * FROM $tableName WHERE brand = ? AND modelid = ?")
        stmt.setString(1, id.brand)
        stmt.setString(2, id.model)
        stmt
      }
      is ProductID.UPC -> {
        val stmt = con.prepareStatement("SELECT * FROM $tableName WHERE upc = ?")
        stmt.setString(1, id.upc)
        stmt
      }
    }

    val results = stmt.executeQuery()
    return if (results.next()) {
      val product = parseItem(results)
      results.close()
      stmt.close()
      product
    } else {
      results.close()
      stmt.close()
      null
    }
  }

  /*
  override fun find(query: RawPosting): Stream<Product> {
    // TODO streaming implementation
    val matches = ArrayList<Product>()
    val querySQL = """
      SELECT * FROM $tableName
      WHERE product_name ILIKE ? OR brand ILIKE ? OR ? ILIKE product_name OR category ILIKE ?
    """

    val con = connect()
    val stmt = con.prepareStatement(querySQL)
    stmt.setString(1, matcherString(query.title))
    if (query.brand != null) {
      stmt.setString(2, matcherString(query.brand!!))
    } else {
      stmt.setNull(2, Types.VARCHAR)
    }
    stmt.setString(3, matcherString(query.description))
    if (query.category != null)
      stmt.setString(4, query.category)
    else
      stmt.setNull(4, Types.VARCHAR)
    val results = stmt.executeQuery()
    while (results.next()) {
      matches.add(parseItem(results))
    }
    results.close()
    stmt.close()

    return matches.stream()
  }*/

  override fun parseItem(results: ResultSet): Product {
    val id = results.getLong("id")
    val productName = results.getString("product_name")
    val productBrand = results.getString("brand")
    return Product(id, productName, Brand(productBrand))
  }

  override fun createTableSQL(): SQLSource {
    return SQLSource.Resource(this::class.java.classLoader.getResource("sql/product/create.sql")!!)
  }
}

val tableModule = DI.Module("Table") {
  bind<Table<Product>>() with singleton {
    val table = ProductTable("products")
    table.open()
    table
  }
}