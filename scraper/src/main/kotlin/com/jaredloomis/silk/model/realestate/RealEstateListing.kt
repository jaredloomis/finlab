package com.jaredloomis.silk.model.realestate

import com.jaredloomis.silk.db.SQLSource
import com.jaredloomis.silk.db.SQLStatement
import com.jaredloomis.silk.db.Table
import com.jaredloomis.silk.model.CurrencyAmount
import com.jaredloomis.silk.model.product.ProductPosting
import com.jaredloomis.silk.model.product.RawPosting
import com.jaredloomis.silk.scrape.product.ProductMarketType
import io.github.sebasbaumh.postgis.Point
import org.postgresql.util.PSQLException
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate
import java.util.HashMap

data class RealEstateListing(
  val url: String,
  val address: String, val price: CurrencyAmount,
  val propertyType: PropertyType?,
  val beds: Short?, val baths: Short?, val squareFootage: Int?,
  val images: Set<String>, val listedDate: LocalDate?,
  val parseTime: Instant?,
  val attrs: Map<String, Any>
) {
  var id: Long? = null
  // TODO
  var lotSizeAcres: Int? = null
  var location: Pair<Double, Double>? = null

  fun geometry(): Point? {
    return location?.let { Point(it.first, it.second) }
  }
}

enum class PropertyType {
  HOUSE, APARTMENT, LAND, MANUFACTURED
}

class RealEstateListingTable(dbUrl: String, tableName: String): Table<RealEstateListing>(dbUrl, tableName) {
  override fun insert(item: RealEstateListing): RealEstateListing {
    // TODO parseTime, lotSizeAcres
    val insertSQL = """
        INSERT INTO $tableName (id, url, address, price, propertyType, beds, baths, squareFootage, images, listedDate, attrs)
        VALUES (DEFAULT, %url, %address, %price.pennies, %propertyType, %beds, %baths, %squareFootage, %images, %listedDate, %attrs);
      """
    update(SQLStatement(insertSQL), item)
    return item
  }

  override fun parseItem(results: ResultSet): RealEstateListing {
    val id = results.getLong("id")
    val url = results.getString("url")
    val address = results.getString("address")
    val price = CurrencyAmount(results.getLong("price"))
    val propertyType = PropertyType.valueOf(results.getString("propertyType"))
    val beds = results.getShort("beds")
    val baths = results.getShort("price")
    val squareFootage = results.getInt("price")
    val images = setOf(results.getString("images"))
    val listedDate = results.getDate("listedDate").toLocalDate()
    val attrs = results.getObject("attrs") as Map<String, Any>
    val geom = results.getObject("geom") as Point
    // TODO parseTime, lotSizeAcres
    val parseTime = null
    val listing = RealEstateListing(url, address, price, propertyType, beds, baths, squareFootage, images, listedDate, parseTime, attrs)
    listing.id = id
    listing.location = Pair(geom.x, geom.y)
    return listing
  }

  override fun createTableSQL(): SQLSource {
    return SQLSource.Resource(this::class.java.classLoader.getResource("sql/realestatelisting/create.sql")!!)
  }
}