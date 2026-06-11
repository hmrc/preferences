/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.service

import org.bson.types.ObjectId
import play.api.Logger
import play.api.libs.json.Json
import uk.gov.hmrc.mongo.lock.{ LockService, MongoLockRepository }
import uk.gov.hmrc.preferences.jobs.RunModeBridge
import uk.gov.hmrc.preferences.model.OptEventType.{ CustomerOptOut, OptIn }
import uk.gov.hmrc.preferences.model.PageType.IPage
import uk.gov.hmrc.preferences.model.TermsAndConditions.{ Accepted, Refused }
import uk.gov.hmrc.preferences.model.{ CustomerOptOutEvent, EmailEvent, EmailEventType, Event, Language, OptEventType, OptInEvent, OptInPage, OptPageEvent, Preferences, Version }
import uk.gov.hmrc.preferences.repository.PreferencesRepository
import uk.gov.hmrc.preferences.service.Actions.{ EMAIL_BOUNCED, EMAIL_CHANGED, EMAIL_VERIFIED }

import java.time.Instant
import javax.inject.{ Inject, Singleton }
import scala.concurrent.duration.{ Duration, FiniteDuration, HOURS }
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class OptInRecordUpdateService @Inject() (
  lockRepository: MongoLockRepository,
  preferencesRepository: PreferencesRepository,
  runModeBridge: RunModeBridge
) extends OptInRecordUpdate(preferencesRepository) {

  val name: String = "optInRecordUpdateJob"

  private val maxLockHours = 10L

  lazy val lockDuration: Option[FiniteDuration] = runModeBridge.getOptionalMillisForScheduling(name, "lockDuration")
  lazy val batchSize: Int = runModeBridge.getBatchSize(name, "batchSize")

  val releaseLockAfter: Duration = lockDuration.getOrElse(Duration(maxLockHours, HOURS))
  val ls = LockService(lockRepository = lockRepository, lockId = name, ttl = releaseLockAfter)

}

class OptInRecordUpdate(
  preferencesRepository: PreferencesRepository
) extends ServiceBase {

  private val logger: Logger = Logger(getClass)

  // The date of the previous opt-in text version change
  private val versionSwitchDate = Instant.parse("2020-01-16T14:00:00Z")

  def migrate(list: List[ObjectId])(implicit ec: ExecutionContext): Future[List[ObjectId]] =
    Future.sequence(
      list
        .map(id =>
          preferencesRepository
            .findPreferencesById(id)
            .map(_ => id)
        )
    )

  case class EventInfo(
    eventType: OptEventType,
    majorVersion: Int,
    cohort: Int,
    preferences: Preferences,
    updatedAt: Instant
  )

  object Cohort {
    val SEVEN = 7
    val EIGHT = 8

  }

  def updatePreferences(optInUpdateResult: OptInUpdateResult, preferences: Preferences)(implicit
    ec: ExecutionContext
  ): Future[OptInUpdateResult] = {
    logger.debug("Starting updatePreferences..")
    preferences.termsAndConditions.generic match {
      case Accepted(updatedAt, _, _) if updatedAt.isBefore(versionSwitchDate) =>
        updatePreferencesWithEvent(
          preferences,
          EventInfo(OptIn, 0, Cohort.SEVEN, preferences, updatedAt),
          optInUpdateResult
        )
      case Accepted(updatedAt, _, _) =>
        updatePreferencesWithEvent(
          preferences,
          EventInfo(OptIn, 1, Cohort.EIGHT, preferences, updatedAt),
          optInUpdateResult
        )
      case Refused(updatedAt, _, _) if updatedAt.isBefore(versionSwitchDate) =>
        updatePreferencesWithEvent(
          preferences,
          EventInfo(CustomerOptOut, 0, Cohort.SEVEN, preferences, updatedAt),
          optInUpdateResult
        )
      case Refused(updatedAt, _, _) =>
        updatePreferencesWithEvent(
          preferences,
          EventInfo(CustomerOptOut, 1, Cohort.EIGHT, preferences, updatedAt),
          optInUpdateResult
        )
      case _ =>
        logger.error(s"Acceptance not known for entity ID ${preferences.entityId}")
        Future.successful(optInUpdateResult.incrementFailed)
    }
  }

  private def updatePreferencesWithEvent(
    prefs: Preferences,
    eventInfo: EventInfo,
    optInUpdateResult: OptInUpdateResult
  )(implicit ec: ExecutionContext): Future[OptInUpdateResult] = {
    logger.debug("Starting updatePreferencesWithEvent..")
    getEvent(eventInfo) match {
      case Right(event) =>
        for {
          preferenceUpdated <-
            preferencesRepository
              .updatePreferenceEventTypeAndOptInPage(prefs._id, event, preferenceEvents(event, prefs))
        } yield
          if (preferenceUpdated) {
            logger.debug(s"Migration of preferences for entity ID ${prefs.entityId} successful.")
            optInUpdateResult.incrementUpdated
          } else {
            logger.debug(s"Migration of preferences for entity ID ${prefs.entityId} failed.")
            optInUpdateResult.incrementFailed
          }
      case Left(err) =>
        logger.error(s"Error running record update for entity ID ${prefs.entityId}: $err")
        Future.successful(optInUpdateResult.incrementFailed)
    }
  }

  private[service] def isPaperless(dateTime: Instant, preference: Preferences): Option[Boolean] = {
    val emailVerifiedTime = preference.email.flatMap(_.verifiedOn)
    val emailBouncedTime = preference.email.flatMap(_.lastBounce).map(_.timestamp)
    val emailPendingBouncedTime = preference.pendingEmail.flatMap(_.lastBounce).map(_.timestamp)
    val emailChangedTime =
      if (emailChanged(preference)) preference.pendingEmail.flatMap(_.verificationLink).map(_.linkSentTime) else None
    val optInTime = getOptInTime(preference)
    val optOutTime = getOptOutTime(preference)
    val lastActionTimestamp =
      Seq(emailVerifiedTime, emailBouncedTime, emailPendingBouncedTime, emailChangedTime, optInTime, optOutTime)
        .sortBy(_.map(_.toEpochMilli))
        .lastOption
        .flatten

    lastActionTimestamp match {
      case Some(time) if time == dateTime => Some(preference.isPaperless)
      case _                              => None
    }
  }

  private def getOptInTime(preference: Preferences): Option[Instant] =
    preference.termsAndConditions.generic match {
      case Accepted(updatedAt, _, _) => Some(updatedAt)
      case _                         => None
    }

  private def getOptOutTime(preference: Preferences): Option[Instant] =
    preference.termsAndConditions.generic match {
      case Refused(updatedAt, _, _) => Some(updatedAt)
      case _                        => None
    }

  private def uniqueOptEvent(event: Event, preferenceEvents: List[(String, Instant)]): Option[Event] = {
    val eventTypeToAdd = (Json.toJson(event) \ "eventType").as[String]
    if (preferenceEvents.contains((eventTypeToAdd, event.time))) None
    else Some(event)
  }

  private def preferenceEvents(event: Event, preference: Preferences): List[Event] = {
    val preferenceEvents: List[(String, Instant)] =
      preference.events.map(_.map(event => ((Json.toJson(event) \ "eventType").as[String], event.time))).toList.flatten
    val optEvent = uniqueOptEvent(event, preferenceEvents)
    val emailVerifiedEvent: Option[Event] = getEmailVerifiedEvent(preference, preferenceEvents)
    val emailBouncedEvent = getEmailBouncedEvents(preference, preferenceEvents)
    val pendingEmailBouncedEvent = getPendingEmailBouncedEvents(preference, preferenceEvents)
    val emailChangedEvent = getEmailChangedEvents(preference, preferenceEvents)
    val finalEvents = List(optEvent, emailVerifiedEvent, emailBouncedEvent, pendingEmailBouncedEvent, emailChangedEvent)
    finalEvents.collect { case Some(event) => event }
  }

  private def getEmailVerifiedEvent(preference: Preferences, events: List[(String, Instant)]): Option[EmailEvent] =
    if (emailVerified(preference)) {
      (preference.email.map(_.email), preference.email.flatMap(_.verifiedOn)) match {
        case (Some(email), Some(verifiedOn)) =>
          if (events.isEmpty || !events.exists(event => (event._1 == EMAIL_VERIFIED) && (event._2 == verifiedOn)))
            Some(
              EmailEvent(
                preference.entityId,
                EmailEventType.EmailVerified,
                email,
                isPaperless(verifiedOn, preference),
                verifiedOn
              )
            )
          else None
        case _ =>
          logger.error("Event not updated: email or verifiedOn date in missing")
          None
      }
    } else None

  private def getEmailBouncedEvents(preference: Preferences, events: List[(String, Instant)]): Option[EmailEvent] =
    if (emailBounced(preference)) {
      val bounceTimeStamp = preference.email
        .flatMap(_.lastBounce)
        .map(_.timestamp)
      (preference.email.map(_.email), bounceTimeStamp) match {
        case (Some(email), Some(bounceTimeStamp)) =>
          if (events.isEmpty || !events.exists(event => (event._1 == EMAIL_BOUNCED) && (event._2 == bounceTimeStamp)))
            Some(
              EmailEvent(
                preference.entityId,
                EmailEventType.EmailBounced,
                email,
                isPaperless(bounceTimeStamp, preference),
                bounceTimeStamp
              )
            )
          else None
        case _ =>
          logger.warn("Event not updated: email or timeStamp date in missing")
          None
      }
    } else None

  private def getPendingEmailBouncedEvents(
    preference: Preferences,
    events: List[(String, Instant)]
  ): Option[EmailEvent] =
    if (pendingEmailBounced(preference)) {
      val bounceTimeStamp = preference.pendingEmail.flatMap(_.lastBounce).map(_.timestamp)
      (preference.pendingEmail.map(_.email), bounceTimeStamp) match {
        case (Some(email), Some(bounceTimeStamp)) =>
          if (events.isEmpty || !events.exists(event => (event._1 == EMAIL_BOUNCED) && (event._2 == bounceTimeStamp)))
            Some(
              EmailEvent(
                preference.entityId,
                EmailEventType.EmailBounced,
                email,
                isPaperless(bounceTimeStamp, preference),
                bounceTimeStamp
              )
            )
          else None

        case _ =>
          logger.warn("Event not updated: email or timeStamp date in missing")
          None

      }
    } else None

  private def getEmailChangedEvents(
    preference: Preferences,
    preferenceEvents: List[(String, Instant)]
  ): Option[EmailEvent] =
    if (emailChanged(preference)) {
      val linkSentTime = preference.pendingEmail.flatMap(_.verificationLink).map(_.linkSentTime)
      (preference.pendingEmail.map(_.email), linkSentTime) match {
        case (Some(pendingEmail), Some(linkSentTime)) =>
          if (
            preferenceEvents.isEmpty || !preferenceEvents
              .exists(event => (event._1 == EMAIL_CHANGED) && (event._2 == linkSentTime))
          )
            Some(
              EmailEvent(
                preference.entityId,
                EmailEventType.EmailChanged,
                pendingEmail,
                isPaperless(linkSentTime, preference),
                linkSentTime
              )
            )
          else None

        case _ =>
          logger.warn("Event not updated: email or timeStamp in missing")
          None
      }
    } else None

  private def getLanguage(preferences: Preferences): Language = {
    val language = for {
      email    <- preferences.email
      language <- email.language
    } yield language
    language match {
      case Some(lang) => lang
      case _          => Language.English
    }
  }

  private def getEvent(eventInfo: EventInfo): Either[String, OptPageEvent] = {
    val prefs = eventInfo.preferences
    eventInfo.eventType match {
      case OptIn =>
        Right(
          OptInEvent(
            OptIn,
            OptInPage(Version(eventInfo.majorVersion, 0), eventInfo.cohort, IPage),
            prefs.entityId,
            eventInfo.updatedAt,
            getLanguage(prefs),
            isPaperless(eventInfo.updatedAt, prefs)
          )
        )
      case CustomerOptOut =>
        Right(
          CustomerOptOutEvent(
            CustomerOptOut,
            OptInPage(Version(eventInfo.majorVersion, 0), eventInfo.cohort, IPage),
            prefs.entityId,
            eventInfo.updatedAt,
            getLanguage(prefs),
            isPaperless(eventInfo.updatedAt, prefs)
          )
        )
      case _ => Left("Unknown eventType")
    }
  }

  private def emailVerified(preference: Preferences) = preference.email.map(_.isVerified).nonEmpty
  private def emailBounced(preference: Preferences) =
    preference.email.exists(_.lastBounce.nonEmpty)
  private def pendingEmailBounced(preference: Preferences) = preference.pendingEmail.exists(_.lastBounce.nonEmpty)
  private def emailChanged(preference: Preferences) = emailVerified(preference) && preference.pendingEmail.nonEmpty

}
case class OptInUpdateResult(updated: Int, failed: Int) {
  def incrementUpdated: OptInUpdateResult = copy(updated = updated + 1)
  def incrementFailed: OptInUpdateResult = copy(failed = failed + 1)
}

object Actions {
  val EMAIL_VERIFIED = "email-verified"
  val EMAIL_BOUNCED = "email-bounced"
  val EMAIL_CHANGED = "email-changed"
  val OPT_IN = "opt-in"
  val CUSTOMER_OPT_OUT = "customer-opt-out"
  val ADMIN_OPT_OUT = "admin-opt-out"
}
