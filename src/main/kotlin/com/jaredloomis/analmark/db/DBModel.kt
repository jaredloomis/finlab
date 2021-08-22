package com.jaredloomis.analmark.db

import com.jaredloomis.analmark.model.CurrencyAmount
import com.jaredloomis.analmark.model.product.*
import com.jaredloomis.analmark.nlp.wordsInCommon
import com.jaredloomis.analmark.util.getLogger
import com.jaredloomis.analmark.scrape.product.ProductMarketType
import org.postgresql.util.PSQLException
import java.sql.*
import java.util.logging.Logger
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

abstract class PostgresDBModel<Q, T>(val tableName: String) : DBModel<Q, T>() {
  var connection: Connection? = null

  override fun init() {
    ensureTableExists()
  }

  override fun close() {
    connection?.close()
  }

  abstract fun ensureTableExists()

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

class PostgresPostingDBModel(
  val productDBModel: DBModel<RawPosting, Product>,
  tableName: String = "public.postings", val productTableName: String = "public.products"
) : PostgresDBModel<Product, ProductPosting>(tableName) {
  private val ID_COLUMN_NAME = "id"
  private val MARKET_COLUMN_NAME = "market"
  private val URL_COLUMN_NAME = "url"
  private val PRODUCT_COLUMN_NAME = "product"
  private val TITLE_COLUMN_NAME = "title"
  private val PRICE_COLUMN_NAME = "price"
  private val DESCRIPTION_COLUMN_NAME = "description"
  private val SPECS_COLUMN_NAME = "specs"
  private val SEEN_COLUMN_NAME = "seenAt"

  private val logger = getLogger(this::class)

  override fun insert(entity: ProductPosting): ProductPosting {
    // Check if product already exists
    val found = findByText(entity.posting.title)
    if (found != null) {
      val ret = found.merge(entity)

      logger.info("PostgresPostingDBModel.insert: merging products into $ret")

      val insertSQL = """
        UPDATE $tableName
        SET url = ?, product = ?, title = ?, price = ?, description = ?, specs = ?, seenAt = ?
        WHERE id = ?
      """.trimIndent()
      val con = connect()
      val stmt = con.prepareStatement(insertSQL, Statement.RETURN_GENERATED_KEYS)
      stmt.setString(1, ret.posting.url)
      if (ret.product.id != null) {
        stmt.setLong(2, ret.product.id!!)
      } else {
        stmt.setNull(2, Types.BIGINT)
      }
      stmt.setString(3, ret.posting.title)
      stmt.setLong(4, ret.posting.price.pennies)
      stmt.setString(5, ret.posting.description)
      stmt.setString(6, ret.posting.specs
        .map { entry -> "${entry.key}=${entry.value}" }
        .fold(StringBuilder()) { acc, entryStr -> acc.append(entryStr).append(",") }
        .toString()
      )
      stmt.setDate(7, Date(ret.posting.seen.toEpochMilli()))
      // Posting is from db, so should have id
      stmt.setLong(8, ret.id!!)
      stmt.executeUpdate()
      stmt.close()
      return ret
    }

    val product = productDBModel.insert(entity.product)
    val insertSQL = "INSERT INTO $tableName VALUES (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?);"
    val con = connect()
    val stmt = con.prepareStatement(insertSQL, Statement.RETURN_GENERATED_KEYS)
    stmt.setString(1, entity.posting.market.name)
    stmt.setString(2, entity.posting.url)
    if (entity.product.id != null)
      stmt.setLong(3, entity.product.id!!)
    else
      stmt.setNull(3, Types.BIGINT)
    stmt.setString(4, entity.posting.title)
    stmt.setLong(5, entity.posting.price.pennies)
    stmt.setString(6, entity.posting.description)
    stmt.setString(7, entity.posting.specs.entries
      .map { entry -> "${entry.key}=${entry.value}" }
      .fold(StringBuilder()) { acc, entryStr -> acc.append(entryStr).append(",") }
      .toString()
    )
    stmt.setDate(8, Date(entity.posting.seen.toEpochMilli()))
    var id: Long? = null
    try {
      stmt.executeUpdate()
      if (stmt.generatedKeys.next()) {
        id = stmt.generatedKeys.getLong(1)
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
    stmt.close()
    return ProductPosting(id, product, entity.posting)
  }

  override fun findByID(id: Long): ProductPosting? {
    // TODO streaming implementation
    val matches = ArrayList<ProductPosting>()
    val querySQL = "SELECT * FROM $tableName WHERE $ID_COLUMN_NAME=${id}"

    val con = connect()
    val stmt = con.createStatement()
    val results = stmt.executeQuery(querySQL)
    if (results.next()) {
      return parseProductPosting(results)
    }
    results.close()
    stmt.close()

    return null
  }

  override fun find(query: Product): Stream<ProductPosting> {
    // TODO streaming implementation
    val matches = ArrayList<ProductPosting>()
    val querySQL = """
      SELECT a.*, b.product_name, b,brand FROM $tableName a
      LEFT JOIN $productTableName b ON a.product = b.id
      WHERE
        a.id = ? OR
        to_tsvector(LOWER(title)) @@ to_tsquery(LOWER(?)) OR
        to_tsvector(LOWER(title)) @@ to_tsquery(LOWER(?)) OR
        to_tsvector(LOWER(description)) @@ to_tsquery(LOWER(?)) OR
        to_tsvector(LOWER(description)) @@ to_tsquery(LOWER(?)) OR
        to_tsvector(LOWER(product_name)) @@ to_tsquery(LOWER(?)) OR
        to_tsvector(LOWER(brand)) @@ to_tsquery(LOWER(?))
    """.trimIndent()

    val con = connect()
    val stmt = con.prepareStatement(querySQL)
    if (query.id != null)
      stmt.setLong(1, query.id!!)
    else
      stmt.setNull(1, Types.BIGINT)
    stmt.setString(2, matcherString(query.canonicalName))
    stmt.setString(3, matcherString(query.primaryBrand.name))
    stmt.setString(4, matcherString(query.primaryBrand.name))
    stmt.setString(5, matcherString(query.canonicalName))
    stmt.setString(6, matcherString(query.canonicalName))
    stmt.setString(7, matcherString(query.primaryBrand.name))
    val results = stmt.executeQuery()
    while (results.next()) {
      val post = parseProductPosting(results)
      if (post != null) {
        matches.add(post)
      } else {
        System.err.println("Failed to parse product posting from postings table.")
      }
    }
    results.close()
    stmt.close()

    return matches.stream()
  }

  private fun findByText(text: String): ProductPosting? {
    var ret: ProductPosting? = null
    val querySQL = "SELECT * FROM $tableName WHERE title ILIKE ?"

    val con = connect()
    val stmt = con.prepareStatement(querySQL)
    stmt.setString(1, matcherString(text))
    val results = stmt.executeQuery()
    if (results.next()) {
      ret = parseProductPosting(results)
    }
    results.close()
    stmt.close()
    return ret
  }

  override fun all(): Stream<ProductPosting> {
    // TODO streaming implementation
    val matches = ArrayList<ProductPosting>()
    val querySQL = "SELECT * FROM $tableName"

    val con = connect()
    val stmt = con.createStatement()
    val results = stmt.executeQuery(querySQL)
    while (results.next()) {
      val post = parseProductPosting(results)
      if (post != null) {
        matches.add(post)
      }
    }
    results.close()
    stmt.close()

    return matches.stream()
  }

  private fun parseProductPosting(results: ResultSet): ProductPosting? {
    val id = results.getLong(ID_COLUMN_NAME)
    val marketStr = results.getString(MARKET_COLUMN_NAME)
    val url = results.getString(URL_COLUMN_NAME)
    val productID = results.getLong(PRODUCT_COLUMN_NAME)
    val title = results.getString(TITLE_COLUMN_NAME)
    val priceCents = results.getLong(PRICE_COLUMN_NAME)
    val description = results.getString(DESCRIPTION_COLUMN_NAME)
    val specsStr = results.getString(SPECS_COLUMN_NAME)
    val seenAt = results.getDate(SEEN_COLUMN_NAME)
    val market = ProductMarketType.valueOf(marketStr)
    val price = CurrencyAmount(priceCents)
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
    val rawPost = RawPosting(market, url, title, description, price, specs)
    val product = productDBModel.findByID(productID)
    return if (product != null) {
      ProductPosting(id, product, rawPost)
    } else {
      null
    }
  }

  override fun ensureTableExists() {
    val postingsSQL =
      """
      CREATE TABLE IF NOT EXISTS $tableName (
        $ID_COLUMN_NAME          SERIAL PRIMARY KEY,
        $MARKET_COLUMN_NAME      VARCHAR(10) NOT NULL,
        $URL_COLUMN_NAME         TEXT NOT NULL,
        $PRODUCT_COLUMN_NAME     BIGINT REFERENCES $productTableName(id),
        $TITLE_COLUMN_NAME       TEXT NOT NULL,
        $PRICE_COLUMN_NAME       BIGINT NOT NULL,
        $DESCRIPTION_COLUMN_NAME TEXT,
        $SPECS_COLUMN_NAME       TEXT,
        $SEEN_COLUMN_NAME        TIMESTAMP,
        UNIQUE ($TITLE_COLUMN_NAME, $SEEN_COLUMN_NAME, $MARKET_COLUMN_NAME)
      );
      """.trimIndent()
    val con = connect()
    val stmt = con.createStatement()
    stmt.execute(postingsSQL)
    stmt.close()
  }
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
        println("ID ${entity.id}\n\n\n\n\n\n\n\n\n")
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
    if (results.next()) {
      return parseProduct(results)
    }
    results.close()
    stmt.close()
    return null
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

  override fun ensureTableExists() {
    val productsSQL =
      """
      CREATE TABLE IF NOT EXISTS $tableName (
        id           SERIAL PRIMARY KEY,
        product_name TEXT UNIQUE NOT NULL,
        brand        TEXT NOT NULL,
        category     TEXT,
        modelID      TEXT,
        upc          TEXT,
        tags         TEXT
      );
      """.trimIndent()
    val con = connect()
    val stmt1 = con.createStatement()
    stmt1.execute(productsSQL)
    stmt1.close()
  }
}

private fun matcherString(input: String): String {
  if (input.isEmpty()) return input
  return "%" + input
    .replace("!", "!!")
    .replace("%", "!%")
    .replace("_", "!_")
    .replace("[", "![") + "%"
}

private fun parseProduct(rs: ResultSet): Product {
  val id = rs.getLong("id")
  val productName = rs.getString("product_name")
  val productBrand = rs.getString("brand")
  return Product(id, productName, Brand(productBrand))
}
