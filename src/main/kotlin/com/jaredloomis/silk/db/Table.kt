package com.jaredloomis.silk.db

import com.jaredloomis.silk.model.CurrencyAmount
import com.jaredloomis.silk.model.ProductMatcher
import com.jaredloomis.silk.model.product.*
import com.jaredloomis.silk.scrape.product.ProductMarketType
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
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

  operator fun get(id: Long): T? {
    val sql = "SELECT * FROM $tableName WHERE id = %it"
    return query(SQLStatement(sql), id).findAny().orElse(null)
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
        item.upc != null     -> findByProductID(ProductKey.UPC(item.upc!!))
        item.modelID != null -> findByProductID(ProductKey.BrandModel(item.primaryBrand.name, item.modelID!!))
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

  private fun findByProductID(key: ProductKey): Product? {
    val con = connect()
    val stmt = when(key) {
      is ProductKey.BrandModel -> {
        val stmt = con.prepareStatement("SELECT * FROM $tableName WHERE brand = ? AND modelid = ?")
        stmt.setString(1, key.brand)
        stmt.setString(2, key.model)
        stmt
      }
      is ProductKey.UPC -> {
        val stmt = con.prepareStatement("SELECT * FROM $tableName WHERE upc = ?")
        stmt.setString(1, key.upc)
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

class PostingTable(tableName: String, val productTable: Table<Product>)
  : Table<ProductPosting>(tableName) {
  private val logger = Logger.getLogger(this::class.simpleName)

  constructor(productTable: Table<Product>) : this("postings", productTable) {}

  override fun insert(item: ProductPosting): ProductPosting? {
    try {
      logger.info("inserting product: $item")
      val insertSQL = """
        INSERT INTO $tableName (id, product, market, url, title, price, description, specs)
        VALUES (DEFAULT, %product.id, %posting.market, %posting.url, %posting.title, %posting.price.pennies, %posting.description, %posting.specsStr);
      """
      update(SQLStatement(insertSQL), item)
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

  override fun parseItem(results: ResultSet): ProductPosting {
    val id = results.getLong("id")
    val productID = results.getLong("product")
    val market = results.getString("market")
    val url = results.getString("url")
    val title = results.getString("title")
    val price = results.getString("price")
    val description = results.getString("description")
    val specsStr = results.getString("specs")
    val specs = specsStr.split(",")
      .mapNotNull { specStr ->
        val splitSpec = specStr.split("=")
        if (splitSpec.isEmpty()) {
          null
        } else {
          Pair(splitSpec[0], if (splitSpec.size >= 2) splitSpec[1] else "")
        }
      }
      .fold(HashMap<String, String>() as Map<String, String>) { acc, spec -> acc.plus(spec) }
    val post = RawPosting(ProductMarketType.valueOf(market), url, title, description, CurrencyAmount(price), specs)
    post.id = id
    val product = productTable[productID]!!
    return ProductPosting(product, post)
  }

  override fun createTableSQL(): SQLSource {
    return SQLSource.Resource(this::class.java.classLoader.getResource("sql/product/create.sql")!!)
  }
}

val tableModule = DI.Module("Table") {
  bind<Table<Product>>() with singleton {
    val table = ProductTable()
    table.open()
    table
  }

  bind<Table<ProductPosting>>() with singleton {
    val table = PostingTable(instance())
    table.open()
    table
  }
}