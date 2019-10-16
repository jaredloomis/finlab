package com.jaredloomis.analmark.db

import com.jaredloomis.analmark.scrape.MarketType
import com.jaredloomis.analmark.model.*
import com.jaredloomis.analmark.nlp.wordsInCommon
import com.jaredloomis.analmark.util.getLogger
import org.postgresql.util.PSQLException
import java.sql.*
import java.util.logging.Logger
import java.util.stream.Stream

abstract class DBModel<Q, T> {
  abstract fun findByID(id: Long): T?
  abstract fun find(query: Q): Stream<T>
  abstract fun all(): Stream<T>
  abstract fun insert(entity: T): T
  abstract fun findByText(str: String): T?

  open fun findOne(query: Q): T? {
    return find(query).findFirst().orElse(null)
  }
}

/* TODO complete SQLTemplate class. good idea, almost done.
abstract class SQLTemplate<T>(val model: T, val template: String) {
  val logger = getLogger(this::class)

  abstract fun getSQLField(str: String): Any?

  /**
   * @return a ready-to-run PreparedStatment, with all values filled in.
   */
  fun fillIn(model: T, con: Connection): PreparedStatement {
    // Find all variables
    val regex = Regex("\\%([A-Za-z][A-Za-z0-9]+)")
    val variables = regex.findAll(template).mapNotNull {it.groups[1]?.value}
    // Replace with '?'
    val newTemplate = template.replace(regex, "?")
    // Create prepared statement
    val stmt = con.prepareStatement(newTemplate)
    // Fill in values
    variables.forEachIndexed {i, varName ->
      val prop = getSQLField(varName)

      if(prop == null) {
        val propKlass = con.typeMap[varName]!!
        val propType = //when(propKlass)
        stmt.setNull(i, propType)
      } else {
        when(prop) {
          is String -> stmt.setString(i, prop as String)
          //Int::class    -> stmt.setInt(i, prop as Int)
          //Long::class   -> stmt.setLong(i, prop as Long)
        }
      }
    }

    return stmt
  }
}
*/

private fun matcherString(input: String): String {
  if(input.isEmpty()) return input
  return "%" + input
    .replace("!", "!!")
    .replace("%", "!%")
    .replace("_", "!_")
    .replace("[", "![") + "%"
}

private fun parseProduct(rs: ResultSet): Product {
  val id           = rs.getLong("id")
  val productName  = rs.getString("product_name")
  val productBrand = rs.getString("brand")
  return Product(id, productName, Brand(productBrand))
}

class DummyProductDBModel : DBModel<RawPosting, Product>() {
  val products: MutableSet<Product> = HashSet()

  override fun findByID(id: Long): Product? {
    return null
  }

  override fun find(query: RawPosting): Stream<Product> {
    return products.stream()
      .filter {product -> wordsInCommon(product.canonicalName, query.title).size > 1}
  }

  override fun findByText(str: String): Product? {
    return products.stream()
      .filter {product -> product.canonicalName == str}
      .findAny()
      .orElse(null)
  }

  override fun all(): Stream<Product> {
    return products.stream()
  }

  override fun insert(entity: Product): Product {
    products.add(entity)
    return entity
  }
}

class PostgresPostingDBModel(
  val productDBModel: DBModel<RawPosting, Product>,
  val tableName: String="public.postings", val productTableName: String="public.products"
  ) : DBModel<Product, ProductPosting>() {
  private val ID_COLUMN_NAME          = "id"
  private val MARKET_COLUMN_NAME      = "market"
  private val URL_COLUMN_NAME         = "url"
  private val PRODUCT_COLUMN_NAME     = "product"
  private val TITLE_COLUMN_NAME       = "title"
  private val PRICE_COLUMN_NAME       = "price"
  private val DESCRIPTION_COLUMN_NAME = "description"
  private val SPECS_COLUMN_NAME       = "specs"
  private val SEEN_COLUMN_NAME        = "seenAt"

  private val logger = getLogger(this::class)

  init {
    ensureTableExists()
  }

  override fun insert(entity: ProductPosting): ProductPosting {
    // Check if product already exists
    val found = findByText(entity.posting.title)
    if(found != null) {
      val ret = found.merge(entity)

      logger.info("PostgresProductDBModel.insert: merging products into $ret")

      val insertSQL = """
        UPDATE $tableName
        SET url = ?, product = ?, title = ?, price = ?, description = ?, specs = ?, seenAt = ?
        WHERE id = ?
      """.trimIndent()
      val con = connect()
      val stmt = con.prepareStatement(insertSQL, Statement.RETURN_GENERATED_KEYS)
      stmt.setString(1, ret.posting.url)
      if(ret.product.id != null) {
        stmt.setLong(2, ret.product.id!!)
      } else {
        stmt.setNull(2, Types.BIGINT)
      }
      stmt.setString(3, ret.posting.title)
      stmt.setLong(4, ret.posting.price.pennies)
      stmt.setString(5, ret.posting.description)
      stmt.setString(6, ret.posting.specs
        .map {entry -> "${entry.key}=${entry.value}"}
        .fold(StringBuilder()) {acc, entryStr -> acc.append(entryStr).append(",")}
        .toString()
      )
      stmt.setDate(7, Date(ret.posting.seen.toEpochMilli()))
      // Posting is from db, so should have id
      stmt.setLong(8, ret.id!!)
      stmt.executeUpdate()
      stmt.close()
      con.close()
      return ret
    }

    val product = productDBModel.insert(entity.product)
    val insertSQL = "INSERT INTO $tableName VALUES (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?);"
    val con = connect()
    val stmt = con.prepareStatement(insertSQL, Statement.RETURN_GENERATED_KEYS)
    stmt.setString(1, entity.posting.market.name)
    stmt.setString(2, entity.posting.url)
    if(entity.product.id != null)
      stmt.setLong(3, entity.product.id!!)
    else
      stmt.setNull(3, Types.BIGINT)
    stmt.setString(4, entity.posting.title)
    stmt.setLong(5, entity.posting.price.pennies)
    stmt.setString(6, entity.posting.description)
    stmt.setString(7, entity.posting.specs.entries
      .map {entry -> "${entry.key}=${entry.value}"}
      .fold(StringBuilder()) {acc, entryStr -> acc.append(entryStr).append(",")}
      .toString()
    )
    stmt.setDate(8, Date(entity.posting.seen.toEpochMilli()))
    var id: Long? = null
    try {
      stmt.executeUpdate()
      if(stmt.generatedKeys.next()) {
        id = stmt.generatedKeys.getLong(1)
      }
    } catch(ex: PSQLException) {
      if(ex.message?.contains("duplicate key value violates unique constraint") == true) {
        logger.info("Ignoring duplicate name error. Throwing away entry $entity")
      } else {
        throw ex
      }
    } finally {
      stmt.close()
      con.close()
    }
    stmt.close()
    con.close()
    return ProductPosting(id, product, entity.posting)
  }

  override fun findByID(id: Long): ProductPosting? {
    // TODO streaming implementation
    val matches = ArrayList<ProductPosting>()
    val querySQL = "SELECT * FROM $tableName WHERE $ID_COLUMN_NAME=${id}"

    val con     = connect()
    val stmt    = con.createStatement()
    val results = stmt.executeQuery(querySQL)
    if(results.next()) {
      return parseProductPosting(results)
    }
    results.close()
    stmt.close()
    con.close()

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

    val con     = connect()
    val stmt    = con.prepareStatement(querySQL)
    if(query.id != null)
      stmt.setLong(1, query.id!!)
    else
      stmt.setNull(1, java.sql.Types.BIGINT)
    stmt.setString(2, matcherString(query.canonicalName))
    stmt.setString(3, matcherString(query.primaryBrand.name))
    stmt.setString(4, matcherString(query.primaryBrand.name))
    stmt.setString(5, matcherString(query.canonicalName))
    stmt.setString(6, matcherString(query.canonicalName))
    stmt.setString(7, matcherString(query.primaryBrand.name))
    val results = stmt.executeQuery()
    while(results.next()) {
      val post = parseProductPosting(results)
      if(post != null) {
        matches.add(post)
      } else {
        System.err.println("Failed to parse product posting from postings table.")
      }
    }
    results.close()
    stmt.close()
    con.close()

    return matches.stream()
  }

  override fun findByText(str: String): ProductPosting? {
    var ret: ProductPosting? = null
    val querySQL = "SELECT * FROM $tableName WHERE title ILIKE ?"

    val con     = connect()
    val stmt    = con.prepareStatement(querySQL)
    stmt.setString(1, matcherString(str))
    val results = stmt.executeQuery()
    if(results.next()) {
      ret = parseProductPosting(results)
    }
    results.close()
    stmt.close()
    con.close()
    return ret
  }

  override fun all(): Stream<ProductPosting> {
    // TODO streaming implementation
    val matches = ArrayList<ProductPosting>()
    val querySQL = "SELECT * FROM $tableName"

    val con     = connect()
    val stmt    = con.createStatement()
    val results = stmt.executeQuery(querySQL)
    while(results.next()) {
      val post = parseProductPosting(results)
      if(post != null) {
        matches.add(post)
      }
    }
    results.close()
    stmt.close()
    con.close()

    return matches.stream()
  }

  private fun parseProductPosting(results: ResultSet): ProductPosting? {
    val id          = results.getLong(ID_COLUMN_NAME)
    val marketStr   = results.getString(MARKET_COLUMN_NAME)
    val url         = results.getString(URL_COLUMN_NAME)
    val productID   = results.getLong(PRODUCT_COLUMN_NAME)
    val title       = results.getString(TITLE_COLUMN_NAME)
    val priceCents  = results.getLong(PRICE_COLUMN_NAME)
    val description = results.getString(DESCRIPTION_COLUMN_NAME)
    val specsStr    = results.getString(SPECS_COLUMN_NAME)
    val seenAt      = results.getDate(SEEN_COLUMN_NAME)
    val market      = MarketType.valueOf(marketStr)
    val price       = CurrencyAmount(priceCents)
    val specs       = specsStr.split(",")
      .mapNotNull {specStr ->
        val splitSpec = specStr.split("=")
        if(splitSpec.isEmpty()) {
          null
        } else {
          Pair(splitSpec[0], if(splitSpec.size >= 2) splitSpec[1] else "")
        }
      }
      .fold(HashMap<String, String>() as Map<String, String>, {acc, spec -> acc.plus(spec)})
    val rawPost     = RawPosting(market, url, title, description, price, specs)
    val product     = productDBModel.findByID(productID)
    return if(product != null) {
      ProductPosting(id, product, rawPost)
    } else {
      null
    }
  }

  private fun ensureTableExists() {
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
    val con  = connect()
    val stmt = con.createStatement()
    stmt.execute(postingsSQL)
    stmt.close()
    con.close()
  }

  private fun connect(): Connection {
    val con = DriverManager.getConnection("jdbc:postgresql://localhost/market_analysis", "postgres", "scattering1")
    con.autoCommit = true
    return con
  }
}

class PostgresProductDBModel() : DBModel<RawPosting, Product>() {
  var productTableName = "public.products"

  private val logger = Logger.getLogger(this::class.qualifiedName)

  constructor(postingModel: DBModel<Product, ProductPosting>, productTableName: String) : this() {
    this.productTableName = productTableName
  }

  init {
    ensureTablesExist()
  }

  override fun insert(entity: Product): Product {
    logger.info("inserting product: $entity")
      // Check if product already exists
    val found = findByText(entity.canonicalName)
    if(found != null) {
      val ret = found.merge(entity)

      logger.info("PostgresProductDBModel.insert: merging products into $ret")

      val insertSQL = """
        UPDATE $productTableName
        SET product_name = ?, brand = ?, modelID = ?, upc = ?, category = ?
        WHERE id = ?
      """.trimIndent()
      val con = connect()
      val stmt = con.prepareStatement(insertSQL, Statement.RETURN_GENERATED_KEYS)
      stmt.setString(1, ret.canonicalName)
      stmt.setString(2, ret.primaryBrand.name)
      stmt.setString(3, ret.modelID)
      stmt.setString(4, ret.upc)
      if(ret.category != null)
        stmt.setString(5, ret.category)
      else
        stmt.setNull(5, Types.VARCHAR)
      stmt.setLong(6, ret.id!!)
      try {
      stmt.executeUpdate()
      } catch(ex: PSQLException) {
        if(ex.message?.contains("duplicate key value violates unique constraint") == true) {
          logger.info("Ignoring duplicate name error. Throwing away entry $entity")
        } else {
          throw ex
        }
      } finally {
        stmt.close()
        con.close()
      }
      return ret
    }

    val insertSQL = "INSERT INTO $productTableName VALUES (DEFAULT, ?, ?, ?, ?, ?);"
    val con = connect()
    val stmt = con.prepareStatement(insertSQL, Statement.RETURN_GENERATED_KEYS)
    stmt.setString(1, entity.canonicalName)
    stmt.setString(2, entity.primaryBrand.name)
    stmt.setString(3, entity.modelID)
    stmt.setString(4, entity.upc)
    if(entity.category != null)
      stmt.setString(5, entity.category)
    else
      stmt.setNull(5, Types.VARCHAR)
    try {
      stmt.executeUpdate()
      if(stmt.generatedKeys.next()) {
        entity.id = stmt.generatedKeys.getLong(1)
      }
    } catch(ex: PSQLException) {
      if(ex.message?.contains("duplicate key value violates unique constraint") == true) {
        logger.info("Ignoring duplicate name error. Throwing away entry $entity")
      } else {
        throw ex
      }
    } finally {
      stmt.close()
      con.close()
    }

    return entity
  }

  override fun findByID(id: Long): Product? {
    var ret: Product? = null
    val querySQL = "SELECT * FROM $productTableName WHERE id=${id}"

    val con     = connect()
    val stmt    = con.createStatement()
    val results = stmt.executeQuery(querySQL)
    if(results.next()) {
      ret = parseProduct(results)
    }
    results.close()
    stmt.close()
    con.close()
    return ret
  }

  override fun findByText(text: String): Product? {
    var ret: Product? = null
    val querySQL = "SELECT * FROM $productTableName WHERE product_name ILIKE ?"

    val con     = connect()
    val stmt    = con.prepareStatement(querySQL)
    stmt.setString(1, matcherString(text))
    val results = stmt.executeQuery()
    if(results.next()) {
      ret = parseProduct(results)
    }
    results.close()
    stmt.close()
    con.close()
    return ret
  }

  override fun find(query: RawPosting): Stream<Product> {
    // TODO streaming implementation
    val matches = ArrayList<Product>()
    val querySQL = """
      SELECT * FROM $productTableName
      WHERE product_name ILIKE ? OR brand ILIKE ? OR ? ILIKE product_name OR category ILIKE ?
    """

    val con     = connect()
    val stmt    = con.prepareStatement(querySQL)
    stmt.setString(1, matcherString(query.title))
    if(query.brand != null) {
      stmt.setString(2, matcherString(query.brand!!))
    } else {
      stmt.setNull(2, Types.VARCHAR)
    }
    stmt.setString(3, matcherString(query.description))
    if(query.category != null)
      stmt.setString(4, query.category)
    else
      stmt.setNull(4, Types.VARCHAR)
    val results = stmt.executeQuery()
    while(results.next()) {
      matches.add(parseProduct(results))
    }
    results.close()
    stmt.close()
    con.close()

    return matches.stream()
  }

  override fun all(): Stream<Product> {
    // TODO true streaming implementation
    val matches = ArrayList<Product>()
    val querySQL = "SELECT * FROM $productTableName"

    val con     = connect()
    val stmt    = con.createStatement()
    val results = stmt.executeQuery(querySQL)
    while(results.next()) {
      matches.add(parseProduct(results))
    }
    results.close()
    stmt.close()
    con.close()

    return matches.stream()
  }

  private fun ensureTablesExist() {
    val productsSQL =
      """
      CREATE TABLE IF NOT EXISTS $productTableName (
        id           SERIAL PRIMARY KEY,
        product_name TEXT UNIQUE NOT NULL,
        brand        TEXT NOT NULL,
        category     TEXT,
        modelID      TEXT,
        upc          TEXT,
        tags         TEXT
      );
      """.trimIndent()
    val con     = connect()
    val stmt1   = con.createStatement()
    stmt1.execute(productsSQL)
    stmt1.close()
    con.close()
  }

  private fun connect(): Connection {
    val con = DriverManager.getConnection("jdbc:postgresql://localhost/market_analysis", "postgres", "scattering1")
    con.autoCommit = true
    return con
  }
}