package com.jaredloomis.silk.crawl

import org.openqa.selenium.JavascriptExecutor

class Crawler<Element, Out>(
  val startUrl: String,
  val driver: CrawlDriver<Element>, val visitors: Array<CrawlVisitor<Element, Out>>
) {
  fun start() {
    driver.open()
    driver.navigateTo(startUrl)
  }

  fun quit() {
    driver.quit()
  }

  fun next(): List<Out> {
    val url = driver.getCurrentUrl()

    // Run any pre visitors
    visitors.filter { it.pre }.forEach {
      println("RUNNING PRE")
      it.visit(driver)
    }
    // Run the rest of the visitors
    return visitors.filter { !it.pre }.map {
      println("RUNNING NONEPRE")
      it.visit(driver)
    }
  }

  fun isActive(): Boolean {
    return try {
      // Make sure we're still connected to WebDriver
      ((driver as CrawlDriver.Selenium).driver as JavascriptExecutor).executeScript("return true;") as Boolean
    } catch(ex: Exception) {
      false
    }
  }
}

/**
 * @param pushback a number 0-255 indicating the degree of "pushback" (anti-bot mechanisms) to be expected in the
 *                 next few minutes.
 */
data class CrawlerState(val pushback: Byte)
