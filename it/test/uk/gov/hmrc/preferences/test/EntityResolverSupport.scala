/*
 * Copyright 2024 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.test

import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.result.InsertOneResult
import org.mongodb.scala.ToSingleObservablePublisher
import org.mongodb.scala.SingleObservableFuture

import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach, Suite }
import uk.gov.hmrc.mongo.test.MongoSupport

trait EntityResolverSupport extends BeforeAndAfterEach with BeforeAndAfterAll with MongoSupport {
  s: Suite =>

  override def afterAll(): Unit = {
    mongoClient
      .getDatabase("entity-resolver")
      .drop()
      .toFuture()
      .futureValue
    super.afterAll()
  }
  override def beforeEach(): Unit = {
    super.beforeEach()
    mongoClient
      .getDatabase("entity-resolver")
      .getCollection("entity")
      .drop()
      .toSingle()
      .toFuture()
      .futureValue
  }

  override def afterEach(): Unit = {
    mongoClient
      .getDatabase("entity-resolver")
      .getCollection("entity")
      .drop()
      .toSingle()
      .toFuture()
      .futureValue
    super.afterEach()
  }

  def withEntity(eid: String, nino: Option[String] = None, sautr: Option[String] = None): InsertOneResult = {
    val sb: StringBuilder = new StringBuilder
    // Create the entity
    sb ++= s"""{"_id" : "$eid""""
    if (nino.isDefined) sb ++= s""", "nino" : "${nino.get}" """
    if (sautr.isDefined) sb ++= s""", "sautr": "${sautr.get}" """
    sb ++= s"}"

    mongoClient
      .getDatabase("entity-resolver")
      .getCollection("entity")
      .insertOne(Document(sb.mkString))
      .toFuture()
      .futureValue
  }
}
