/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.repository

import com.mongodb.client.result.UpdateResult
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.{ BsonDateTime, BsonString, BsonValue }
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.{ Aggregates, CountOptions, Filters, UpdateOptions, Updates }
import org.mongodb.scala.SingleObservableFuture
import org.mongodb.scala.ToSingleObservablePublisher
import org.mongodb.scala.model.Aggregates.{ count, group }
import play.api.Logger
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.preferences.util.Dc
import uk.gov.hmrc.preferences.CurrentTime
import java.time.{ Instant, ZoneOffset }
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

class StatsCounter @Inject() (mongo: MongoComponent)(implicit ec: ExecutionContext) extends CurrentTime {
  import PreferencesStatsQuery._

  private val logger: Logger = Logger(getClass)

  val collection: MongoCollection[Document] = mongo.database.getCollection[Document]("saIndividualPreferences")

  val statsRepo: MongoCollection[Document] = mongo.database.getCollection[Document]("saPreferencesStats")

  private def countFor(spec: QuerySpec): Future[UpdateResult] = {
    val predicate = buildPredicate(spec.filters)
    logger.warn(s"CountingFor ${spec.name} with predicate: $predicate")
    spec.mode match {
      case CountMode.Total =>
        val started = System.nanoTime()
        logger.warn(s"CountingForTotalStarted at $started for ${spec.name} with predicate: $predicate")
        collection
          .countDocuments(predicate, CountOptions())
          .toFuture()
          .flatMap { n =>
            val duration = (System.nanoTime() - started) / 1_000_000.0
            logger.warn(s"CountingForTotalEnded ${spec.name} with predicate: $predicate and took $duration ms")
            recordStatsResult(spec.name, n.toInt)
              .andThen {
                case Success(n) => logger.warn(s"CountingForTotalSuccess for ${spec.name} -> $n")
                case Failure(e) => logger.error(s"CountingForTotalFailed for ${spec.name}", e)
              }
          }
      case CountMode.Distinct(field) =>
        val started = System.nanoTime()
        logger.warn(s"CountingForDistinctStarted at $started for ${spec.name} with predicate: $predicate")
        collection
          .aggregate[BsonValue](
            Seq(
              Aggregates.`match`(predicate),
              Aggregates.group(new BsonString(s"$$$field")),
              Aggregates.count("size")
            )
          )
          .headOption()
          .flatMap {
            case Some(doc) =>
              val duration = (System.nanoTime() - started) / 1_000_000.0
              logger.warn(s"CountingForDistinctEnded ${spec.name} with predicate: $predicate and took $duration ms")
              recordStatsResult(spec.name, doc.asDocument().getInt32("size").intValue())
            case None => recordStatsResult(spec.name, 0)
          }
          .andThen {
            case Success(n) => logger.warn(s"CountingForDistinctSuccess for ${spec.name} -> $n")
            case Failure(e) => logger.error(s"CountingForDistinctFailed for ${spec.name}", e)
          }

    }
  }

  private def buildPredicate(conditions: Seq[Bson]): Bson = conditions match {
    case Seq(condition) => condition
    case conditions     => Filters.and(conditions: _*)
  }

  private def recordStatsResult(name: String, value: Int): Future[UpdateResult] = {
    val setCount = Updates.set("value.count", value)
    val setDate = Updates.set("value.date", BsonDateTime(Instant.now.toEpochMilli))

    logger.warn(s"Checking preference query performance - Started write for $name")
    statsRepo
      .updateOne(
        filter = Filters.equal("_id", name),
        update = Updates.combine(setCount, setDate),
        options = UpdateOptions().upsert(true)
      )
      .toFuture()
      .andThen {
        case Success(_) =>
          logger.warn(s"Checking preference query performance - Finished write for $name")
        case Failure(e) =>
          logger.warn(s"Checking preference query performance - Failed write for $name ${e.getMessage}")
      }
  }

  def timedStatsQuery(): Future[Long] = {
    val start = System.currentTimeMillis()
    runStatsQuery().map { _ =>
      val end = System.currentTimeMillis()
      end - start
    }
  }

  def runStatsQuery()(implicit ec: ExecutionContext): Future[List[UpdateResult]] =
    sequencingFutures[QuerySpec, UpdateResult](queryDocuments)(q => countFor(q))

  private def queryDocuments: Seq[QuerySpec] = Seq(
    QuerySpec("generic.optedIn", Seq(genericTermsAndConditionsDefined, genericAccepted), CountMode.Total),
    QuerySpec(
      "generic.optedInAndVerified",
      Seq(genericTermsAndConditionsDefined, genericAccepted, hasVerifiedEmail),
      CountMode.Distinct("email.email")
    ),
    QuerySpec("generic.optedOut", Seq(genericTermsAndConditionsDefined, hasNotAcceptedGeneric), CountMode.Total),
    QuerySpec(
      "generic.optedInAndVerifiedAndWelsh",
      Seq(genericTermsAndConditionsDefined, genericAccepted, isContactable, hasSelectedWelsh),
      CountMode.Total
    ),
    QuerySpec("generic.optedOutWithVerifiedEmail", Seq(hasNotAcceptedGeneric, isContactable), CountMode.Total),
    QuerySpec(
      "generic.optedOutWithPendingEmail",
      Seq(hasNotAcceptedGeneric, doesNotHaveVerifiedEmail, hasPendingEmail),
      CountMode.Total
    ),
    QuerySpec("generic.reOptedIn", Seq(genericAccepted, reOptInTermsAndConditions), CountMode.Total),
    QuerySpec(
      "generic.customerReOptedOut",
      Seq(hasNotAcceptedGeneric, customerReOptOutTermsAndConditions),
      CountMode.Total
    ),
    QuerySpec("verifiedButBounced", Seq(doesNotHavePendingEmail, hasBouncedEmail), CountMode.Total)
  )

  private def sequencingFutures[T, U](xs: Seq[T])(f: T => Future[U])(implicit ec: ExecutionContext): Future[List[U]] = {
    val resBase = Future.successful(ListBuffer.empty[U])
    xs.foldLeft(resBase) { (futureRes, x) =>
      futureRes.flatMap { res =>
        f(x).map(res += _)
      }
    }.map(_.toList)
  }
}

object PreferencesStatsQuery {
  import AggregateHelper._

//  val dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
  val formatter: DateTimeFormatter =
//    DateTimeFormatter.ofPattern(dateFormat).withZone(ZoneOffset.UTC)
    DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneOffset.UTC)

  val hasVerifiedEmail: Bson = fieldExists("email")
  val doesNotHaveVerifiedEmail: Bson = fieldDoesNotExist("email")

  val hasPendingEmail: Bson = fieldExists("pendingEmail")
  val doesNotHavePendingEmail: Bson = fieldDoesNotExist("pendingEmail")

  private val hasPendingEmailLastBounce = fieldExists("pendingEmail.lastBounce")
  val hasBouncedPendingEmail: Bson = conjoin(hasPendingEmail, hasPendingEmailLastBounce)
  val doesNotHaveBouncedPendingEmail: Bson = fieldDoesNotExist("pendingEmail.lastBounce")

  private val hasEmailLastBounce = fieldExists("email.lastBounce")
  val hasBouncedEmail: Bson = conjoin(hasVerifiedEmail, hasEmailLastBounce)
  val doesNotHaveBouncedEmail: Bson = fieldDoesNotExist("email.lastBounce")

  val genericTermsAndConditionsDefined: Bson = fieldNotNull("termsAndConditions.generic")
  val genericAccepted: Bson = fieldIsTrue("termsAndConditions.generic.accepted")
  val hasNotAcceptedGeneric: Bson = fieldIsFalse("termsAndConditions.generic.accepted")

  val reOptInTermsAndConditions: Bson = fieldIsValue("termsAndConditions.generic.eventType", "re-opt-in")
  val customerReOptOutTermsAndConditions: Bson =
    fieldIsValue("termsAndConditions.generic.eventType", "customer-re-opt-out")

  val affinityGroupIndividualCondition: Bson = fieldIsValue("userType.affinityGroup", "Individual")
  val affinityGroupOrganisationCondition: Bson = fieldIsValue("userType.affinityGroup", "Organisation")

  val confidenceLevel50: Bson = fieldIsValueNumber("userType.confidenceLevel", 50)
  val confidenceLevel200: Bson = fieldIsValueNumber("userType.confidenceLevel", 200)
  val confidenceLevel500: Bson = fieldIsValueNumber("userType.confidenceLevel", 500)

  val hasSelectedWelsh: Bson = fieldIsValue("email.language", "cy")

  val isContactable: Bson = conjoin(hasVerifiedEmail, doesNotHavePendingEmail, doesNotHaveBouncedEmail)

  val hasEvents: Bson = fieldExists("events")

  val hasReOptInEvent: Bson = hasEventType("re-opt-in")

  val hasSystemOptOutEvent: Bson = hasEventType("system-opt-out")

  val hasCustomerReOptOutEvent: Bson = hasEventType("customer-re-opt-out")

  val now: Instant = Dc.instantNow()
  val delinquentForMoreThan2DaysUpTo7DaysIncl: Bson = conjoin(
    doesNotHaveVerifiedEmail,
    hasPendingEmail,
    fieldNotNull("pendingEmail.verificationLink.linkSentTime"),
    dateIsBetween(
      "pendingEmail.verificationLink.linkSentTime",
      now.minus(7, ChronoUnit.DAYS).toEpochMilli,
      now.minus(2, ChronoUnit.DAYS).toEpochMilli
    )
  )
  val delinquentForMoreThan28Days: Bson = conjoin(
    doesNotHaveVerifiedEmail,
    hasPendingEmail,
    fieldNotNull("pendingEmail.verificationLink.linkSentTime"),
    dateIsBefore("pendingEmail.verificationLink.linkSentTime", now.minus(28, ChronoUnit.DAYS).toEpochMilli)
  )
  val delinquentForMoreThan7DaysUpTo28DaysIncl: Bson = conjoin(
    doesNotHaveVerifiedEmail,
    hasPendingEmail,
    fieldNotNull("pendingEmail.verificationLink.linkSentTime"),
    dateIsBetween(
      "pendingEmail.verificationLink.linkSentTime",
      now.minus(28, ChronoUnit.DAYS).toEpochMilli,
      now.minus(7, ChronoUnit.DAYS).toEpochMilli
    )
  )
}

object AggregateHelper {

  def fieldExists(fieldName: String): Bson =
    Filters.exists(fieldName, exists = true)
//    Document(
//      fieldName -> Document("$exists" -> true)
//    )

  def fieldNotNull(fieldName: String): Bson =
    Filters.ne(fieldName, "null")
//    Document(
//      fieldName -> Document("$ne" -> "null")
//    )

  def fieldIsTrue(fieldName: String): Bson =
    Filters.eq(fieldName, true)
//    Document(
//      fieldName -> Document("$eq" -> true)
//    )

  def fieldIsValue(fieldName: String, value: String): Bson =
    Filters.eq(fieldName, value)
//    Document(
//      fieldName -> Document("$eq" -> value)
//    )

  def fieldIsValueNumber(fieldName: String, value: Int): Bson =
    Filters.eq(fieldName, value)
//    Document(
//      fieldName -> Document("$eq" -> value)
//    )

  def fieldIsFalse(fieldName: String): Bson =
    Filters.eq(fieldName, false)
//    Document(
//      fieldName -> Document("$eq" -> false)
//    )

  def fieldDoesNotExist(fieldName: String): Bson =
    Filters.exists(fieldName, exists = false)
//    Document(
//      fieldName -> Document("$exists" -> false)
//    )

  def conjoin(predicates: Bson*): Bson =
    Filters.and(predicates: _*)
//    Document(
//      "$and" -> BsonArray(predicates)
//    )

  def dateIsBetween(fieldName: String, from: Long, to: Long): Bson =
    Filters.and(
      Filters.gte(fieldName, Instant.ofEpochMilli(from)),
      Filters.lte(fieldName, Instant.ofEpochMilli(to))
    )

  def dateIsBefore(fieldName: String, after: Long): Bson =
    Filters.lte(fieldName, Instant.ofEpochMilli(after)) // .toBsonDocument

  def hasEventType(eventType: String): Bson =
    Filters.elemMatch("events", Filters.eq("eventType", eventType))
//    Document("events" -> Document("$elemMatch" -> Document("eventType" -> s"$eventType")))

}
