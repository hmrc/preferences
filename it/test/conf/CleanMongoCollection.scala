/*
 * Copyright 2024 HM Revenue & Customs
 *
 */

package conf

import org.mongodb.scala.{ MongoCollection, MongoDatabase }
import org.mongodb.scala.bson.collection.immutable.Document
import uk.gov.hmrc.mongo.MongoComponent

import javax.inject.{ Inject, Singleton }

@Singleton
class CleanMongoCollection @Inject() (mongoComponent: MongoComponent) {

  val db: MongoDatabase = mongoComponent.database

  protected lazy val saIndividualPreferences = db.getCollection[Document]("saIndividualPreferences")
  protected lazy val saPreferencesStats = db.getCollection[Document]("saPreferencesStats")
  protected lazy val abTestContexts = db.getCollection[Document]("abTestContexts")
  protected lazy val locks = db.getCollection[Document]("locks")
  protected lazy val events = db.getCollection[Document]("events")

  def collections(): Seq[MongoCollection[Document]] =
    Seq(saIndividualPreferences, saPreferencesStats, abTestContexts, locks, events)

}
