package com.jaredloomis.silk.scrape.realestate

import com.jaredloomis.silk.model.CurrencyAmount
import com.jaredloomis.silk.model.realestate.RealEstateListing
import com.jaredloomis.silk.scrape.SeleniumScraper
import com.jaredloomis.silk.scrape.product.PerimeterXCaptchaSolver
import com.jaredloomis.silk.util.SimpleIterator
import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.Keys
import java.time.LocalDate
import java.util.function.Supplier

abstract class RealEstateScraper(
  private val startUrl: String,
  protected val listingURL: Regex,
  private val searchInputBy: By, protected val nextPageBtnBy: By
): SeleniumScraper() {
  fun search(text: String): Iterator<RealEstateListing> {
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
        .filter { listingURL.matches(it.second) }
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
      val listing = parseListing()
      driver.navigate().back()
      waitForPageLoad()
      listing
    }

    return SimpleIterator({ true }) {
      var x: RealEstateListing? = sup.get()
      while(x == null) {
        x = sup.get()
      }
      x
    }
  }

  abstract fun parseListing(): RealEstateListing?
}

class ZillowScraper : RealEstateScraper("https://zillow.com/", Regex(".*zillow\\.com/homedetails.*"), By.cssSelector(".srp-search-box input,#search-box-input"), By.cssSelector("a[rel=next]")) {
  override fun parseListing(): RealEstateListing? {
    return try {
      val bedBathSqft = findElement(By.cssSelector(".ds-bed-bath-living-area-container"))!!.findElements(By.cssSelector("*")).map { it.text }
      val beds = bedBathSqft.find { it.contains("bd") }!!.split(" ")[0].toInt()
      val baths = bedBathSqft.find { it.contains("ba") }!!.split(" ")[0].toInt()
      val sqft = bedBathSqft.find { it.contains("sqft") }!!.split(" ")[0].toInt()
      val images = (driver as JavascriptExecutor).executeScript("return Array.from(document.querySelectorAll('.media-stream img')).map(el => el.getAttribute(\"src\"));") as List<String>
      RealEstateListing(
        findElement(By.cssSelector("#ds-chip-property-address"))!!.text,
        CurrencyAmount(findElement(By.cssSelector("#ds-summary-row > span > span > span"))!!.text),
        beds, baths, sqft, images.toSet(), LocalDate.EPOCH
      )
    } catch(ex: Exception) {
      ex.printStackTrace()
      null
    }
  }

  override fun handleCaptcha() {
    Thread.sleep(1000)
    // Check for captcha, solve
    if(findElement(By.id("px-captcha")) != null || driver.currentUrl.contains("/perimeterXCaptcha")) {
      PerimeterXCaptchaSolver(driver).solve()
    }
  }
}