package com.jaredloomis.analmark.main

import java.util.stream.Collectors
import edu.stanford.nlp.pipeline.CoreDocument
import edu.stanford.nlp.pipeline.StanfordCoreNLP
import java.util.Properties

interface IProductRecognition {
  fun recognizeBrands(title: String, description: String): List<String>
}

class NERProductRecognition: IProductRecognition {
  val pipeline: StanfordCoreNLP

  init {
    val props = Properties()
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner")
    props.setProperty("ner.fine.regexner.ignorecase", "true");
    // disable fine grained ner
    //props.setProperty("ner.applyFineGrained", "false");
    // customize fine grained ner
    //props.setProperty("ner.fine.regexner.mapping", "example.rules");
    // add additional rules
    //props.setProperty("ner.additional.regexner.mapping", "example.rules");
    //props.setProperty("ner.additional.regexner.ignorecase", "true");
    // add 2 additional rules files ; set the first one to be case-insensitive
    //props.setProperty("ner.additional.regexner.mapping", "ignorecase=true,example_one.rules;example_two.rules");
    pipeline = StanfordCoreNLP(props)
  }

  override fun recognizeBrands(title: String, description: String): List<String> {
    // make an example document
    val doc = CoreDocument(title + "\n" + description)
    // annotate the document
    pipeline.annotate(doc)
    // view results
    println("---")
    println("entities found")
    for(em in doc.entityMentions())
      println("\tdetected entity: \t" + em.text() + "\t" + em.entityType())
    println("---")
    println("tokens and ner tags")
    return doc.tokens().stream()
      //.filter {token -> token.ner()}
      .map {token -> "(" + token.word() + "," + token.ner() + ")"}
      .collect(Collectors.toList())
  }
}