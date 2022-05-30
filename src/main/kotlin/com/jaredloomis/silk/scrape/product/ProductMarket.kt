package com.jaredloomis.silk.scrape.product

import com.jaredloomis.silk.di
import com.jaredloomis.silk.model.CurrencyAmount
import com.jaredloomis.silk.model.product.EbayRawPosting
import com.jaredloomis.silk.model.product.RawPosting
import com.jaredloomis.silk.util.getLogger
import com.jaredloomis.silk.util.retry
import io.reactivex.rxjava3.core.Flowable
import org.kodein.di.instance
import org.openqa.selenium.*
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxOptions
import org.openqa.selenium.support.ui.WebDriverWait
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.logging.Logger
import java.util.stream.Collectors
import java.util.stream.Stream
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.min
import kotlin.random.Random

enum class ProductMarketType {
  CRAIGSLIST, EBAY, OVERSTOCK, NEWEGG, WALMART
}

abstract class ProductMarket(val type: ProductMarketType, val name: String, val startURL: String) {
  abstract fun init()
  abstract fun fetchProductBatch(maxSize: Long = Long.MAX_VALUE): List<RawPosting>
  abstract fun search(query: String)
  abstract fun quit()
  abstract fun navigateToRandomProductList()

  fun postings(): Stream<List<RawPosting>> {
    return Stream.generate { fetchProductBatch() }.sequential()
  }
}

abstract class SeleniumProductMarket(
  type: ProductMarketType, name: String, startURL: String,
  val productBy: By, val titleBy: By, val priceBy: By,
  val searchInputBy: By, val searchBtnBy: By,
  val nextPageBtnBy: By, val productListPageIdBy: By)
  : ProductMarket(type, name, startURL) {

  lateinit var driver: WebDriver
  protected val logger: Logger = getLogger(this::class)
  private val threads by di.instance<ExecutorService>()

  var headless = true
  var takeScreenshots = false
  var backend = "chrome"
  var currentPage: Int = 0
  var lastItem: Int = -1
  var screenshotCount: Int = 0

  override fun init() {
    if(!::driver.isInitialized) {
      driver = when(backend) {
        "chrome", "chromium" -> {
          val options = ChromeOptions()
            .setHeadless(headless)

          if (System.getProperty("webdriver.chrome.driver") == null) {
            System.setProperty(
              "webdriver.chrome.driver",
              findExecutablePath("chromedriver")!!
            )
          }
          ChromeDriver(options)
        }
        else -> {
          val options = FirefoxOptions()
            .setHeadless(headless)

          if (System.getProperty("webdriver.gecko.driver") == null) {
            System.setProperty(
              "webdriver.gecko.driver",
              findExecutablePath("geckodriver")!!
            )
          }
          FirefoxDriver(options)
        }
      }
    }
  }

  // XXX TODO FIX this does not respect pages. Must track lastItem
  override fun fetchProductBatch(maxSize: Long): List<RawPosting> {
    ensureOnProductListPage()

    val products = driver.findElements(productBy)
    val productCount = products.size
    var productStream = products.stream()
    if (maxSize <= productCount) {
      productStream = productStream.skip(lastItem.toLong() + 1).limit(maxSize)
      lastItem = maxSize.toInt()
    }

    val batch = productStream
      .map { productElem ->
        try {
          val titleStr = productElem.findElement(titleBy).text
          val priceStr = productElem.findElement(priceBy).text
          RawPosting(type, "", titleStr, "", CurrencyAmount(priceStr), emptyMap())
        } catch (ex: Exception) {
          null
        }
      }
      .filter { post -> post != null }
      .map { post -> post ?: throw Exception("") }
      .collect(Collectors.toList())

    if (batch.isEmpty()) {
      logger.warning("[$name] No products found")
    }

    if (lastItem >= productCount) {
      nextPage()
    }

    return batch
  }

  override fun search(query: String) {
    logger.info("[$name] Searching for '$query'")
    ensureOnProductListPage()
    val searchBar = findElement(searchInputBy)
    searchBar?.clear()
    searchBar?.sendKeys(query)
    findElement(searchInputBy)?.sendKeys(Keys.ENTER)
    waitForPageLoad()
    findElement(searchBtnBy)?.click()
    waitForPageLoad()
  }

  override fun navigateToRandomProductList() {
    logger.info("[$name] Navigating to random product list page.")
    val choices = getRandomProductListUrls()
    val url = choices[Random.nextInt(choices.size)]
    println(url)
    driver.navigate().to(url)
    waitForPageLoad()
    currentPage = 1
  }

  protected abstract fun getRandomProductListUrls(): List<String>

  override fun quit() {
    try {
      driver.quit()
    } catch(ex: Exception) {
      System.err.println("Error quiting SeleniumProductMarket.")
      ex.printStackTrace()
    }
  }

  fun nextPage(): Boolean {
    lastItem = -1
    val nextBtn = findElement(nextPageBtnBy)
    if (nextBtn != null) {
      ++currentPage
      try {
        nextBtn.click()
      } catch (ex: ElementNotInteractableException) {
        --currentPage
        return false
      }
      waitForPageLoad()
      return true
    }
    return false
  }

  fun ensureOnProductListPage() {
    waitForPageLoad()
    if (currentPage == 0) {
      driver.navigate().to(startURL)
      currentPage = 1
    } else {
      findElement(productListPageIdBy)
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
    } catch (ex: Exception) {
      logger.warning("Could not find any elements matching '$by'")
      null
    }
  }

  fun findElements(by: By): List<WebElement> {
    return try {
      driver.findElements(by).toList()
    } catch (ex: Exception) {
      logger.warning("Could not find any elements matching '$by'")
      emptyList()
    }
  }

  fun screenshot(tag: String? = null) {
    if(takeScreenshots) {
      ++screenshotCount

      threads.submit {
        try {
          val src = (driver as TakesScreenshot).getScreenshotAs(OutputType.FILE)
          val destDir = Paths.get("logs", "screenshots")
          val destName = "${Date()}_${tag ?: ""}"
          val destPath = findFreePath(destDir, destName, ".png")
          Files.createDirectories(destDir)
          Files.move(src.toPath(), destPath)
        } catch (ex: Exception) {
          val buf = ByteArrayOutputStream()
          val ps = PrintStream(buf)
          ex.printStackTrace(ps)
          val str = buf.toString(StandardCharsets.UTF_8)
          logger.warning("Failed to take a screenshot\n$str")
        }
      }
    }
  }

  private fun findFreePath(dir: Path, name: String, extension: String): Path {
    val cname = name.replace(':', '.')
    val preferred = dir.resolve("$cname$extension")
    if (!Files.exists(preferred)) {
      return preferred
    }

    var suffix = 0
    while (Files.exists(dir.resolve("$cname$suffix$extension"))) {
      ++suffix
    }
    return dir.resolve("$cname$suffix$extension")
  }

  fun waitForPageLoad(afterDelay: Long = 500) {
    WebDriverWait(driver, 10000).until { webDriver ->
      if (webDriver is JavascriptExecutor)
        return@until (webDriver as JavascriptExecutor).executeScript("return document.readyState") == "complete"
      false
    }
    Thread.sleep(afterDelay)
  }
}

abstract class DetailedProductMarket(
  type: ProductMarketType, name: String, startURL: String,
  productBy: By, titleBy: By, priceBy: By,
  searchInputBy: By, searchBtnBy: By,
  nextPageBtnBy: By, productListPageIdBy: By, val productPageIdBy: By
) : SeleniumProductMarket(type, name, startURL, productBy, titleBy, priceBy,
  searchInputBy, searchBtnBy, nextPageBtnBy, productListPageIdBy) {

  open fun fetchProduct(): RawPosting? {
    val url = driver.currentUrl
    val titleStr = findElement(titleBy)?.text
    val priceStr = findElement(priceBy)?.text

    return if (priceStr != null && titleStr != null) {
      RawPosting(type, url, titleStr, "", CurrencyAmount(priceStr), emptyMap())
    } else {
      null
    }
  }

  override fun fetchProductBatch(maxSize: Long): List<RawPosting> {
    ensureOnProductListPage()
    waitForPageLoad(1000)
    val productElems = findElements(productBy)
    val maxProductI = min(productElems.size, lastItem + 1 + maxSize.toInt())
    val posts = (lastItem + 1 until maxProductI)
      .map { productLink(it) }
      .mapNotNull { link ->
        driver.navigate().to(link)
        waitForPageLoad()
        val product = try {
          fetchProduct()
        } catch (ex: Exception) {
          null
        }
        driver.navigate().back()
        waitForPageLoad()
        logger.info("Posting parsed: $product")
        if (product == null) {
          screenshot("fetch_product_null")
        }
        product
      }

    if (posts.isEmpty()) {
      logger.warning("[$name] No products found")
      screenshot("no_products_found")
    }

    if (lastItem >= productElems.size) {
      nextPage()
    }

    lastItem = maxProductI

    logger.info("Retrieved ${posts.size} postings: $posts")

    return posts
  }

  open fun parsePrice(str: String): CurrencyAmount? {
    return CurrencyAmount(str)
  }

  open fun productLink(i: Int): String {
    return findElements(productBy)[i].getAttribute("href")
  }
}

class Craigslist : DetailedProductMarket(
  ProductMarketType.CRAIGSLIST,"Craigslist", "https://sandiego.craigslist.org/search",
  By.className("result-title"), By.id("titletextonly"), By.className("price"),
  By.id("query"), By.className("searchbtn"), By.cssSelector(".button.next"),
  By.className("pagenum"), By.id("titletextonly")
) {
  override fun getRandomProductListUrls(): List<String> {
    return listOf("https://craigslist.org/")
  }
}

class EBay : DetailedProductMarket(
  ProductMarketType.EBAY, "eBay", "https://www.ebay.com/",
  By.cssSelector(".s-item__link, a[itemprop=url]"), By.id("itemTitle"),
  By.cssSelector("#prcIsum, #prcIsum_bidPrice, *[itemprop=price]"),
  By.id("gh-ac"), By.id("gh-btn"), By.cssSelector("a[rel=next]"),
  By.className("s-answer-region"), By.id("itemTitle")
) {
  private val descriptionBy = By.cssSelector("#desc_div, #ds_div, #ProductDetails")

  override fun fetchProduct(): RawPosting? {
    val url = driver.currentUrl
    val titleStr = findElement(titleBy)?.text ?: ""
    val descrStr = findElement(descriptionBy)?.text ?: findElement(By.id("ds_div"))?.text
    ?: findElement(By.id("ProductDetails"))?.text ?: ""
    val priceStr = findElement(priceBy)?.text ?: ""
    return try {
      val post = EbayRawPosting(url, titleStr, descrStr, CurrencyAmount(priceStr), fetchAttrs())
      post.category = findElement(By.cssSelector("#vi-VR-brumb-lnkLst li:nth-last-of-type(3), .breadcrumb > ol > li:last-of-type"))?.text
      logger.info("Post category: ${post.category}")
      post
    } catch (ex: Exception) {
      ex.printStackTrace()
      null
    }
  }

  private fun fetchAttrs(): Map<String, String> {
    val attrs = HashMap<String, String>()
    val tagList = ArrayList<String>()
    val elems = driver.findElements(By.cssSelector(".itemAttr td"))

    elems.forEach { elem ->
      tagList.add(elem.text.lowercase(Locale.getDefault()).replace(":", ""))
    }

    // Every even td is an attribute label
    // Every odd td is the value of preceding label
    for (i in 1 until tagList.size step 2) {
      attrs[tagList[i - 1]] = tagList[i]
    }
    return attrs
  }

  override fun navigateToRandomProductList() {
    driver.navigate().to(startURL)
    waitForPageLoad()
    // Open categories list
    findElement(By.id("gh-shop-a"))!!.click()
    Thread.sleep(500)
    // Click a random subcategory
    val catLinks = findElements(By.className("scnd"))
    catLinks[Random.nextInt(0, catLinks.size)].click()
    waitForPageLoad()
    // Click a random product list
    val listLinks = findElements(By.cssSelector(".b-visualnav__tile b-visualnav__tile__default, a[class*=nav][class*=tile]"))
    if (listLinks.isEmpty()) {
      navigateToRandomProductList()
      return
    }
    retry(3) { listLinks[Random.nextInt(0, listLinks.size)].click() }
    waitForPageLoad()
    currentPage = 1
  }

  /// @see this#navigateToRandomProductList()
  override fun getRandomProductListUrls(): List<String> {
    return emptyList()
  }
}

class NewEgg : DetailedProductMarket(
  ProductMarketType.NEWEGG, "New Egg", "https://www.newegg.com/",
  By.className("item-title"), By.className("item-title"), By.className("price-current"),
  By.id("haQuickSearchBox"), By.className("search-bar-btn"),
  By.cssSelector("button[aria-label=Next]"),
  By.id("radio_soldby"), By.className("product-buy-box")
) {
  override fun fetchProduct(): RawPosting? {
    val url = driver.currentUrl
    val titleStr = findElement(titleBy)?.text ?: ""
    val priceStr = findElement(priceBy)?.text ?: ""
    return try {
      val post = RawPosting(ProductMarketType.NEWEGG, url, titleStr, "", CurrencyAmount(priceStr), fetchAttrs())
      logger.info("Post category: ${post.category}")
      post
    } catch (ex: Exception) {
      ex.printStackTrace()
      null
    }
  }

  private fun fetchAttrs(): Map<String, String> {
    // Open specs tabs
    waitForPageLoad(3000)
    click(By.xpath("//div[contains(@class, 'tab-nav'), text()='Specs']"))

    val attrs = HashMap<String, String>()
    val elems = driver.findElements(By.cssSelector("#product-details > .tab-panes .table-horizontal tr"))

    elems.forEach { elem ->
      // Elements are hidden, so .text is not functional. textContent works
      val key = elem.findElement(By.tagName("th")).getAttribute("textContent")
      val value = elem.findElement(By.tagName("td")).getAttribute("textContent")
      attrs[key] = value
    }

    return attrs
  }

  override fun getRandomProductListUrls(): List<String> {
    return listOf("https://www.newegg.com/todays-deals")
  }
}

class Overstock : DetailedProductMarket(
  ProductMarketType.OVERSTOCK, "Overstock.com", "https://www.overstock.com/",
  By.className("productCardLink"), By.cssSelector("h1[data-cy='product-title'], .product-title"),
  By.cssSelector("#product-price-price-container, .monetary-price-value"),
  By.className("search_searchBar_de"), By.cssSelector("#headerSearchContainer button[type=submit]"),
  By.cssSelector("a[title='Next Page']"),
  By.id("top-refinements"), By.id("brand-name")
) {
  override fun fetchProduct(): RawPosting? {
    val brandBy = By.cssSelector("#brand-name a, div[data-cy='brand-name'] > a")

    val url = driver.currentUrl
    val title = findElement(titleBy)?.text
    val priceStr = findElement(priceBy)?.text ?: ""
    val brandStr = findElement(brandBy)?.text ?: ""
    val attrs = fetchAttrs().plus(Pair("brand", brandStr))
    val price = parsePrice(priceStr)
    return if (price != null && title != null) {
      RawPosting(type, url, title, "", price, attrs)
    } else {
      null
    }
  }

  private fun fetchAttrs(): Map<String, String> {
    val attrs = HashMap<String, String>()
    val elems = driver.findElements(By.xpath("//h3[text()='Specifications']/following-sibling::section/div/div"))

    elems.forEach { elem ->
      val key = elem.findElement(By.cssSelector("div:nth-of-type(1)")).text
      val value = elem.findElement(By.cssSelector("div:nth-of-type(2)")).text
      attrs[key] = value
    }

    return attrs
  }

  override fun parsePrice(str: String): CurrencyAmount? {
    val len = str.length
    return if (len > 0) {
      val cents = str.substring(len - 2)
      val dollars = str.substring(0, len - 2)
      CurrencyAmount("$dollars.$cents")
    } else {
      null
    }
  }

  override fun getRandomProductListUrls(): List<String> {
    return listOf(
      "https://www.overstock.com/Home-Garden/Rugs/244/cat.html?TID=SALESDEALS:04:05:Rugs",
      "https://www.overstock.com/Home-Garden/Living-Room-Furniture/713/cat.html",
      "https://www.overstock.com/Home-Garden/Patio-Furniture/714/cat.html",
      "https://www.overstock.com/Home-Garden/Bedroom-Furniture/710/cat.html",
      "https://www.overstock.com/Home-Garden/Home-Office-Furniture/712/cat.html",
      "https://www.overstock.com/Home-Garden/Dining-Room-Bar-Furniture/711/cat.html"
    )
  }
}

fun createMarket(ty: ProductMarketType): ProductMarket {
  return when (ty) {
    ProductMarketType.CRAIGSLIST -> Craigslist()
    ProductMarketType.EBAY -> EBay()
    ProductMarketType.NEWEGG -> NewEgg()
    ProductMarketType.OVERSTOCK -> Overstock()
    ProductMarketType.WALMART -> EBay() // TODO FIX
  }
}

private fun findExecutablePath(name: String): String? {
  return try {
    val p = Runtime.getRuntime().exec("whereis $name")
    val br = BufferedReader(InputStreamReader(p.inputStream))
    val output = br.readLine()
    output.split(" ")[1]
  } catch (ex: Exception) {
    return null
  }
}
