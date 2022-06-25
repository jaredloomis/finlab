package com.jaredloomis.silk.crawl

import com.jaredloomis.silk.util.getLogger
import org.openqa.selenium.*
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxOptions
import org.openqa.selenium.support.ui.WebDriverWait
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.logging.Logger

abstract class CrawlDriver<E> {
  abstract fun open()
  abstract fun quit()
  abstract fun click(sel: Sel)
  abstract fun sendText(sel: Sel, text: CharSequence)
  abstract fun getCurrentUrl(): String
  abstract fun getText(sel: Sel): String
  abstract fun navigateTo(url: String)
  abstract fun back()
  abstract fun executeJavascript(js: String, vararg args: Any): Any
  abstract fun exists(sel: Sel): Boolean
  abstract fun goTo(url: String)

  class Selenium(val options: Options) : CrawlDriver<WebElement>() {
    lateinit var driver: WebDriver
    var isShutdown = false
    private val log: Logger = getLogger(this::class)
    val visitedURLs: MutableSet<String> = HashSet()

    override fun open() {
      if(!::driver.isInitialized || isShutdown) {
        driver = when(options.browser) {
          BackendBrowser.CHROME -> {
            val options = ChromeOptions()
              .setHeadless(options.headless)
              .addArguments("user-agent=${options.fingerprint.userAgent}")
              .addArguments("--load-extension=assets/uBlock0.chromium")
              .addArguments("disable-infobars")
              .addArguments("--disable-blink-features=AutomationControlled")
              .addArguments("window-size=${options.fingerprint.windowSize.first},${options.fingerprint.windowSize.second}")
              .setExperimentalOption("excludeSwitches", arrayOf("enable-automation"))

            if(System.getProperty("webdriver.chrome.driver") == null) {
              System.setProperty(
                "webdriver.chrome.driver",
                findExecutablePath("chromedriver")!!
              )
            }
            ChromeDriver(options)
          }
          BackendBrowser.FIREFOX -> {
            val options = FirefoxOptions()
              .setHeadless(options.headless)
              .addArguments("user-agent=${options.fingerprint.userAgent}")
              .addArguments("window-size=${options.fingerprint.windowSize.first},${options.fingerprint.windowSize.second}")
              .addArguments("disable-infobars")

            if(System.getProperty("webdriver.gecko.driver") == null) {
              System.setProperty(
                "webdriver.gecko.driver",
                findExecutablePath("geckodriver")!!
              )
            }
            FirefoxDriver(options)
          }
        }
        isShutdown = false

        driver.manage().window().size = Dimension(options.fingerprint.windowSize.first, options.fingerprint.windowSize.second)
      }
    }

    override fun quit() {
      driver.close()
      isShutdown = true
    }

    override fun click(sel: Sel) {
      val elem = findElement(sel) ?: throw Exception("Couldn't find element to click: $sel.")
      elem.click()
      waitForPageLoad()
    }

    override fun sendText(sel: Sel, text: CharSequence) {
      val textStripped = if(text.endsWith("\n")) text.subSequence(0, text.length-1) else ""
      val elem = findElement(sel) ?: throw Exception("Couldn't find element to send text to: $sel.")
      tryOrThrow("send text '$textStripped' to $sel") {
        elem.click()
        elem.clear()
        when(sel) {
          is Sel.CSS -> {
            try{
              executeJavascript("document.querySelector('${sel.css}').value = \"$text\";", elem)
            } catch(ex: Exception){
              ex.printStackTrace()
            }
          }
          else -> {}
        }
        elem.sendKeys(textStripped)
        if(text.endsWith("\n")) {
          elem.sendKeys(Keys.ENTER)
        }
      }
    }

    override fun getCurrentUrl(): String {
      return driver.currentUrl
    }

    override fun getText(sel: Sel): String {
      return findElement(sel)!!.text
    }

    override fun navigateTo(url: String) {
      driver.navigate().to(url)
      waitForPageLoad(500)
    }

    override fun back() {
      driver.navigate().back()
      waitForPageLoad()
    }

    override fun executeJavascript(js: String, vararg args: Any): Any {
      return (driver as JavascriptExecutor).executeScript(js, args)
    }

    override fun exists(sel: Sel): Boolean {
      return try {
        driver.findElement(sel.toBy())
        true
      } catch(ex: Exception) {
        false
      }
    }

    override fun goTo(url: String) {
      driver.navigate().to(url)
    }

    /*** WAITS ***/

    fun waitForPageLoad(timeoutSeconds: Long = 5) {
      visitedURLs.add(driver.currentUrl)
      WebDriverWait(driver, timeoutSeconds).until { webDriver ->
        if (webDriver is JavascriptExecutor)
          return@until (webDriver as JavascriptExecutor).executeScript("return document.readyState") == "complete"
        false
      }
    }

    /*** UTILITIES ***/

    fun findElement(sel: Sel): WebElement? {
      return tryOrNull("find element: $sel") {
        driver.findElement(sel.toBy())
      }
    }

    fun findElements(sel: Sel): List<WebElement> {
      return tryOrNull("find elements: $sel") {
        driver.findElements(sel.toBy()).toList()
      } ?: emptyList()
    }

    fun waitUntilClickable(sel: Sel, timeoutSeconds: Long = 8): WebElement? {
      val by = sel.toBy()
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

    private fun <A> tryOrThrow(desc: String, action: () -> A): A {
      for(i in 1..options.maxRetries) {
        try {
          return action()
        } catch(ex: Exception) {
          Thread.sleep(options.initialRetryDelayMs * i)
        }
      }
      throw Exception("Failed to $desc.")
    }

    private fun <A> tryOrNull(desc: String, action: () -> A): A? {
      for(i in 1..options.maxRetries) {
        try {
          return action()
        } catch(ex: Exception) {
          Thread.sleep(options.initialRetryDelayMs * i)
        }
      }
      return null
    }

    data class Options(
      val maxRetries: Byte, val initialRetryDelayMs: Long,
      val browser: BackendBrowser, val headless: Boolean, val fingerprint: BrowserFingerprint
    ) {
      companion object {
        fun default(): Options {
          return Options(5, 500, BackendBrowser.CHROME, false,
            BrowserFingerprint("Mozilla/5.0 (X11; CrOS x86_64 14268.67.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.111 Safari/537.36", Pair(1024, 768))
          )
        }
      }
    }

    enum class BackendBrowser { CHROME, FIREFOX }
  }
}

sealed class Sel {
  data class CSS(val css: String): Sel()
  data class XPath(val xpath: String): Sel()

  fun toBy(): By {
    return when(this) {
      is CSS -> By.cssSelector(css)
      is XPath -> By.xpath(xpath)
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
