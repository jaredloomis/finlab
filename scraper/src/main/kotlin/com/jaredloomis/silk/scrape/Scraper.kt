package com.jaredloomis.silk.scrape

import com.jaredloomis.silk.util.getLogger
import org.openqa.selenium.*
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxOptions
import org.openqa.selenium.support.ui.WebDriverWait
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Paths
import java.util.HashSet
import java.util.logging.Logger

open class SeleniumScraper {
  constructor() {}

  constructor(driver: WebDriver) {
    this.driver = driver
  }

  protected lateinit var driver: WebDriver
  protected val visitedURLs: MutableSet<String> = HashSet()

  private val log: Logger = getLogger(this::class)

  var headless = true
  var backend = "chrome"
  var userAgent = "Mozilla/5.0 (X11; CrOS x86_64 14268.67.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.111 Safari/537.36"

  fun open() {
    if(!::driver.isInitialized) {
      driver = when(backend.lowercase()) {
        "chrome", "chromium" -> {
          val options = ChromeOptions()
            .setHeadless(headless)
            .addArguments("user-agent=$userAgent")
            .addArguments("--load-extension=assets/uBlock0.chromium")
            .addArguments("disable-infobars")
            .addArguments("--disable-blink-features=AutomationControlled")
            .setExperimentalOption("excludeSwitches", arrayOf("enable-automation"))

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
            .addArguments("user-agent=$userAgent")
            .addArguments("disable-infobars")

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

  fun close() {
    driver.quit()
  }

  protected open fun handleCaptcha() {}

  protected fun scrollTo(element: WebElement) {
    (driver as JavascriptExecutor).executeScript("arguments[0].scrollIntoView(true);", element)
  }

  protected fun getAllLinks(regex: Regex): List<WebElement> {
    return (driver as JavascriptExecutor).executeScript(
      """
        var reg = new RegExp(arguments[0]);
        return Array.prototype.slice.call(document.querySelectorAll('a')).filter(e => reg.test(e.href))
        """, regex.pattern) as List<WebElement>
  }

  protected fun findElement(by: By): WebElement? {
    return try {
      driver.findElement(by)
    } catch (ex: Exception) {
      log.warning("Could not find any elements matching '$by'")
      null
    }
  }

  protected fun findElements(by: By): List<WebElement> {
    return try {
      driver.findElements(by).toList()
    } catch (ex: Exception) {
      log.warning("Could not find any elements matching '$by'")
      emptyList()
    }
  }

  protected fun waitForPageLoad(beforeDelayMs: Long = 100, timeoutSeconds: Long = 10, captchaCheck: Boolean = true) {
    visitedURLs.add(driver.currentUrl)
    Thread.sleep(beforeDelayMs)
    WebDriverWait(driver, timeoutSeconds).until { webDriver ->
      if (webDriver is JavascriptExecutor)
        return@until (webDriver as JavascriptExecutor).executeScript("return document.readyState") == "complete"
      false
    }
    if(captchaCheck) {
      handleCaptcha()
    }
  }

  protected fun waitUntilClickable(by: By, timeoutSeconds: Long = 8): WebElement? {
    return try {
      val w = WebDriverWait(driver, timeoutSeconds)
      w.until {
        try {
          driver.findElement(by).click()
          true
        } catch(ex: Exception) {
          false
        }
      }
      return driver.findElement(by)
    } catch(ex: TimeoutException) {
      log.warning("Timed out waiting for '$by'")
      null
    } catch (ex: Exception) {
      log.warning("Error waiting for '$by'")
      null
    }
  }

  protected fun waitForElement(by: By, timeoutSeconds: Long = 8): WebElement? {
    return try {
      val w = WebDriverWait(driver, timeoutSeconds)
      w.until {
        try {
          driver.findElement(by).isDisplayed
        } catch(ex: Exception) {
          false
        }
      }
      return driver.findElement(by)
    } catch(ex: TimeoutException) {
      log.warning("Timed out waiting for '$by'")
      null
    } catch (ex: Exception) {
      log.warning("Error waiting for '$by'")
      null
    }
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
