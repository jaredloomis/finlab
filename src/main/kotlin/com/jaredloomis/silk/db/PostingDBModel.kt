package com.jaredloomis.silk.db

import com.jaredloomis.silk.model.CurrencyAmount
import com.jaredloomis.silk.model.product.Product
import com.jaredloomis.silk.model.product.ProductPosting
import com.jaredloomis.silk.model.product.RawPosting
import com.jaredloomis.silk.scrape.product.ProductMarketType
import com.jaredloomis.silk.util.getLogger
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.singleton
import org.postgresql.util.PSQLException
import java.sql.Date
import java.sql.ResultSet
import java.sql.Statement
import java.sql.Types
import java.util.stream.Stream

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
    logger.info("Inserted Product")
    val insertSQL = "INSERT INTO $tableName VALUES (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?);"
    val con = connect()
    val stmt = con.prepareStatement(insertSQL, Statement.RETURN_GENERATED_KEYS)
    stmt.setString(1, entity.posting.market.name)
    stmt.setString(2, entity.posting.url)
    if (product.id != null)
      stmt.setLong(3, product.id!!)
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
    return ProductPosting(id, product, entity.posting)
  }

  override fun find(query: Product): Stream<ProductPosting> {
    // TODO streaming implementation
    val matches = ArrayList<ProductPosting>()
    val querySQL = """
      SELECT * FROM $tableName
      WHERE product = ?
    """.trimIndent()

    val con = connect()
    val stmt = con.prepareStatement(querySQL)
    if (query.id != null)
      stmt.setLong(1, query.id!!)
    else
      stmt.setNull(1, Types.BIGINT)

    val results = stmt.executeQuery()
    while (results.next()) {
      val post = parseItem(results)
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
      ret = parseItem(results)
    }
    results.close()
    stmt.close()
    return ret
  }

  override fun parseItem(results: ResultSet): ProductPosting? {
    val id = results.getLong(ID_COLUMN_NAME)
    val marketStr = results.getString(MARKET_COLUMN_NAME)
    val url = results.getString(URL_COLUMN_NAME)
    val productID = results.getLong(PRODUCT_COLUMN_NAME)
    val title = results.getString(TITLE_COLUMN_NAME)
    val priceCents = results.getLong(PRICE_COLUMN_NAME)
    val description = results.getString(DESCRIPTION_COLUMN_NAME)
    val specsStr = results.getString(SPECS_COLUMN_NAME)
    //val seenAt = results.getDate(SEEN_COLUMN_NAME)
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

  override fun createTableSQL(): SQLSource {
    return SQLSource.Resource(this::class.java.classLoader.getResource("sql/posting/create.sql")!!)
  }
}

val postingDBModule = DI.Module("PostingDBModel") {
  import(productDBModule)
  bind<PostgresPostingDBModel>() with singleton { PostgresPostingDBModel(instance()) }
}