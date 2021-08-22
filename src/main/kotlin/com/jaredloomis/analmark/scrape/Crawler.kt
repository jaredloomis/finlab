package com.jaredloomis.analmark.scrape

import org.openqa.selenium.WebDriver

class Crawler(val startUrl: String, val linkPatterns: List<String>, val visit: (driver: WebDriver) -> Any) {

}