package com.jaredloomis.silk.scrape.product

import com.jaredloomis.silk.model.CurrencyAmount
import com.jaredloomis.silk.model.product.RawPosting
import com.jaredloomis.silk.scrape.SeleniumScraper
import com.jaredloomis.silk.util.SimpleIterator
import com.jaredloomis.silk.util.getLogger
import com.jaredloomis.silk.util.printExceptions
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.openqa.selenium.*
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxOptions
import org.openqa.selenium.interactions.Actions
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStreamReader
import java.net.http.HttpClient
import java.nio.Buffer
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.function.Supplier
import java.util.logging.Logger
import java.util.stream.Collectors
import javax.imageio.ImageIO
import kotlin.math.abs


interface ProductScraper {
  fun search(text: String): Iterator<RawPosting?>
  fun randomProductList(): Iterator<RawPosting?>

  /**
   * Helps to optimize fetching (might as well fetch all products in the list).
   *
   * @return an exact or approximate number of products visible on the product list page.
   */
  fun getPageSize(): Int
}

abstract class SeleniumProductScraper : ProductScraper, SeleniumScraper {
  constructor() : super() {}
  constructor(driver: WebDriver) : super(driver) {}
}

abstract class DetailedFastProductScraper(
  protected val queryUrl: String, protected val randomUrls: List<String>,
  protected val productPageURL: Regex,
  protected val approxPageSize: Int,
): ProductScraper {
  protected abstract fun parsePost(doc: Document): RawPosting?

  override fun search(text: String): Iterator<RawPosting?> {
    val doc = fetch(queryUrl.replace("{}", text))

    println(doc)

    val pageURLs = doc?.select("a")?.stream()
      ?.map { it.absUrl("href") }
      ?.filter { productPageURL.matches(it) }
      ?.collect(Collectors.toList())

    var i = 0

    return SimpleIterator({ pageURLs != null && i < pageURLs.size }) {
      val page = fetch(pageURLs!![i++])
      if (page != null)
        parsePost(page)
      else
        null
    }
  }

  override fun randomProductList(): Iterator<RawPosting?> {
    TODO("Not yet implemented")
  }

  override fun getPageSize(): Int {
    return approxPageSize
  }

  protected fun fetch(uri: String): Document? {
    return try {
      return Jsoup.connect(uri).get()
    } catch (e: java.lang.Exception) {
      e.printStackTrace()
      null
    }
  }
}

abstract class DetailedSeleniumProductScraper(
  protected val startUrl: String, protected val approxPageSize: Int,
  protected val productListURL: Regex, protected val productPageURL: Regex,
  protected val searchInputBy: By, protected val nextPageBtnBy: By
) : SeleniumProductScraper() {
  private val log: Logger = getLogger(this::class)

  protected abstract fun parsePost(): RawPosting?

  override fun search(text: String): Iterator<RawPosting?> {
    driver.get(startUrl)
    waitForPageLoad()

    handleCaptcha()

    // Search
    findElement(searchInputBy)?.sendKeys(text, Keys.ENTER)
    waitForPageLoad()

    val sup = Supplier {
      // Go to a random product page, or next page if we've already seen every product.
      // If there is no next page, return null.
      val links = findElements(By.tagName("a"))
      val productLinks = links
        .asSequence()
        .map { Pair(it, it.getAttribute("href")) }
        .filter { it.second != null }
        .filter { productPageURL.matches(it.second) }
        .filter { !visitedURLs.contains(it.second) }
        .map { it.first }
        .toList()
      if(productLinks.isNotEmpty()) {
        val link = productLinks.random()
        scrollTo(link)
        try {
          link.click()
        } catch(ex: Exception) {
          ex.printStackTrace()
          return@Supplier null
        }
      } else {
        val nextPageBtn = findElement(nextPageBtnBy)
        if(nextPageBtn != null) {
          nextPageBtn.click()
        } else {
          return@Supplier null
        }
      }
      waitForPageLoad()

      // Parse product post, and go back to product list
      val post = parsePost()
      driver.navigate().back()
      waitForPageLoad()
      post
    }

    return SimpleIterator({ true }) {
      var x: RawPosting? = sup.get()
      while(x == null) {
        x = sup.get()
      }
      x
    }
  }

  override fun randomProductList(): Iterator<RawPosting> {
    var onProductList = false

    val sup = Supplier {
      waitForPageLoad()
      Thread.sleep(1000)
      handleCaptcha()

      // Go to a random product list.
      // If no product list links are found, click a random link, and wait for the next iteration.
      if(!onProductList) {
        println("NOT ON PRODUCT LIST \n\n\n")
        driver.get(startUrl)
        val productListLinks = getAllLinks(productListURL)
          .filter { !visitedURLs.contains(it.getAttribute("href")) }
        if (productListLinks.isNotEmpty()) {
          println("CLICKING A PRODUCT LIST LINK \n\n\n")
          printExceptions { productListLinks.random().click() }
          waitForPageLoad()
          onProductList = true
        } else {
          println("CLICKING A RANDOM LINK \n\n\n")
          val links = findElements(By.tagName("a"))
          links.random().click()
          waitForPageLoad()
          return@Supplier null
        }
      } else {
        println("ON PRODUCT LIST")
      }

      // Go to a random product page, or next page if we've already seen every product.
      // If there is no next page, restart the process by setting onProductList = false.
      val productLinks = getAllLinks(productPageURL)
        .filter { !visitedURLs.contains(it.getAttribute("href")) }
      if(productLinks.isNotEmpty()) {
        printExceptions { productLinks.random().click() }
      } else {
        val nextPageBtn = findElement(nextPageBtnBy)
        if(nextPageBtn != null) {
          nextPageBtn.click()
        } else {
          onProductList = false
          return@Supplier null
        }
      }
      waitForPageLoad()

      // Parse product post, and go back to product list
      val post = parsePost()
      driver.navigate().back()
      waitForPageLoad()
      post
    }

    return SimpleIterator({ true }) {
      var x: RawPosting? = sup.get()
      while(x == null) {
        x = sup.get()
      }
      x
    }
  }

  override fun getPageSize(): Int {
    return approxPageSize
  }
}

class WalmartProductScraper : DetailedSeleniumProductScraper(
  "https://walmart.com", 40,
  Regex(".*walmart\\.com/browse/.*"), Regex(".*walmart\\.com/ip/.*"),
  By.id("global-search-input"), By.className("paginator-btn-next")
) {
  private val titleBy = By.className("prod-ProductTitle")
  private val priceBy = By.cssSelector("#price > .price-group, span[itemprop='price']")
  private val brandNameBy = By.cssSelector(".prod-brandName")
  private val secondaryInfoBy = By.cssSelector(".prod-productsecondaryinformation > div")
  private val specBy = By.className("product-specification-row")
  private val spec2By = By.className("pb2")

  override fun parsePost(): RawPosting? {
    val title = findElement(titleBy)?.text
    val price = findElement(priceBy)?.text
    return if(title != null && price != null) {
      RawPosting(ProductMarketType.WALMART, driver.currentUrl, title, "", CurrencyAmount(price), parseSpecs())
    } else {
      null
    }
  }

  private fun parseSpecs(): Map<String, String> {
    val ret = HashMap<String, String>()

    // Parse brand
    val brandElem = findElement(brandNameBy)
    if(brandElem != null) {
      ret["brand"] = brandElem.text
    }

    // Parse secondary information
    val secondaryInfoElems = findElements(secondaryInfoBy)
    secondaryInfoElems.forEach {
      val text = it.text
      if(text.contains(":")) {
        val parts = text.split(":")
        val key = parts[0]
        val value = parts[1]
        ret[key] = value
      }
    }

    // Parse specs
    val specElems = findElements(specBy)
    specElems.forEach {
      try {
        val key = it.findElement(By.cssSelector("td:nth-of-type(1)")).text
        val value = it.findElement(By.cssSelector("td:nth-of-type(2)")).text.replace("\n", " ")
        ret[key] = value
      } catch(ex: Exception) {
        ex.printStackTrace()
      }
    }

    // Parse alternate spec format
    val spec2Elems = findElements(spec2By)
    spec2Elems.forEach {
      try {
        val key = it.findElement(By.cssSelector("h3")).text
        val value = it.findElement(By.cssSelector("p > span")).text.replace("\n", " ")
        ret[key] = value
      } catch(ex: Exception) {
        ex.printStackTrace()
      }
    }

    return ret
  }

  override fun handleCaptcha() {
    Thread.sleep(1000)
    // Check for captcha, solve
    if(findElement(By.id("px-captcha")) != null || driver.currentUrl.contains("/blocked")) {
      PerimeterXCaptchaSolver(driver).solve()
    }
  }
}

class PerimeterXCaptchaSolver(driver: WebDriver): SeleniumScraper(driver) {
  private val log: Logger = getLogger(this::class)

  fun solve(tries: Int = 5) {
    // Try to solve automatically
    var i = 0
    while(findElement(By.id("px-captcha")) != null && i < tries) {
      attemptSolve()
      ++i
    }
    // Ask a user to solve it
    if(driver.currentUrl.contains("blocked")) {
      println("!!!! Please solve the captcha and press enter when done.")
      try {
        val scanner = Scanner(System.`in`)
        scanner.nextLine()
      } catch(ex: Exception) {
        ex.printStackTrace()
      }
      println("DONE READING LINE")
    }
  }

  private fun attemptSolve(testClickDurationMs: Long = 1000) {
    waitForElement(By.id("px-captcha"))!!
    val maxError = .2
    val maxTests = 5
    val clickTimeEstimates = ArrayList<Long>(maxTests)
    val percentTarget = .60
    var tcDuration = testClickDurationMs

    while(true) {
      // Click and hold for tcDuration, take screenshot. Retry if screenshot fails.
      val imgAndError = clickAndHoldAndScreenshot(findElement(By.id("px-captcha"))!!, tcDuration)
      if(imgAndError == null) {
        Thread.sleep(tcDuration)
        continue
      }
      val img = imgAndError.first
      val screenshotErrorMs = imgAndError.second
      // If time between screenshot and click ending is too long, retry
      if(screenshotErrorMs > 125) {
        // Wait for "unload"
        Thread.sleep(tcDuration)
        continue
      }

      // Calculate percent loaded based on screenshot
      val percent = percentLoaded(img)

      // Calculate total click time
      val clickTimeMs = (tcDuration.toDouble() / percent).toLong()
      clickTimeEstimates.add(clickTimeMs)

      // Wait for "unload"
      Thread.sleep(tcDuration)

      // Calculate next test click duration
      println("$percent, $percentTarget")
      tcDuration = (clickTimeMs * percentTarget).toLong()
      println("${abs(percent - percentTarget)}, $maxError")
      if(abs(percent - percentTarget) < maxError) {
        println("BROKE")
        break
      }
    }

    // Take average of estimates for click time, use that as the attempt click time
    val clickTimeMs = clickTimeEstimates.fold(0L) {acc, i -> acc + i } / clickTimeEstimates.size
    Actions(driver).clickAndHold(findElement(By.id("px-captcha"))!!).pause(clickTimeMs).release().build().perform()
  }

  /**
   * Click an hold target for clickDurationMs, and take a screenshot at the time the click is released.
   * In the case of an error, exceptions are swallowed and a null is returned. Please retry.
   * @return Pair(img, duration between the time the click was released and the time the screenshot was taken).
   */
  private fun clickAndHoldAndScreenshot(target: WebElement, clickDurationMs: Long): Pair<BufferedImage, Long>? {
    var screenshotTime: Instant? = null
    var img: BufferedImage? = null
    // Start a thread to take a screenshot
    val screenshotThread = Thread {
      try {
        Thread.sleep(clickDurationMs)
        // Take screenshot
        val bytes = target.getScreenshotAs(OutputType.BYTES)
        val screenshotEndTime = Instant.now()
        screenshotTime = screenshotEndTime
        val bais = ByteArrayInputStream(bytes)
        img = ImageIO.read(bais)
        ImageIO.write(img, "png", File("PerimeterX-button.png"))
      } catch(ex: Exception) {}
    }
    screenshotThread.start()
    // Click and hold the button for 500ms
    Actions(driver).clickAndHold(target).pause(clickDurationMs).release().build().perform()
    val clickEndTime = Instant.now()
    screenshotThread.join()
    return if(img != null && screenshotTime != null) {
      val screenshotErrorMs = Duration.between(clickEndTime, screenshotTime).toMillis()
      Pair(img!!, screenshotErrorMs)
    } else {
      null
    }
  }
}

private fun percentLoaded(img: BufferedImage): Double {
  // CONFIGURATION (may need to tune)
  // RGB below 80,80,80 will be interpreted as black / loaded.
  val maxValue = 80
  // y coordinate indicating where to extract a row of pixels from (NB: important to avoid the button text)
  val y = 39 // img.height / 3

  /// Get a row of pixels
  val stripImg = img.getSubimage(0, y, img.width, 1)

  ImageIO.write(stripImg, "png", File("PerimeterX-button-strip.png"))

  // List of pairs indicating black/white (true = black), and the starting x location in pixels
  val stripSections: MutableList<Pair<Boolean, Int>> = ArrayList()
  var currentColor: Boolean? = null
  for(x in (0 until stripImg.width)) {
    val rgba = stripImg.getRGB(x, 0)
    val col = Color(rgba)
    val r = col.red
    val g = col.green
    val b = col.blue
    val intensity = (r + g + b) / 3
    // Section change
    val pixelColor = intensity < maxValue
    if(currentColor == null) {
      currentColor = pixelColor
      stripSections.add(Pair(currentColor, x))
    } else if(pixelColor != currentColor) {
      stripSections.add(Pair(pixelColor, x))
      currentColor = pixelColor
    }
  }

  println(stripSections.fold("") { acc, x -> "$acc (${x.first},${x.second})"})

  val loadedSectionCount = stripSections.filter { it.first }.size
  return if(loadedSectionCount == 2) {
    val borderPlusLoadedIx = stripSections.indexOfFirst { it.first }
    val borderPlusLoaded = stripSections[borderPlusLoadedIx + 1].second.toDouble() - stripSections[borderPlusLoadedIx].second.toDouble()
    val notYetLoaded = stripSections[borderPlusLoadedIx + 2].second.toDouble() - stripSections[borderPlusLoadedIx + 1].second.toDouble()
    val rightBorder = if (stripSections.size > borderPlusLoadedIx + 3) {
      stripSections[borderPlusLoadedIx + 3].second.toDouble() - stripSections[borderPlusLoadedIx + 2].second.toDouble()
    } else {
      stripImg.width.toDouble() - stripSections[borderPlusLoadedIx + 2].second.toDouble()
    }
    val loaded = borderPlusLoaded - rightBorder
    val total = loaded + notYetLoaded
    (loaded / total) * 1.0
  } else {
    1.0
  }
}
