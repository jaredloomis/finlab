package com.jaredloomis.silk.crawl

data class CrawlVisitor<Element, Out>(val pre: Boolean, val visit: (driver: CrawlDriver<Element>) -> Out)