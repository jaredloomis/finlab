package com.jaredloomis.analmark.main

import org.openqa.selenium.*
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxOptions
import org.openqa.selenium.support.ui.WebDriverWait
import java.lang.Exception
import java.util.stream.Collectors
import kotlin.math.min

abstract class Marketplace constructor(val productDB: ProductDB, val name: String, val startURL: String) {
  abstract fun init()
  abstract fun fetchProductBatch(maxSize: Long=Long.MAX_VALUE): List<RawPosting>
  abstract fun search(query: String)
  abstract fun quit()
}

open class SeleniumMarketplace(
  productDB: ProductDB, name: String, startURL: String,
  val productBy: By, val titleBy: By, val priceBy: By,
  val searchInputBy: By, val searchBtnBy: By,
  val nextPageBtnBy: By, val productListPageIdBy: By)
  : Marketplace(productDB, name, startURL) {

  lateinit var driver: WebDriver
  var headless: Boolean = true
  var currentPage: Int = 0
  var lastItem: Int = -1

  override fun init() {
    val options = FirefoxOptions()
      .setHeadless(headless)
    System.setProperty(
      "webdriver.gecko.driver",
      "C:\\Program Files\\geckodriver\\geckodriver-v0.24.0-win64\\geckodriver.exe"
    )
    driver = FirefoxDriver(options)
  }

  // XXX TODO FIX this does not respect pages. Must track lastItem
  override fun fetchProductBatch(maxSize: Long): List<RawPosting> {
    ensureOnProductListPage()

    val products = driver.findElements(productBy)
    val productCount = products.size
    var productStream = products.stream()
    if(maxSize <= productCount) {
      productStream = productStream.skip(lastItem.toLong() + 1).limit(maxSize)
      lastItem = maxSize.toInt()
    }

    val batch = productStream
      .map {productElem ->
        try {
          val titleStr = productElem.findElement(titleBy).text
          val priceStr = productElem.findElement(priceBy).text
          RawPosting(titleStr, "", priceStr, emptyMap())
        } catch(ex: Exception) {
          null
        }
      }
      .filter {post -> post != null}
      .map {post -> post ?: throw Exception("")}
      .collect(Collectors.toList())
      .toList()

    if(batch.isEmpty()) {
      println("[$name] No products found")
    }

    if(maxSize == null || lastItem >= productCount) {
      nextPage()
    }

    return batch
  }

  override fun search(query: String) {
    ensureOnProductListPage()
    val searchBar = driver.findElement(searchInputBy)
    searchBar.clear()
    searchBar.sendKeys(query)
    searchBar.sendKeys(Keys.ENTER)
    waitForPageLoad()
    driver.findElement(searchBtnBy).click()
    waitForPageLoad()
  }

  override fun quit() {
    if(::driver.isInitialized)
      driver.quit()
  }

  fun nextPage(): Boolean {
    lastItem = -1
    val nextBtn = findElement(nextPageBtnBy)
    if(nextBtn != null) {
      ++currentPage
      try {
        nextBtn.click()
      } catch(ex: ElementNotInteractableException) {
        --currentPage
        return false
      }
      waitForPageLoad()
      return true
    }
    return false
  }

  fun ensureOnProductListPage() {
    if(currentPage == 0) {
      driver.navigate().to(startURL)
      currentPage = 1
      waitForPageLoad()
    } else if(findElement(productListPageIdBy) == null) {
      driver.navigate().to(startURL)
      waitForPageLoad()
      repeat(currentPage) {nextPage()}
    }
  }

  fun findElement(by: By): WebElement? {
    return try {
      driver.findElement(by)
    } catch(ex: Exception) {
      null
    }
  }

  fun findElements(by: By): List<WebElement> {
    return try {
      driver.findElements(by).toList()
    } catch(ex: Exception) {
      emptyList()
    }
  }

  fun waitForPageLoad() {
    Thread.sleep(1000)
    WebDriverWait(driver, 10000).until {webDriver ->
      if(webDriver is JavascriptExecutor)
        return@until (webDriver as JavascriptExecutor).executeScript("return document.readyState") == "complete"
      false
    }
  }
}

open class DetailedMarketplace(
  productDB: ProductDB, name: String, startURL: String, //val postParser: RawPosting,
  productBy: By, titleBy: By, priceBy: By,
  searchInputBy: By, searchBtnBy: By,
  nextPageBtnBy: By, productListPageIdBy: By, val productPageIdBy: By)
  : SeleniumMarketplace(productDB, name, startURL, productBy, titleBy, priceBy,
                        searchInputBy, searchBtnBy, nextPageBtnBy, productListPageIdBy) {

  open fun fetchProduct(): RawPosting? {
    val titleStr = findElement(titleBy)?.text ?: ""
    val priceStr = findElement(priceBy)?.text ?: ""
    return RawPosting(titleStr, "", priceStr, emptyMap())
  }

  override fun fetchProductBatch(maxSize: Long): List<RawPosting> {
    ensureOnProductListPage()

    val productElems = findElements(productBy)
    val maxProductI  = min(productElems.size, lastItem + 1 + maxSize.toInt())
    val posts = (lastItem+1 until maxProductI)
      .map {println(it); productLink(it)}
      .mapNotNull {link ->
        println("Going to $link")
        driver.navigate().to(link)
        Thread.sleep(5000)
        WebDriverWait(driver, 30000).until {
          try {
            driver.findElement(productPageIdBy)
            true
          } catch(ex: Exception) {
            false
          }
          //(driver as JavascriptExecutor).executeScript("return document.readyState;") == "complete"
        }
        val product = fetchProduct()
        driver.navigate().back()
        product
      }

    if(posts.isEmpty()) {
      println("[$name] No products found")
    }

    if(lastItem >= productElems.size) {
      nextPage()
    }

    lastItem = maxProductI

    return posts
  }

  open fun parsePrice(str: String): CurrencyAmount {
    return CurrencyAmount(str)
  }

  open fun productLink(i: Int): String {
    return findElements(productBy)[i].getAttribute("href")
  }
}

class Craigslist(productDB: ProductDB) : SeleniumMarketplace(
  productDB, "Craigslist", "https://sandiego.craigslist.org/search",
  By.className("result-row"), By.className("result-title"), By.className("result-price"),
  By.id("query"), By.className("searchbtn"), By.cssSelector(".button.next"),
  By.className("pagenum")
) {}
/*
class EBay(productDB: ProductDB) : SeleniumMarketplace(
  productDB, "eBay", "https://www.ebay.com/",
  By.className("s-item__info"), By.className("s-item__title"),
  By.className("s-item__price"),
  By.id("gh-ac"), By.id("gh-btn"), By.cssSelector("a[rel=next]"),
  By.className("s-answer-region")
) {}*/

class EBay(productDB: ProductDB) : DetailedMarketplace(
  productDB, "eBay", "https://www.ebay.com/",
  By.className("s-item__link"), By.id("itemTitle"),
  By.id("prcIsum_bidPrice"),
  By.id("gh-ac"), By.id("gh-btn"), By.cssSelector("a[rel=next]"),
  By.className("s-answer-region"), By.id("itemTitle")
) {}

class NewEgg(productDB: ProductDB) : SeleniumMarketplace(
  productDB, "New Egg", "https://www.newegg.com/",
  By.className("item-container"), By.className("item-title"), By.className("price-current"),
  By.id("haQuickSearchBox"), By.className("search-bar-btn"),
  By.cssSelector("button[aria-label=Next]"),
  By.id("radio_soldby")
) {}

class Overstock(productDB: ProductDB) : DetailedMarketplace(
  productDB, "Overstock.com", "https://www.overstock.com/",
  By.className("productCardLink"), By.className("product-title"),
  By.className("monetary-price-value"),
  By.className("search_searchBar_de"), By.cssSelector("#headerSearchContainer button[type=submit]"),
  By.cssSelector("a[title='Next Page']"),
  By.id("top-refinements"), By.id("brand-name")
) {
  override fun fetchProduct(): RawPosting? {
    val brandBy = By.cssSelector("#brand-name a")

    val titleStr = findElement(titleBy)?.text ?: ""
    val priceStr = findElement(priceBy)?.text ?: ""
    val brandStr = findElement(brandBy)?.text ?: ""
    val title    = removeSubstring(brandStr, titleStr, caseSensitive=false)
    val ret = RawPosting(title, "", priceStr, emptyMap())
    ret.brand = brandStr
    return ret
    /*
    val product  = Product(title, brandStr)
    val price = parsePrice(priceStr)
    return ProductPosting(product, price)
     */
  }
}