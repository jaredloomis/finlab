package com.jaredloomis.silk.crawl

import com.fasterxml.jackson.databind.annotation.JsonAppend.Prop
import com.jaredloomis.silk.model.CurrencyAmount
import com.jaredloomis.silk.model.realestate.PropertyType
import com.jaredloomis.silk.model.realestate.RealEstateListing
import com.jaredloomis.silk.scrape.product.PerimeterXCaptchaSolver
import com.jaredloomis.silk.util.getLogger
import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.Keys
import org.openqa.selenium.WebElement
import org.openqa.selenium.interactions.Actions
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalUnit

fun zillowCrawler(locations: List<String>, listingsPerLocation: Int? = 5, loopThreshold: Int = 7): Crawler<WebElement, RealEstateListing?> {
  val startUrl = "https://zillow.com"
  val locs = locations.toMutableList()
  var crawledListingCount = 0
  var locationI = 0
  val logger = getLogger("zillowCrawler")

  // On the home page, just type in the next location
  val landingPageVisitor = CrawlVisitor<WebElement, RealEstateListing?>(false) { driver: CrawlDriver<*> ->
    if(!landingPageRegex.matches(driver.getCurrentUrl())) return@CrawlVisitor null

    driver.click(Sels.homePageSearchBar)
    Thread.sleep(1000)
    driver.sendText(Sels.homePageSearchBar, locs[locationI] + "\n")
    Thread.sleep(1000)
    // Select for sale listings
    driver.click(Sel.CSS(".lightbox-body ul > li:nth-of-type(1) > button"))
    Thread.sleep(1000)
    null
  }

  // On the PerimeterX page, solve captcha. After 7 attempts, text/email me and await user input.
  // TODO get the real URL
  val perimeterXVisitor = CrawlVisitor<WebElement, RealEstateListing?>(true) { driver: CrawlDriver<*> ->
    if(!Regex(".*erimeter.*").matches(driver.getCurrentUrl())) return@CrawlVisitor null

    // switch vpn, restart browser
    Runtime.getRuntime().exec("sh ${Paths.get("scripts/reconnect-to-vpn").toAbsolutePath()}")
    Thread.sleep(7000)
    driver.quit()
    driver.open()
    driver.goTo(startUrl)
    // any other workarounds, then send text/email
    null
  }

  // On the 'homes list' page, navigate to a home details page. The other visitors will handle the rest.
  val homesListVisitor = CrawlVisitor<WebElement, RealEstateListing?>(false) { driver: CrawlDriver<*>  ->
    if(!driver.exists(Sel.CSS("#search-page-react-content"))) return@CrawlVisitor null
    if(driver.exists(Sel.CSS("#details-page-container"))) return@CrawlVisitor null

    // Remove boundary around searched city, to allow more listings
    if(driver.exists(Sel.CSS(".remove-boundary"))) driver.click(Sel.CSS(".remove-boundary"))
    // Search for a new location if we've seen enough of this one
    if(listingsPerLocation != null && listingsPerLocation >= 0 && crawledListingCount >= listingsPerLocation) {
      Thread.sleep(1000)
      locationI = (locationI + 1) % locs.size
      val searchBar = (driver as CrawlDriver.Selenium).driver.findElement(By.cssSelector("#srp-search-box input:nth-of-type(1)"))
      // Clear search bar (difficult to clear...)
      val currentSearch = searchBar.getAttribute("value")
      (1..currentSearch.length + 10).fold(Actions(driver.driver).moveToElement(searchBar).pause(500).doubleClick()) { actions, _i ->
        actions.click().sendKeys(Keys.BACK_SPACE).pause(100)
      }.perform()
      driver.sendText(Sel.CSS("#srp-search-box input:nth-of-type(1)"), "")
      driver.sendText(Sel.CSS("#srp-search-box input:nth-of-type(1)"), locs[locationI] + "\n")
      crawledListingCount = 0
      null
    } else {
      // XXX Selenium specific
      driver as CrawlDriver.Selenium
      val links = driver.findElements(Sel.CSS("a"))
      val productLinks = links
        .asSequence()
        .map { Pair(it, it.getAttribute("href")) }
        .filter { it.second != null }
        .filter { homeDetailsUrlRegex.matches(it.second) }
        .map { it.first }
        .toList()
      val unvisitedProductLinks = productLinks
        .filter { !driver.visitedURLs.contains(it.getAttribute("href")) }
      if(unvisitedProductLinks.isNotEmpty()) {
        val link = unvisitedProductLinks.random()
        (driver.driver as JavascriptExecutor).executeScript("arguments[0].scrollIntoView(true);", link)
        try {
          link.click()
          Thread.sleep(1000)
        } catch(ex: Exception) {
          ex.printStackTrace()
        }
      } else {
        // If there are no results from the search, go to the next search
        if(productLinks.isNotEmpty()) {
          try {
            driver.findElement(Sels.nextPageBtn)!!.click()
            Thread.sleep(1000)
            return@CrawlVisitor null
          } catch(ex: Exception) {
            ex.printStackTrace()
          }
        }
        locationI = (locationI + 1) % locs.size
        val searchBar = (driver as CrawlDriver.Selenium).driver.findElement(By.cssSelector("#srp-search-box input:nth-of-type(1)"))
        // Clear search bar (difficult to clear...)
        val currentSearch = searchBar.getAttribute("value")
        (1..currentSearch.length + 10).fold(Actions(driver.driver).moveToElement(searchBar).pause(500).doubleClick()) { actions, _i ->
          actions.click().sendKeys(Keys.BACK_SPACE).pause(100)
        }.perform()
        driver.sendText(Sel.CSS("#srp-search-box input:nth-of-type(1)"), "")
        driver.sendText(Sel.CSS("#srp-search-box input:nth-of-type(1)"), locs[locationI] + "\n")
      }
      driver.waitForPageLoad()
      null
    }
  }

  val homeDetailsVisitor = CrawlVisitor<WebElement, RealEstateListing?>(false) { driver: CrawlDriver<*>  ->
    if(!homeDetailsUrlRegex.matches(driver.getCurrentUrl())) return@CrawlVisitor null
    try {
      val listing = ZillowListingParser(driver).parseListing()
      crawledListingCount += 1
      driver.back()
      listing
    } catch(ex: Exception) {
      driver.back()
      throw ex
    }
  }

  // Loop detector visitor
  val history = ArrayDeque<String>(loopThreshold)
  val loopDetectorVisitor = CrawlVisitor<WebElement, RealEstateListing?>(true) { driver: CrawlDriver<*>  ->
    history.add(driver.getCurrentUrl())
    val isLoop = history.fold(Pair(true, null as String?)) { acc, url ->
      Pair(acc.first && (acc.second == null || acc.second == url), acc.second ?: url)
    }.first
    if(isLoop) {
      logger.warning("Loop detected! Restarting.")
      driver.quit()
      driver.open()
      driver.navigateTo(startUrl)
    } else if(history.size >= loopThreshold) {
      history.removeFirst()
    }
    null
  }

  return Crawler(startUrl, CrawlDriver.Selenium(CrawlDriver.Selenium.Options.default()),
    arrayOf(loopDetectorVisitor, perimeterXVisitor, landingPageVisitor, homesListVisitor, homeDetailsVisitor)
  )
}

object Sels {
  val homePageSearchBar = Sel.CSS(".srp-search-box input,#search-box-input")

  val nextPageBtn = Sel.CSS("a[rel=next]")
}

val homeDetailsUrlRegex = Regex(".*zillow\\.com/homedetails.*")
val landingPageRegex = Regex(".*zillow\\.com[^a-zA-Z]*$")

class ZillowListingParser(val driver: CrawlDriver<*>) {
  fun parseListing(): RealEstateListing? {
    val url = driver.getCurrentUrl()
    val address = driver.getText(Sel.CSS(".summary-container h1"))
    val price = CurrencyAmount(driver.getText(Sel.CSS("*[data-testid=price]")))
    val bedBathSqftText = driver.executeJavascript("return document.querySelector(\"*[data-testid='bed-bath-beyond']\").innerText;") as String
    println("BED BATH SQFT: $bedBathSqftText")
    val beds = try { parseWithSuffix("\\d+", "bd", bedBathSqftText)?.toShort() } catch(ex: Exception) { null }
    val baths = try { parseWithSuffix("\\d+", "ba", bedBathSqftText)?.toShort() } catch(ex: Exception) { null }
    val sqft = try { parseWithSuffix("\\d[\\d,]+\\d", "sqft", bedBathSqftText)?.replace(",", "")?.toInt() } catch(ex: Exception) { null }
    val imgs = emptySet<String>()
    Thread.sleep(1000)
    //val timeElem = (driver as CrawlDriver.Selenium).driver.findElements(By.tagName("dt")).filter { it.text.lowercase().contains("time on zillow") }[0]
    //val timeTxt = timeElem.text
    val timeTxt = driver.executeJavascript("return [...document.querySelectorAll('dt')].filter(x => x.innerText.toLowerCase().indexOf(\"time on zillow\") > -1)[0].parentNode.querySelector(\"dd > strong\").innerText;") as String
    val timeOnZillow = parseDuration(timeTxt)
    val postedAt = timeOnZillow?.let { LocalDateTime.now().minus(it).toLocalDate() }
    val facts = driver.executeJavascript("return [...document.querySelectorAll('h4')].filter(h => h.innerText.toLowerCase().indexOf('fact') > -1)[0].parentNode.innerText") as String
    val propertyType = parsePropertyType(facts)
    val attrs = HashMap<String, Any>()
    val hoaMonthly = parseHOA(facts)
    if(hoaMonthly != null) {
      attrs["hoaMonthly"] = hoaMonthly
    }
    attrs["facts"] = facts
    if(propertyType == PropertyType.LAND) {

    }
    return RealEstateListing(url, address, price, propertyType, beds, baths, sqft, imgs, postedAt, Instant.now(), attrs)
  }

  private fun parseDuration(txt: String): Duration? {
    return try {
      val txtParts = txt.split(Regex("\\s+"))
      if(txtParts.size != 2) {
        println("Couldn't parse duration: $txt")
        null
      } else {
        val unitStr = if(txtParts[1].endsWith('s')) {
          txtParts[1].uppercase()
        } else {
          txtParts[1].uppercase() + 's'
        }
        Duration.of(Integer.parseInt(txtParts[0].replace(",", "")).toLong(), ChronoUnit.valueOf(unitStr))
      }
    } catch(ex: Exception) {
      println("Failed to parse duration: $txt")
      null
    }
  }

  private fun parsePropertyType(txt: String): PropertyType? {
    val txtL = txt.lowercase()
    return when {
      txtL.contains("land") -> PropertyType.LAND
      txtL.contains("single family") -> PropertyType.HOUSE
      txtL.contains("townhouse") -> PropertyType.HOUSE
      txtL.contains("manufactured") -> PropertyType.MANUFACTURED
      txtL.contains("triplex") -> PropertyType.APARTMENT
      txtL.contains("duplex") -> PropertyType.APARTMENT
      txtL.contains("condominium") -> PropertyType.APARTMENT
      txtL.contains("multi") -> PropertyType.APARTMENT
      else -> null
    }
  }

  private fun parseHOA(txt: String): CurrencyAmount? {
    val res = Regex("(\\$[\\d,.]+)\\s+([a-zA-Z])\\s+HOA fee").find(txt)
    return when(res?.groups?.get(2)?.value?.lowercase()) {
      "monthly" -> res.groups[1]?.value?.let { CurrencyAmount(it) }
      "yearly"  -> res.groups[1]?.value?.let { CurrencyAmount(it).div(12) }
      else      -> null
    }
  }

  private fun parseWithSuffix(regex: String, suffix: String, str: String): String? {
    return Regex("($regex)\\s*$suffix").find(str)?.groups?.get(1)?.value
  }
}