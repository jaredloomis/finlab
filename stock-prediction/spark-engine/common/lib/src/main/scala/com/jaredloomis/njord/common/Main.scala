package com.jaredloomis.njord.common

import org.apache.spark.sql.SparkSession

object Main {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder().master("local").appName("MongoSparkConnectorIntro").config("spark.mongodb.read.connection.uri", "mongodb://127.0.0.1/stock_analysis.financials_reported").config("spark.mongodb.write.connection.uri", "mongodb://127.0.0.1/stock_analysis.financials_reported").getOrCreate()
    val df = spark.read.format("mongo").option("uri", "mongodb://127.0.0.1").option("database", "stock_analysis").option("collection", "financials_reported").load()
    df.head(10).foreach { row =>
      val report = row.getValuesMap[Map[String, Array[Map[String, Any]]]](Seq("report"))
      println(report.head._2.head)
    }
  }
}
