package com.jaredloomis.analmark.analysis

import com.amazonaws.services.comprehend.AmazonComprehendClientBuilder
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.services.comprehend.AmazonComprehend
import com.amazonaws.services.comprehend.model.*
import java.util.*

class AWSComprehend {
  private val awsCreds: DefaultAWSCredentialsProviderChain
  private val client: AmazonComprehend

  init {
    val props = Properties()
    props.load(this.javaClass.getResourceAsStream("credentials.properties"))
    System.setProperties(props)

    awsCreds = DefaultAWSCredentialsProviderChain.getInstance()
    client = AmazonComprehendClientBuilder.standard()
      .withCredentials(awsCreds)
      .withRegion("us-west-2")
      .build()
  }

  fun comprehendBrand(text: String): String? {
    return comprehend(text).stream()
      .filter {entity -> entity.type == EntityType.ORGANIZATION.name}
      .findFirst()
      .map {entity -> entity.text}
      .orElse(null)
  }

  fun comprehendProduct(text: String): String? {
    return comprehend(text).stream()
      .filter {entity -> entity.type == EntityType.COMMERCIAL_ITEM.name}
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