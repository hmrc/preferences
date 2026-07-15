/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
