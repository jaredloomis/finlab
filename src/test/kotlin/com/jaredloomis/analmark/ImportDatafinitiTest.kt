package com.jaredloomis.analmark

import com.jaredloomis.analmark.db.PostgresProductDBModel
import com.jaredloomis.analmark.model.product.Brand
import com.jaredloomis.analmark.model.product.Product
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

import java.nio.file.Files
import java.nio.file.Paths

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ImportDatafinitiTest {
  val productDB = PostgresProductDBModel()
  val dataFile = Paths.get("data", "DatafinitiElectronicsProductsPricingData.csv")

  @Test
  fun importData() {
    val reader = Files.newBufferedReader(dataFile)
    val csvParser = CSVParser(reader, CSVFormat.EXCEL.withHeader())

    val products = csvParser.map { csvRecord ->
      val name = csvRecord.get("name")
      val brand = csvRecord.get("brand")
      val manufacturer = csvRecord.get("manufacturer")
      val categories = csvRecord.get("categories")
      val modelID = csvRecord.get("manufacturerNumber")
      val product = Product(-1, name, Brand("$brand,$manufacturer"))
      product.modelID = modelID
      categories.split(",").forEach { product.tags.add(it) }
      product
    }

    products.forEach {
      try {
        productDB.insert(it)
      } catch (ex: Exception) {
        ex.printStackTrace()
      }
    }
  }
}
