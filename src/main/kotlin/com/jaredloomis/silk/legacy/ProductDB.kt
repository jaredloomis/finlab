package com.jaredloomis.silk.legacy

import com.jaredloomis.silk.db.DBModel
import com.jaredloomis.silk.model.product.Brand
import com.jaredloomis.silk.model.product.Product
import com.jaredloomis.silk.model.product.RawPosting
import com.jaredloomis.silk.nlp.containsIgnoreCase
import com.jaredloomis.silk.nlp.stem
import com.jaredloomis.silk.nlp.tokens
import org.postgresql.util.PSQLException
import java.sql.Connection
import java.sql.DriverManager
import java.util.stream.Stream

typealias ProductDB = DBModel<RawPosting, Product>

interface ProductDB1 {
  fun addProduct(product: Product)
  fun findMatches(post: RawPosting): Stream<Product>
  fun products(): Stream<Product>
}

class DummyProductDB(val brandDB: BrandDB) : ProductDB1 {
  private var products: Set<Product> = HashSet()

  override fun addProduct(product: Product) {
    products = products.plus(product)
  }

  // TODO return matches in order, instead of just best match
  override fun findMatches(post: RawPosting): Stream<Product> {
    val searchTokens = tokens(post.title).map { stem(it) }
    var maxScore = 0
    var bestMatch: Product? = null

    for (product in products.filter { post.brand == null || it.primaryBrand.name == post.brand }) {
      val productTokens = tokens(product.primaryBrand.name + " " + product.canonicalName)
        .map { stem(it) }
      // Score = number of tokens which are common across both search product and search
      val score = productTokens.filter {
        searchTokens.containsIgnoreCase(it)
      }.size

      System.out.println(productTokens + "   " + searchTokens + "   " + score)
      if (score > maxScore) {
        maxScore = score
        bestMatch = product
      }
    }

    val ret = ArrayList<Product>()
    if (bestMatch != null) {
      ret.add(bestMatch)
    }
    return ret.stream()
  }

  override fun products(): Stream<Product> {
    return products.stream()
  }
}

class PostgresProductDB(val brandDB: BrandDB) : ProductDB1 {
  var productTableName = "public.products"
  var postingTableName = "public.postings"

  constructor(brandDB: BrandDB, productTableName: String, postingTableName: String) : this(brandDB) {
    this.productTableName = productTableName
    this.postingTableName = postingTableName
  }

  init {
    ensureTablesExist()
  }

  override fun addProduct(product: Product) {
    try {
      val insertSQL = "INSERT INTO $productTableName (product_name, brand) VALUES (?, ?);"
      val con = connect()
      val stmt = con.prepareStatement(insertSQL)
      stmt.setString(1, product.canonicalName)
      stmt.setString(2, product.primaryBrand.name)
      /*
      val insertSQL = "INSERT INTO $productTableName (product_name, brand) VALUES ('${product.canonicalName}', '${product.primaryBrand.name}');"
      val con = connect()
      val stmt = con.createStatement()
      println(insertSQL)*/
      stmt.execute(insertSQL)
      con.close()
      stmt.close()
    } catch (ex: PSQLException) {
      println(
        "[PostgresProductDB] Following exception is likely due to a duplicate product in PostgresProductDB.insert. Ignoring."
      )
      System.err.println(ex.message)
    }
  }

  override fun findMatches(post: RawPosting): Stream<Product> {
    // TODO streaming implementation
    val matches = ArrayList<Product>()
    val querySQL = "SELECT * FROM $productTableName WHERE brand ILIKE '${post.title}'"

    val con = connect()
    val stmt = con.createStatement()
    val results = stmt.executeQuery(querySQL)
    while (results.next()) {
      val id = results.getLong("id")
      val productName = results.getString("product_name")
      val productBrand = results.getString("brand")
      matches.add(Product(id, productName, Brand(productBrand)))
    }
    results.close()
    stmt.close()
    con.close()

    return matches.stream()
  }

  override fun products(): Stream<Product> {
    // TODO streaming implementation
    val matches = ArrayList<Product>()
    val querySQL = "SELECT * FROM $productTableName"

    val con = connect()
    val stmt = con.createStatement()
    val results = stmt.executeQuery(querySQL)
    while (results.next()) {
      val id = results.getLong("id")
      val productName = results.getString("product_name")
      val productBrand = results.getString("brand")
      matches.add(Product(id, productName, Brand(productBrand)))
    }
    results.close()
    stmt.close()
    con.close()

    return matches.stream()
  }

  private fun ensureTablesExist() {
    val productsSQL = "CREATE TABLE IF NOT EXISTS $productTableName (" +
      "id SERIAL PRIMARY KEY, " +
      "product_name VARCHAR(50) UNIQUE NOT NULL, " +
      "brand VARCHAR(50) NOT NULL" +
      ");"
    val postingsSQL = "CREATE TABLE IF NOT EXISTS $postingTableName (" +
      "id SERIAL PRIMARY KEY, " +
      "market SMALLINT NOT NULL, " +
      "product SERIAL references $productTableName(id)" +
      ");"
    val con = connect()
    val stmt1 = con.createStatement()
    stmt1.execute(productsSQL)
    val stmt2 = con.createStatement()
    stmt2.execute(postingsSQL)
    stmt1.close()
    stmt2.close()
    con.close()
  }

  private fun connect(): Connection {
    return DriverManager.getConnection("jdbc:postgresql://localhost/market_analysis", "postgres", "scattering1")
  }
}