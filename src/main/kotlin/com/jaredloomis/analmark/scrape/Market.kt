package com.jaredloomis.analmark.scrape

import com.jaredloomis.analmark.legacy.ProductDB
import com.jaredloomis.analmark.model.CurrencyAmount
import com.jaredloomis.analmark.model.EbayRawPosting
import com.jaredloomis.analmark.model.RawPosting
import com.jaredloomis.analmark.util.getLogger
import org.openqa.selenium.*
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxOptions
import org.openqa.selenium.support.ui.WebDriverWait
import java.lang.Exception
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.logging.Logger
import java.util.stream.Collectors
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.min
import kotlin.random.Random

enum class MarketType {
  CRAIGSLIST, EBAY, OVERSTOCK, NEWEGG
}

abstract class Market constructor(val type: MarketType, val productDB: ProductDB, val name: String, val startURL: String) {
  abstract fun init()
  abstract fun fetchProductBatch(maxSize: Long=Long.MAX_VALUE): List<RawPosting> // TODO refactor to Stream<RawPosting>
  abstract fun search(query: String)
  abstract fun quit()
  abstract fun navigateToRandomProductList()
}

abstract class SeleniumMarket(
  type: MarketType, productDB: ProductDB, name: String, startURL: String,
  val productBy: By, val titleBy: By, val priceBy: By,
  val searchInputBy: By, val searchBtnBy: By,
  val nextPageBtnBy: By, val productListPageIdBy: By)
  : Market(type, productDB, name, startURL) {

  lateinit var driver: WebDriver
  var headless: Boolean = true
  var currentPage: Int = 0
  var lastItem: Int = -1
  protected val logger: Logger = getLogger(this::class)

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
          RawPosting(type, "", titleStr, "", CurrencyAmount(priceStr), emptyMap())
        } catch(ex: Exception) {
          null
        }
      }
      .filter {post -> post != null}
      .map {post -> post ?: throw Exception("")}
      .collect(Collectors.toList())

    if(batch.isEmpty()) {
      logger.warning("[$name] No products found")
    }

    if(lastItem >= productCount) {
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

  override fun navigateToRandomProductList() {
    val choices = getRandomProductListUrls()
    val url     = choices[Random.nextInt(choices.size)]
    driver.navigate() to url
    waitForPageLoad()
  }

  protected abstract fun getRandomProductListUrls(): List<String>

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

  fun getText(by: By): String {
    return findElement(by)?.text ?: ""
  }

  fun click(by: By) {
    findElement(by)?.click()
  }

  fun findElement(by: By): WebElement? {
    return try {
      driver.findElement(by)
    } catch(ex: Exception) {
      logger.warning("Could not find any elements matching '$by'")
      screenshot()
      null
    }
  }

  fun findElements(by: By): List<WebElement> {
    return try {
      driver.findElements(by).toList()
    } catch(ex: Exception) {
      logger.warning("Could not find any elements matching '$by'")
      screenshot()
      emptyList()
    }
  }

  fun screenshot() {
    val src      = (driver as TakesScreenshot).getScreenshotAs(OutputType.FILE)
    val destDir  = Paths.get("data", "screenshots")
    val destName = "${Date().toString()}.png".replace(':', '-')
    Files.createDirectories(destDir)
    Files.move(src.toPath(), destDir.resolve(destName))
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

abstract class DetailedMarket(
  type: MarketType, productDB: ProductDB, name: String, startURL: String,
  productBy: By, titleBy: By, priceBy: By,
  searchInputBy: By, searchBtnBy: By,
  nextPageBtnBy: By, productListPageIdBy: By, val productPageIdBy: By)
  : SeleniumMarket(type, productDB, name, startURL, productBy, titleBy, priceBy,
                        searchInputBy, searchBtnBy, nextPageBtnBy, productListPageIdBy) {

  open fun fetchProduct(): RawPosting? {
    val url      = driver.currentUrl
    val titleStr = findElement(titleBy)?.text
    val priceStr = findElement(priceBy)?.text

    return if(priceStr != null && titleStr != null) {
      RawPosting(type, url, titleStr, "", CurrencyAmount(priceStr), emptyMap())
    } else {
      null
    }
  }

  override fun fetchProductBatch(maxSize: Long): List<RawPosting> {
    ensureOnProductListPage()

    val productElems = findElements(productBy)
    val maxProductI  = min(productElems.size, lastItem + 1 + maxSize.toInt())
    val posts = (lastItem+1 until maxProductI)
      .map {productLink(it)}
      .mapNotNull {link ->
        driver.navigate().to(link)
        waitForPageLoad()
        val product = fetchProduct()
        driver.navigate().back()
        waitForPageLoad()
        product
      }

    if(posts.isEmpty()) {
      logger.warning("[$name] No products found")
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

class Craigslist(productDB: ProductDB) : DetailedMarket(
  MarketType.CRAIGSLIST, productDB, "Craigslist", "https://sandiego.craigslist.org/search",
  By.className("result-title"), By.id("titletextonly"), By.className("price"),
  By.id("query"), By.className("searchbtn"), By.cssSelector(".button.next"),
  By.className("pagenum"), By.id("titletextonly")
) {
  override fun getRandomProductListUrls(): List<String> {
    return listOf("https://craigslist.org/d/for-sale/search/sss")
  }
}

class EBay(productDB: ProductDB) : DetailedMarket(
  MarketType.EBAY, productDB, "eBay", "https://www.ebay.com/",
  By.cssSelector(".s-item__link, a[itemprop=url]"), By.id("itemTitle"),
  By.id("prcIsum_bidPrice"),
  By.id("gh-ac"), By.id("gh-btn"), By.cssSelector("a[rel=next]"),
  By.className("s-answer-region"), By.id("itemTitle")
) {
  override fun fetchProduct(): RawPosting? {
    val url      = driver.currentUrl
    val titleStr = findElement(titleBy)?.text ?: ""
    val priceStr = fetchPriceStr() ?: ""
    return try {
      EbayRawPosting(url, titleStr, "", CurrencyAmount(priceStr), fetchAttrs())
    } catch(ex: Exception) {
      null
    }
  }

  private fun fetchPriceStr(): String? {
    val priceBys = listOf(priceBy, By.id("prcIsum"))

    priceBys.forEach {
      try {
        val elem = driver.findElement(it)
        return elem.text
      } catch(ex: Exception) {}
    }

    return null
  }

  private fun fetchAttrs(): Map<String, String> {
    val attrs   = HashMap<String, String>()
    val tagList = ArrayList<String>()
    val elems   = driver.findElements(By.cssSelector(".itemAttr td"))

    elems.forEach {elem ->
      tagList.add(elem.text.toLowerCase().replace(":", ""))
    }

    // Every even td is an attribute label
    // Every odd td is the value of preceding label
    for(i in 1 until tagList.size step 2) {
      attrs[tagList[i-1]] = tagList[i]
    }
    return attrs
  }

  override fun getRandomProductListUrls(): List<String> {
    return listOf("https://www.ebay.com/deals/tech")
  }
}

class NewEgg(productDB: ProductDB) : SeleniumMarket(
  MarketType.NEWEGG, productDB, "New Egg", "https://www.newegg.com/",
  By.className("item-container"), By.className("item-title"), By.className("price-current"),
  By.id("haQuickSearchBox"), By.className("search-bar-btn"),
  By.cssSelector("button[aria-label=Next]"),
  By.id("radio_soldby")
) {
  override fun getRandomProductListUrls(): List<String> {
    return listOf("https://www.newegg.com/todays-deals")
  }
}

class Overstock(productDB: ProductDB) : DetailedMarket(
  MarketType.OVERSTOCK, productDB, "Overstock.com", "https://www.overstock.com/",
  By.className("productCardLink"), By.className("product-title"),
  By.className("monetary-price-value"),
  By.className("search_searchBar_de"), By.cssSelector("#headerSearchContainer button[type=submit]"),
  By.cssSelector("a[title='Next Page']"),
  By.id("top-refinements"), By.id("brand-name")
) {
  override fun fetchProduct(): RawPosting? {
    val brandBy = By.cssSelector("#brand-name a")

    val url      = driver.currentUrl
    val title    = findElement(titleBy)?.text ?: ""
    val priceStr = findElement(priceBy)?.text ?: ""
    val brandStr = findElement(brandBy)?.text ?: ""
    val attrs    = emptyMap<String, String>().plus(Pair("brand", brandStr))
    return RawPosting(type, url, title, "", parsePrice(priceStr), attrs)
  }

  override fun parsePrice(str: String): CurrencyAmount {
    val len     = str.length
    val cents   = str.substring(len - 2)
    val dollars = str.substring(0, len - 2)
    return CurrencyAmount("$dollars.$cents")
  }

  override fun getRandomProductListUrls(): List<String> {
    return listOf("https://www.overstock.com/Home-Garden/Rugs/244/cat.html?TID=SALESDEALS:04:05:Rugs")
  }
}

fun createMarket(ty: MarketType, productDB: ProductDB): Market {
  return when(ty) {
    MarketType.CRAIGSLIST -> Craigslist(productDB)
    MarketType.EBAY -> EBay(productDB)
    MarketType.NEWEGG -> NewEgg(productDB)
    MarketType.OVERSTOCK -> Overstock(productDB)
  }
}