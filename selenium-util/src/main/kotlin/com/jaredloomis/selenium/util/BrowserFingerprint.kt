package com.jaredloomis.selenium.util

import org.openqa.selenium.Capabilities
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxOptions
import kotlin.random.Random

data class BrowserFingerprint(
  val browserType: BrowserType, val arguments: List<String>, val userAgent: String,
  val windowSize: Pair<Int, Int>, val headless: Boolean
) {
  companion object {
    fun default(): BrowserFingerprint {
      val width = Random.nextInt(500, 1600)
      val height = Random.nextInt(300, 800)
      return BrowserFingerprint(
        BrowserType.CHROME,
        listOf(
          "--load-extension=assets/uBlock0.chromium", "disable-infobars",
          "--disable-blink-features=AutomationControlled"
        ),
        "Mozilla/5.0 (X11; CrOS x86_64 14268.67.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.111 Safari/537.36",
        Pair(width, height),
        false
      )
    }
  }

  fun type(type: BrowserType): BrowserFingerprint {
    return BrowserFingerprint(type, arguments, userAgent, windowSize, headless)
  }

  fun arguments(args: List<String>): BrowserFingerprint {
    return BrowserFingerprint(browserType, args, userAgent, windowSize, headless)
  }

  fun plusArguments(vararg args: String): BrowserFingerprint {
    return arguments(arguments.plus(args))
  }

  fun windowSize(userAgent: String): BrowserFingerprint {
    return BrowserFingerprint(browserType, arguments, userAgent, windowSize, headless)
  }

  fun windowSize(windowSize: Pair<Int, Int>): BrowserFingerprint {
    return BrowserFingerprint(browserType, arguments, userAgent, windowSize, headless)
  }

  fun headless(headless: Boolean): BrowserFingerprint {
    return BrowserFingerprint(browserType, arguments, userAgent, windowSize, headless)
  }

  fun createWebDriver(): WebDriver {
    return when(browserType) {
      BrowserType.CHROME -> ChromeDriver(ChromeOptions()
        .addArguments(arguments)
        .setHeadless(headless)
        .addArguments("user-agent=${userAgent}")
        .addArguments("window-size=${windowSize.first},${windowSize.second}")
        .setExperimentalOption("excludeSwitches", arrayOf("enable-automation")))
      BrowserType.FIREFOX -> FirefoxDriver(FirefoxOptions()
        .addArguments(arguments)
        .setHeadless(headless)
        .addArguments("user-agent=${userAgent}")
        .addArguments("window-size=${windowSize.first},${windowSize.second}"))
    }
  }
}

enum class BrowserType {
  CHROME, FIREFOX
}