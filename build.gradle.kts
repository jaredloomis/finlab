plugins {
  kotlin("jvm") version "1.3.41"
  application
}

application {
  mainClassName = "com.jaredloomis.analmark.Main"
}

repositories {
  mavenCentral()
}

dependencies {
  implementation(kotlin("stdlib"))
  implementation("org.seleniumhq.selenium:selenium-java:3.141.59")
}
