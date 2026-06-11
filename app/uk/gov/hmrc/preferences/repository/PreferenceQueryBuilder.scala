/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.repository

import org.bson.conversions.Bson
import org.bson.types.ObjectId
import uk.gov.hmrc.mongo.play.json.Codecs

import java.time.{ Instant, LocalDate, ZoneId }
import org.mongodb.scala.model.Filters.{ exists, _ }
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.{ Failed, InProgress, ToDo }
import uk.gov.hmrc.preferences.model.{ EntityId, PendingEmailAddress }
import uk.gov.hmrc.preferences.controllers.model.EmailToken

trait PreferenceQueryBuilder extends QueryBase {

  def findByIdQuery(id: ObjectId): Bson =
    equal("_id", id)

  def unverifiedUsersQuery(date: Instant): Bson =
    and(
      exists("email", exists = false),
      lt("pendingEmail.verificationLink.linkSentTime", date)
    )

  def unverifiedWithTwoEmailsQuery(date: Instant): Bson =
    and(
      exists("email", exists = true),
      lt("pendingEmail.verificationLink.linkSentTime", date)
    )

  def findByEmailQuery(emailAddress: String): Bson =
    or(
      equal("email.lowercaseEmail", emailAddress.toLowerCase),
      equal("pendingEmail.lowercaseEmail", emailAddress.toLowerCase),
      equal("email.email", emailAddress),
      equal("pendingEmail.email", emailAddress)
    )

  def findByVerificationTokenQuery(token: EmailToken): Bson =
    or(
      equal("pendingEmail.verificationLink._id", token.token),
      equal("email.verifiedWithLink._id", token.token)
    )

  def findByEntityIdQuery(entityId: EntityId): Bson =
    equal("entityId", Codecs.toBson(entityId))

  def findByEntityIdWithVerifiedEmailQuery(entityId: EntityId): Bson =
    and(
      equal("entityId", Codecs.toBson(entityId)),
      exists("email", exists = true)
    )

  def findByEntityIdWithPendingEmailQuery(entityId: EntityId): Bson =
    and(
      equal("entityId", Codecs.toBson(entityId)),
      exists("pendingEmail", exists = true)
    )

  def hasOptInMigrationQuery(eventsExist: Boolean): Bson =
    and(
      exists("events", exists = eventsExist),
      exists(s"termsAndConditions.generic", exists = true)
    )

  def findByIdAndPendingEmail(id: ObjectId, pendingEmail: PendingEmailAddress): Bson =
    and(
      equal("_id", id),
      equal("pendingEmail.email", pendingEmail.email)
    )

  def hasBounceCountQuery(id: ObjectId): Bson =
    and(
      equal("_id", id),
      exists("email.bounceCount", exists = true)
    )

  def hasPreferencesUpdatedOnQuery(oDate: Option[LocalDate]): Bson =
    oDate match {
      case Some(date) =>
        and(
          gte("updatedAt", Instant.from(date.atStartOfDay(ZoneId.of("UTC")))),
          lt("updatedAt", Instant.from(date.plusDays(1).atStartOfDay(ZoneId.of("UTC"))))
        )

      case _ => empty()
    }

  def findDueRemindersOrIncompleteBeforeQuery(
    unverifiedEmailsBefore: => Instant,
    retryIncompleteBefore: => Instant,
    reminderField: String
  ): Bson =
    or(
      and(
        lte("pendingEmail.verificationLink.linkSentTime", unverifiedEmailsBefore),
        equal(s"$reminderField.status", ToDo.name)
      ),
      and(
        in(s"$reminderField.status", InProgress.name, Failed.name),
        lte(s"$reminderField.updatedAt", retryIncompleteBefore)
      )
    )

}
