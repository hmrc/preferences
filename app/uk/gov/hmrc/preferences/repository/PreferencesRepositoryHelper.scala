/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.repository

import org.bson.types.ObjectId
import org.mongodb
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.model.Updates
import org.mongodb.scala.ToSingleObservablePublisher
import play.api.libs.json.Format
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.UpdateOptions
import org.mongodb.scala.SingleObservableFuture
import play.api.libs.json.OFormat
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.preferences.CurrentTime
import uk.gov.hmrc.preferences.controllers.model.Credentials
import uk.gov.hmrc.preferences.model.Preferences.formats
import uk.gov.hmrc.preferences.model.TermsAndConditions.Accepted
import uk.gov.hmrc.preferences.model.{ EntityId, Preferences }
import uk.gov.hmrc.mongo.workitem.ProcessingStatus
import java.time.Instant
import scala.concurrent.{ ExecutionContext, Future }

trait PreferencesRepositoryHelper extends PreferenceQueryBuilder with PreferencesUpdateBuilder with CurrentTime {

  protected[repository] val repo: PlayMongoRepository[Preferences]

  implicit val oFormatPreferences: OFormat[Preferences] = JsonUtil.oFormat(formats)
  implicit val instantFormats: Format[Instant] = MongoJavatimeFormats.instantFormat

  def createOrUpdate(entityId: EntityId, jsonUpdate: Bson)(implicit
    ec: ExecutionContext
  ): Future[PreferenceUpdateResult] =
    repo.collection
      .updateOne(
        filter = findByEntityIdQuery(entityId),
        update = jsonUpdate,
        UpdateOptions().upsert(true)
      )
      .toSingle()
      .toFuture()
      .map {
        case x if x.getUpsertedId != null => NewPreferenceCreated
        case x if x.getModifiedCount > 0  => PreferenceUpdated
        case x if x.getMatchedCount > 0   => PreferenceMatched
        case _                            => PreferenceNotMatched
      }

  def updateTermsAndConditions(
    preferences: Preferences,
    credentials: Option[Credentials]
  )(implicit ec: ExecutionContext): Future[PreferenceUpdateResult] = {
    val termsAndConditions = preferences.termsAndConditions
    val entityId = preferences.entityId
    val stillOptedInForAtLeastOneTermsAndConditions = termsAndConditions.generic
      .isInstanceOf[Accepted]

    withCurrentTime { implicit time =>
      val unsetEmail =
        if (stillOptedInForAtLeastOneTermsAndConditions) mongodb.scala.Document()
        else unsetEmailQuery

      val setEvents = setEventsUpdate(preferences.events)

      val setSurveys = setSurveyUpdate(preferences.surveys)

      val pendingEmailUpdate: Bson = if (stillOptedInForAtLeastOneTermsAndConditions) {
        preferences.pendingEmail match {
          case Some(pe) => getPendingEmailUpdate(pe, time)
          case _        => BsonDocument()
        }
      } else BsonDocument()

      val baseQry =
        Updates.combine(
          pendingEmailUpdate,
          defaultUpdate(entityId, termsAndConditions, time, credentials),
          unsetEmail,
          setEvents,
          setSurveys
        )

      createOrUpdate(
        entityId,
        baseQry
      )
    }
  }

  def setReminderStatus(preferencesId: ObjectId, status: ProcessingStatus, reminderField: String)(implicit
    ec: ExecutionContext
  ): Future[Boolean] =
    repo.collection
      .updateOne(
        filter = findByIdQuery(preferencesId),
        update = setReminderStatusUpdate(status, reminderField)
      )
      .toFuture()
      .map(s => s.wasAcknowledged())

}
