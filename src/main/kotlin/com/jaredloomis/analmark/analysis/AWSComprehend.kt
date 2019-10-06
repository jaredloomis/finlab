package com.jaredloomis.analmark.analysis

import com.amazonaws.services.comprehend.AmazonComprehendClientBuilder
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.services.comprehend.AmazonComprehend
import com.amazonaws.services.comprehend.model.*


class AWSComprehend {
  private val awsCreds: DefaultAWSCredentialsProviderChain
  private val client: AmazonComprehend

  init {
    // TODO proper AWS keys configuration
    System.setProperty("aws.accessKeyId", "AKIA2OV7LVAAGYECNKTA")
    System.setProperty("aws.secretKey", "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")

    awsCreds = DefaultAWSCredentialsProviderChain.getInstance()
    client = AmazonComprehendClientBuilder.standard()
      .withCredentials(awsCreds)
      .withRegion("us-west-2")
      .build()
  }

  fun comprehendBrand(text: String): String? {
    return comprehend(text).stream()
      .filter {entity -> entity.type == "ORGANIZATION"}
      .findFirst()
      .map {entity -> entity.text}
      .orElse(null)
  }

  fun comprehendProduct(text: String): String? {
    return comprehend(text).stream()
      .filter {entity -> entity.type == "COMMERCIAL_ITEM"}
      .findFirst()
      .map {entity -> entity.text}
      .orElse(null)
  }

  fun comprehend(text: String): List<Entity> {
    val detectEntitiesRequest = DetectEntitiesRequest().withText(text)
      .withLanguageCode("en")
    val detectEntitiesResult = client.detectEntities(detectEntitiesRequest)
    return detectEntitiesResult.entities
  }
}