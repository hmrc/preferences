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

///*
// * Copyright 2026 HM Revenue & Customs
// *
// */
//
//import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, InputStream, ObjectOutputStream }
//
//import org.joda.time.DateTime
//import org.scalatest.DoNotDiscover
//import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
//import org.scalatest.time.{ Hour, Span }
//import org.scalatestplus.play.PlaySpec
//import play.api.libs.json.{ JsObject, JsValue, Json, Writes }
//import reactivemongo.bson.BSONObjectID
//import reactivemongo.play.json.collection.Helpers._
//import reactivemongo.play.json.collection.JSONCollection
//import uk.gov.hmrc.mongo.MongoConnector
//import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
//import uk.gov.hmrc.preferences.model.EntityId
//import utils.GenerateRandom
//
//import scala.util.Random
//
//@DoNotDiscover
//class BuildProductionSizedPrefs extends PlaySpec with ScalaFutures with IntegrationPatience {
//  val rand = new Random()
//
//  import scala.concurrent.ExecutionContext.Implicits.global
//
//  override implicit val patienceConfig: PatienceConfig = new PatienceConfig(Span(1, Hour))
//
//  val preferencesMongo = new MongoConnector(s"mongodb://127.0.0.1:27017/preferences").db
//  val preferencesCollection = preferencesMongo().collection[JSONCollection]("saIndividualPreferences")
//
//  val entityResolverMongo = new MongoConnector(s"mongodb://127.0.0.1:27017/entity-resolver").db
//  val entityResolverCollection = entityResolverMongo().collection[JSONCollection]("entity")
//
//  val batchSize = 5000 * 2
//
//  object Dates {
//    val fromNYearsAgo: Int = 3
//    val now = DateTime.now()
//    val numberOfDays = 365 * fromNYearsAgo
//    val randomInterval = new Random().nextInt(numberOfDays)
//    val createdAtTime = now.minusDays(randomInterval)
//    val linkSentTime = createdAtTime.plusMinutes(1)
//    val updatedAtTime = createdAtTime.plusDays(1)
//    val verifiedOn = linkSentTime.plusDays(1)
//  }
//
//  sealed trait PayloadType
//  case object OptedInWithPendingAndVerifiedPayload extends PayloadType
//  case object OptedInPendingPayload extends PayloadType
//  case object OptedInVerifiedPayload extends PayloadType
//  case object OptedOutPayload extends PayloadType
//
//  val verifiedLinkId = BSONObjectID.generate.stringify
//
//  def payloadFor(payloadType: PayloadType, entityId: EntityId): JsValue = {
//    import Dates._
//    implicit val dateWrites: Writes[DateTime] = ReactiveMongoFormats.dateTimeWrite
//    implicit val objectIdFormats = ReactiveMongoFormats.objectIdFormats
//
//    val email = GenerateRandom.email()
//
//    val pendingEmail = Json.obj(
//      "verificationLink" -> Json.obj(
//        "_id"          -> "verificationLink",
//        "linkSentTime" -> createdAtTime
//      ),
//      "email"          -> email,
//      "lowercaseEmail" -> email.toLowerCase,
//      "lastBounce" -> Json.obj(
//        "errorCode" -> 510,
//        "timestamp" -> createdAtTime
//      ),
//      "reminder" -> Json.obj(
//        "status"    -> "succeeded",
//        "updatedAt" -> createdAtTime
//      )
//    )
//
//    val jsonEmail = Json.obj(
//      "email"          -> email,
//      "lowercaseEmail" -> email.toLowerCase,
//      "verifiedOn"     -> verifiedOn,
//      "verifiedWithLink" -> Json.obj(
//        "linkSentTime" -> linkSentTime,
//        "_id"          -> verifiedLinkId
//      )
//    )
//
//    val minimal = Json.obj(
//      "_id"       -> BSONObjectID.generate,
//      "createdAt" -> createdAtTime,
//      "updatedAt" -> updatedAtTime,
//      "entityId"  -> entityId,
//      "ups" -> Json.obj(
//        "createdAt"    -> createdAtTime,
//        "updatedAt"    -> updatedAtTime,
//        "status"       -> "todo",
//        "failureCount" -> 0
//      ),
//      "noc" -> Json.obj(
//        "createdAt"    -> createdAtTime,
//        "updatedAt"    -> updatedAtTime,
//        "status"       -> "todo",
//        "failureCount" -> 0
//      )
//    )
//
//    payloadType match {
//      case OptedInWithPendingAndVerifiedPayload =>
//        Json.obj(
//          "email"        -> jsonEmail,
//          "pendingEmail" -> pendingEmail,
//          "termsAndConditions" -> Json.obj(
//            "generic" -> Json.obj(
//              "accepted"  -> true,
//              "updatedAt" -> updatedAtTime
//            )
//          )
//        ) ++ minimal
//
//      case OptedInPendingPayload =>
//        Json.obj(
//          "pendingEmail" -> pendingEmail,
//          "termsAndConditions" -> Json.obj(
//            "generic" -> Json.obj(
//              "accepted"  -> true,
//              "updatedAt" -> updatedAtTime
//            )
//          )
//        ) ++ minimal
//
//      case OptedInVerifiedPayload =>
//        Json.obj(
//          "email" -> jsonEmail,
//          "termsAndConditions" -> Json.obj(
//            "generic" -> Json.obj(
//              "accepted"  -> true,
//              "updatedAt" -> updatedAtTime
//            )
//          )
//        ) ++ minimal
//
//      case OptedOutPayload =>
//        Json.obj(
//          "termsAndConditions" -> Json.obj(
//            "generic" -> Json.obj(
//              "accepted"  -> false,
//              "updatedAt" -> updatedAtTime
//            )
//          )
//        ) ++ minimal
//    }
//  }
//
//  // This is a double size production database - i.e. twice as many records as of Sept 2015
//  def doIt(): Unit = {
//    generateAndPersistPayload(OptedInWithPendingAndVerifiedPayload, 1000)
//    generateAndPersistPayload(OptedInPendingPayload, 50000)
//    generateAndPersistPayload(OptedOutPayload, 2600000)
//    generateAndPersistPayload(OptedInVerifiedPayload, 2000000)
//  }
//
//  def generateEntityPayLoad(entityId: EntityId): JsValue =
//    Json.obj("_id" -> entityId.value, "sautr" -> GenerateRandom.utr().value, "nino" -> GenerateRandom.nino().value)
//
//  def generateAndPersistPayload(payloadType: PayloadType, size: Int): Unit = {
//    val entityIds = (0 until size).toStream.map(n => GenerateRandom.entityId()).grouped(batchSize)
//    while (entityIds.hasNext) {
//      val entityStream = entityIds.next
//      val preferences = entityStream.map { entityId: EntityId =>
//        payloadFor(payloadType, entityId).as[JsObject]
//      }
//      bulkInsert(preferencesCollection, streamConvert(preferences), ordered = false).futureValue
//
//      val entities = entityStream.map { entityId: EntityId =>
//        generateEntityPayLoad(entityId).as[JsObject]
//      }
//      bulkInsert(entityResolverCollection, streamConvert(entities), ordered = false).futureValue
//    }
//    println(s"""Persisted $size records for ${payloadType.getClass.getSimpleName}""")
//  }
//
//  // Note that the bulkInsert does take tens of minutes depending on spec of your machine
//  "load data" should {
//    "bulkInsert into the DB" in {
//      doIt()
//    }
//  }
//
//  private def streamConvert(entityStream: Stream[JsObject]): InputStream = {
//    val baos = new ByteArrayOutputStream()
//    val oos = new ObjectOutputStream(baos)
//    entityStream.foreach { entity =>
//      oos.writeObject(entity)
//    }
//    new ByteArrayInputStream(baos.toByteArray)
//  }
//
//}
