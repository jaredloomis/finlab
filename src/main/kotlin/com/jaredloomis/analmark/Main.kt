package com.jaredloomis.analmark

import org.openqa.selenium.By
import org.openqa.selenium.Keys
import org.openqa.selenium.firefox.FirefoxDriver
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.stream.Collectors

fun sleep(ms: Long) {
  try {
    Thread.sleep(ms)
  } catch(ex: Exception) {
    ex.printStackTrace()
  }
}

enum class MarketplaceID {
  CRAIGSLIST //, EBAY
}

class CurrencyAmount {
  val pennies: Long

  constructor(pennies: Long) {
    this.pennies = pennies
  }

  constructor(str: String) {
    val pricePattern: Pattern = Pattern.compile("[^\\d]*([\\d,]+)")
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

class Product {
  val product: String
  val brand: String
  val rawPostings: List<String>

  constructor(product: String, brand: String, rawPostings: List<String>) {
    this.product     = product
    this.brand       = brand
    this.rawPostings = rawPostings
  }

  // XXX temporary - need to parse product & brand
  constructor(str: String, rawPostings: List<String>) {
    this.product     = str
    this.brand       = ""
    this.rawPostings = rawPostings
  }

  constructor(str: String) : this(str, ArrayList())
}

data class ProductPosting(val product: Product, val price: CurrencyAmount)

abstract class Marketplace constructor(val name: String, val startURL: String) {
  abstract fun fetchProductBatch(): List<ProductPosting>
  abstract fun search(query: String): Unit
  abstract fun quit(): Unit
}

open class StandardMarketplace(
  name: String, startURL: String,
  val productBy: By, val titleBy: By, val priceBy: By,
  val searchInputBy: By, val searchBtnBy: By,
  val nextPageBtnBy: By)
      : Marketplace(name, startURL) {
  init {
    System.setProperty(
      "webdriver.gecko.driver",
      "C:\\Program Files\\geckodriver\\geckodriver-v0.24.0-win64\\geckodriver.exe"
    )
  }

  val driver = FirefoxDriver()
  var currentPage: Int = 0

  override fun fetchProductBatch(): List<ProductPosting> {
    ensureOnSite()

    val ret = driver.findElements(productBy).stream()
      .map {productElem ->
        val productStr = productElem.findElement(titleBy).text
        val priceStr   = productElem.findElement(priceBy).text
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
    val searchBar = driver.findElement(searchInputBy)
    searchBar.clear()
    searchBar.sendKeys(query)
    searchBar.sendKeys(Keys.ENTER)
    driver.findElement(searchBtnBy).click()
    sleep(2000)
  }

  override fun quit() {
    driver.quit()
  }

  fun nextPage() {
    driver.findElement(nextPageBtnBy).click()
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

class Craigslist : StandardMarketplace(
  "Craigslist", "https://sandiego.craigslist.org/search/sss",
  By.className("result-row"), By.className("result-title"), By.className("result-price"),
  By.id("query"), By.className("searchbtn"), By.cssSelector(".button.next")
) {}

/*
class Craigslist : Marketplace("Craigslist", "https://sandiego.craigslist.org/search/sss") {
  init {
    System.setProperty(
      "webdriver.gecko.driver",
      "C:\\Program Files\\geckodriver\\geckodriver-v0.24.0-win64\\geckodriver.exe"
    )
  }

  val driver = FirefoxDriver()
  var currentPage: Int = 0

  override fun fetchProductBatch(): List<ProductPosting> {
    ensureOnSite()

    val ret = driver.findElements(By.className("result-row")).stream()
      .map {productElem ->
        val productStr = productElem.findElement(By.className("result-title")).text
        val priceStr   = productElem.findElement(By.className("result-price")).text
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
    sleep(4000)
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
 */

class EBay : StandardMarketplace(
  "eBay", "https://www.ebay.com/b/Computers-Tablets-Network-Hardware/58058/bn_1865247?_from=R40&_nkw=&_trksid=m570.l1313",
  By.className("s-item__info "), By.className("s-item__title"), By.className("s-item__price"),
  By.id("gh-ac"), By.id("gh-btn"), By.className("x-carousel__next-btn")
  ) {}

fun main() {
  val pageCount = 3
  val cl = Craigslist()
  val eb = EBay()
  val ebPosts: MutableList<ProductPosting> = ArrayList()
  val clPosts: MutableList<ProductPosting> = ArrayList()

  try {
    cl.search("surfboard")

    repeat(pageCount) {
      val clBatch = cl.fetchProductBatch()
      clPosts.addAll(clBatch)
      eb.search(clBatch[0].product.product)
      val ebBatch = eb.fetchProductBatch()
      ebPosts.addAll(ebBatch)
    }

    println(clPosts)
    println(ebPosts)
  } catch(ex: Throwable) {
    ex.printStackTrace()
  } finally {
    cl.quit()
    eb.quit()
  }
}
