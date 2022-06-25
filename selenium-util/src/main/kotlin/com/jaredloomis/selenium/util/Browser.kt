package com.jaredloomis.selenium.util

import org.openqa.selenium.*
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxOptions
import org.openqa.selenium.support.ui.WebDriverWait
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.Duration

class Browser(private val fingerprint: BrowserFingerprint) : WebDriver {
  lateinit var driver: WebDriver

  val history = HashSet<String>()

  fun open() {
    driver = when(fingerprint.browserType) {
      BrowserType.CHROME -> {
        val options = ChromeOptions()
          .addArguments(fingerprint.arguments)
          .setHeadless(fingerprint.headless)
          .addArguments("user-agent=${fingerprint.userAgent}")
          .addArguments("window-size=${fingerprint.windowSize.first},${fingerprint.windowSize.second}")
          .setExperimentalOption("excludeSwitches", arrayOf("enable-automation"))

        if(System.getProperty("webdriver.chrome.driver") == null) {
          System.setProperty(
            "webdriver.chrome.driver",
            findExecutablePath("chromedriver")!!
          )
        }
        ChromeDriver(options)
      }
      BrowserType.FIREFOX -> {
        val options = FirefoxOptions()
          .addArguments(fingerprint.arguments)
          .setHeadless(fingerprint.headless)
          .addArguments("user-agent=${fingerprint.userAgent}")
          .addArguments("window-size=${fingerprint.windowSize.first},${fingerprint.windowSize.second}")

        if(System.getProperty("webdriver.gecko.driver") == null) {
          System.setProperty(
            "webdriver.gecko.driver",
            findExecutablePath("geckodriver")!!
          )
        }
        FirefoxDriver(options)
      }
    }

    driver.manage().window().size = Dimension(fingerprint.windowSize.first, fingerprint.windowSize.second)
  }

  /** Utils **/


  fun executeScript(js: String, vararg args: Any): Any {
    return (driver as JavascriptExecutor).executeScript(js, args)
  }

  /**
   * <b>Very important to use this every time a page load is expected</b> - preserves browser history
   * across sessions.
   */
  fun waitForPageLoad(timeoutSeconds: Duration = Duration.ofSeconds(5)) {
    WebDriverWait(driver, timeoutSeconds).until { webDriver ->
      if (webDriver is JavascriptExecutor)
        return@until (webDriver as JavascriptExecutor).executeScript("return document.readyState") == "complete"
      false
    }
    history.add(currentUrl)
  }

  /** Inherited methods from WebDriver **/

  override fun findElements(by: By?): MutableList<WebElement> {
    return driver.findElements(by)
  }

  override fun findElement(by: By?): WebElement {
    return driver.findElement(by)
  }

  override fun get(url: String?) {
    return driver.get(url)
  }

  override fun getCurrentUrl(): String {
    return driver.currentUrl
  }

  override fun getTitle(): String {
    return driver.title
  }

  override fun getPageSource(): String {
    return driver.pageSource
  }

  override fun close() {
    driver.close()

  }

  override fun quit() {
    driver.quit()
  }

  override fun getWindowHandles(): MutableSet<String> {
    return driver.windowHandles
  }

  override fun getWindowHandle(): String {
    return driver.windowHandle
  }

  override fun switchTo(): WebDriver.TargetLocator {
    return driver.switchTo()
  }

  override fun navigate(): WebDriver.Navigation {
    return driver.navigate()
  }

  override fun manage(): WebDriver.Options {
    return driver.manage()
  }
}

/**
 * Note: tied to linux, maybe mac.
 */
private fun findExecutablePath(name: String): String? {
  return try {
    val p = Runtime.getRuntime().exec("whereis $name")
    val br = BufferedReader(InputStreamReader(p.inputStream))
    val output = br.readLine()
    output.split(" ")[1]
  } catch(ex: Exception) {
    return null
  }
}
