package com.jaredloomis.analmark

import org.openqa.selenium.By
import org.openqa.selenium.Keys
import org.openqa.selenium.WebDriver
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.support.ui.WebDriverWait
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.stream.Collectors

class CurrencyAmount {
  val pennies: Long

  constructor(pennies: Long) {
    this.pennies = pennies
  }

  constructor(str: String) {
    val pricePattern: Pattern = Pattern.compile("[^\\d]*([\\d,]+)")

    // Now create matcher object.
    val m: Matcher = pricePattern.matcher(str)
    if(m.find()) {
      val dollars = m.group(1).replace(",", "")
      this.pennies = dollars.toLong() * 100
    } else {
      throw Exception("Improperly formatted CurrencyAmount")
    }
  }

  override fun toString(): String {
    return "CurrencyAmount(pennies=$pennies)"
  }

}

data class Product(val name: String, val otherNames: List<String>)

data class ProductPosting(val product: Product, val price: CurrencyAmount)

abstract class Service constructor(val name: String, val driver: WebDriver, val startURL: String) {
  abstract fun fetchProductBatch(): List<ProductPosting>
  abstract fun search(query: String): Unit
}

fun sleep(ms: Long) {
  try {
    Thread.sleep(ms)
  } catch(ex: Exception) {
    ex.printStackTrace()
  }
}

class Craigslist(driver: WebDriver) :
      Service("Craigslist", driver, "https://sandiego.craigslist.org/search/sss") {
  var currentPage: Int = 0

  override fun fetchProductBatch(): List<ProductPosting> {
    ensureOnSite()

    val ret = driver.findElements(By.className(".result-row")).stream()
      .map {productElem ->
        val productStr = productElem.findElement(By.className("result-title")).text
        val priceStr   = productElem.findElement(By.className(".result-price")).text
        val product    = Product(productStr, ArrayList())
        val price      = CurrencyAmount(priceStr)
        ProductPosting(product, price)
      }
      .collect(Collectors.toList())

    nextPage()

    return ret
  }

  override fun search(query: String) {
    ensureOnSite()
    val searchBar = driver.findElement(By.id("query"))
    searchBar.sendKeys(query)
    searchBar.sendKeys(Keys.ENTER)
    sleep(1000)
  }

  fun nextPage() {
    driver.findElement(By.cssSelector(".button.next")).click()
    ++currentPage
  }

  fun ensureOnSite() {
    if (currentPage == 0) {
      driver.navigate().to(startURL)
      sleep(5000)
      currentPage = 1
    }
  }
}

fun main() {
  System.setProperty("webdriver.gecko.driver", "/usr/bin/geckodriver")
  val driver = FirefoxDriver()

  try {
    val cl = Craigslist(driver)
    cl.search("surfboard")
    repeat(3) {
      System.out.println(cl.fetchProductBatch())
    }
  } finally {
    driver.quit()
  }
}
