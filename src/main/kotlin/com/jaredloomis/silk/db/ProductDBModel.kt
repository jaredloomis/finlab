package com.jaredloomis.silk.db

import com.jaredloomis.silk.model.product.Brand
import com.jaredloomis.silk.model.product.Product
import com.jaredloomis.silk.model.product.ProductID
import com.jaredloomis.silk.model.product.RawPosting
import com.jaredloomis.silk.nlp.wordsInCommon
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.singleton
import org.postgresql.util.PSQLException
import java.sql.ResultSet
import java.sql.Types
import java.util.logging.Logger
import java.util.stream.Stream

class DummyProductDBModel : DBModel<RawPosting, Product>() {
  val products: MutableSet<Product> = HashSet()

  override fun findByID(id: Long): Product? {
    return null
  }

  override fun find(query: RawPosting): Stream<Product> {
    return products.stream()
      .filter { product -> wordsInCommon(product.canonicalName, query.title).size > 1 }
  }

  override fun all(): Stream<Product> {
    return products.stream()
  }

  override fun insert(entity: Product): Product {
    products.add(entity)
    return entity
  }

  override fun close() {}
}


class PostgresProductDBModel(tableName: String) : PostgresDBModel<RawPosting, Product>(tableName) {
  private val logger = Logger.getLogger(this::class.qualifiedName)

  constructor() : this("products") {}

  override fun insert(entity: Product): Product {
    logger.info("inserting product: $entity")
    // Check if product already exists
    val found = when {
      entity.upc != null     -> findByProductID(ProductID.UPC(entity.upc!!))
      entity.modelID != null -> findByProductID(ProductID.BrandModel(entity.primaryBrand.name, entity.modelID!!))
      else                   -> null
    }
    logger.info("FOUND EXISTING ENTRY $found")
    if (found != null) {
      val ret = found.merge(entity)

      logger.info("PostgresProductDBModel.insert: merging products into $ret")

      val updateSql = """
        UPDATE $tableName
        SET product_name = %first.canonicalName, brand = %first.primaryBrand.name, modelID = %first.modelID, upc = %first.upc, category = %first.category
        WHERE id = %second.id
      """.trimIndent()
      val con = connect()
      val stmt = SQLStatement(con, updateSql).prepare(Pair(entity, found))
      try {
        stmt.executeUpdate()
      } catch (ex: PSQLException) {
        if (ex.message?.contains("duplicate key value violates unique constraint") == true) {
          logger.info("Ignoring duplicate name error. Throwing away entry $entity")
        } else {
          throw ex
        }
      } finally {
        stmt.close()
      }
      return ret
    }

    val insertSQL = """
      INSERT INTO $tableName (id, product_name, brand, modelid, upc, category)
      VALUES (DEFAULT, %canonicalName, %primaryBrand.name, %modelID, %upc, %category);
    """
    val con = connect()
    val stmt = SQLStatement(con, insertSQL).prepare(entity)

    try {
      stmt.executeUpdate()
      if (stmt.generatedKeys.next()) {
        entity.id = stmt.generatedKeys.getLong(1)
      }
    } catch (ex: PSQLException) {
      if (ex.message?.contains("duplicate key value violates unique constraint") == true) {
        logger.info("Ignoring duplicate name error. Throwing away entry $entity")
      } else {
        throw ex
      }
    } finally {
      stmt.close()
    }

    return entity
  }

  override fun findByID(id: Long): Product? {
    var ret: Product? = null
    val querySQL = "SELECT * FROM $tableName WHERE id=${id}"

    val con = connect()
    val stmt = con.createStatement()
    val results = stmt.executeQuery(querySQL)
    if (results.next()) {
      ret = parseProduct(results)
    }
    results.close()
    stmt.close()
    return ret
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
      val product = parseProduct(results)
      results.close()
      stmt.close()
      product
    } else {
      results.close()
      stmt.close()
      null
    }
  }

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
      matches.add(parseProduct(results))
    }
    results.close()
    stmt.close()

    return matches.stream()
  }

  override fun all(): Stream<Product> {
    // TODO true streaming implementation
    val matches = ArrayList<Product>()
    val querySQL = "SELECT * FROM $tableName"

    val con = connect()
    val stmt = con.createStatement()
    val results = stmt.executeQuery(querySQL)
    while (results.next()) {
      matches.add(parseProduct(results))
    }
    results.close()
    stmt.close()

    return matches.stream()
  }

  override fun createTableSQL(): SQLSource {
    return SQLSource.Resource(this::class.java.classLoader.getResource("sql/product/create.sql")!!)
  }
}

private fun parseProduct(rs: ResultSet): Product {
  val id = rs.getLong("id")
  val productName = rs.getString("product_name")
  val productBrand = rs.getString("brand")
  return Product(id, productName, Brand(productBrand))
}

val productDBModule = DI.Module("ProductDBModel") {
  bind<PostgresProductDBModel>() with singleton { PostgresProductDBModel() }
}