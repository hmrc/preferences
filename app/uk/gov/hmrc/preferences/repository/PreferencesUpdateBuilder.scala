/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.repository

import org.bson.conversions.Bson
import org.mongodb.scala.bson.BsonDocument
import uk.gov.hmrc.mongo.play.json.Codecs
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.{ PushOptions, Updates }
import org.mongodb.scala.model.Updates._
import play.api.Logger
import play.api.libs.json.{ JsObject, Json }
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.ToDo
import uk.gov.hmrc.preferences.controllers.model.Credentials
import uk.gov.hmrc.preferences.controllers.model.Credentials._
import uk.gov.hmrc.preferences.model._
import uk.gov.hmrc.mongo.workitem.ProcessingStatus
import uk.gov.hmrc.preferences.util.Dc

import java.time.Instant

trait PreferencesUpdateBuilder extends QueryBase {
  val logger: Logger = Logger(this.getClass)

  val unsetEmailQuery: Bson =
    combine(
      unset("email"),
      unset("pendingEmail")
    )

  val unsetPendingEmailQuery: JsObject =
    Json.obj("$unset" -> Json.obj("pendingEmail" -> 1))

  val unsetEmailBounceUpdate: Bson = unset("email.lastBounce")

  def incrementBounceQueryUpdate(emailBounce: Option[EmailBounce]): Bson =
    emailBounce.fold[Bson](Document()) { _ =>
      inc("email.bounceCount", 1)
    }

  def setUpdateAt(time: Instant): Bson =
    set("updatedAt", time)

  def setEventsUpdate(events: Option[List[Event]]): Bson = {
    import uk.gov.hmrc.preferences.model.Event.eventFormats
    events match {
      case Some(eventList) =>
        // Deduplicate events
        val eventsBsonList = eventList.distinct.sortBy(ev => ev.time).reverse.map(Codecs.toBson(_))
        logger.debug(s"[setEventsUpdate] setting ${eventsBsonList.size} distinct events (was ${eventList.size}) events")
        val bsonValues = eventList.map(ev => Codecs.toBson(eventFormats.writes(ev)))
        set("events", bsonValues)
      case None => Document()
    }
  }

  def setSurveyUpdate(surveys: Option[List[Survey]]): Bson =
    surveys match {
      case Some(s) =>
        val surveyBsonList = s.toSet[Survey].toList.map(Codecs.toBson(_))
        logger.debug(s"[setSurveyUpdate] setting ${surveyBsonList.size} distinct surveys (was ${s.size}) surveys")
        Updates.set("survey", surveyBsonList)
      case None => Document()
    }

  def pushEventUpdate(maybeEvent: Option[Event]): Bson = {
    val ef = Event.eventFormats
    maybeEvent match {
      case Some(event) =>
        logger.debug(s"[pushEventUpdate] pushing 1 event")
        Updates.pushEach("events", PushOptions().position(0), Codecs.toBson(ef.writes(event)))
      case _ => Document()
    }
  }

  def pushEventsForUpdate(eventsToPush: Seq[Event]): Bson = {
    val ef = Event.eventFormats
    if (eventsToPush.nonEmpty) {
      logger.debug(s"[pushEventsForUpdate] pushing ${eventsToPush.size} events")
      val bsonValues = eventsToPush.map(e => Codecs.toBson(ef.writes(e)))
      Updates.pushEach("events", PushOptions().position(0), bsonValues: _*)
    } else {
      BsonDocument()
    }
  }

  def setReminderStatusUpdate(status: ProcessingStatus, reminderField: String): Bson =
    set(reminderField, Codecs.toBson(Reminder(status, Dc.instantNow())))

  def defaultUpdate(
    entityId: EntityId,
    termsAndConditions: TermsAndConditions,
    time: Instant,
    credentials: Option[Credentials]
  ): Bson =
    Codecs
      .toBson(
        Json.obj(
          "$setOnInsert" -> Json.obj(
            "entityId"  -> entityId.value,
            "createdAt" -> time
          ),
          "$set" -> {
            Json.obj("termsAndConditions" -> termsAndConditions, "updatedAt" -> time) ++ credentials
              .fold(Json.obj())(credential =>
                credential.affinityGroup.fold(Json.obj())(ag => Json.obj("userType.affinityGroup" -> ag)) ++
                  Json.obj("userType.confidenceLevel" -> credential.confidenceLevel)
              )
          }
        )
      )
      .asDocument()

  def setEventTypeAndOptInPageUpdate(event: OptPageEvent, events: List[Event]): Bson = {
    val optInPage = event.optInPage
    combine(
      set("termsAndConditions.generic.eventType", event.eventType.entryName),
      set("termsAndConditions.generic.optInPage.version.major", optInPage.version.major),
      set("termsAndConditions.generic.optInPage.version.minor", optInPage.version.minor),
      set("termsAndConditions.generic.optInPage.cohort", optInPage.cohort),
      set("termsAndConditions.generic.optInPage.pageType", optInPage.pageType.toString),
      setEventsUpdate(Option(events))
    )
  }

  def setLastBounceUpdate(emailBounce: Option[EmailBounce]): Bson =
    emailBounce.fold[Bson](Document()) { e =>
      set("email.lastBounce", Codecs.toBson(e))
    }

  def setPendingEmailBounceUpdate(emailBounce: Option[EmailBounce]): Bson =
    emailBounce.fold[Bson](Document()) { e =>
      combine(
        set("pendingEmail.lastBounce", Codecs.toBson(e)),
        unset("pendingEmail.verificationLink")
      )
    }

  def setEmailLanguageUpdate(lang: Option[Language]): Bson =
    set("email.language", Codecs.toBson(lang)) // TODO

  def setPendingEmailLanguageUpdate(lang: Option[Language]): Bson =
    set("pendingEmail.language", Codecs.toBson(lang)) // TODO

  def setPendingEmailUpdate(email: PendingEmailAddress): Bson =
    set("pendingEmail", Codecs.toBson(email))

  def setPendingEmailVerificationLinkSentUpdate(link: EmailVerificationLink): Bson =
    set("pendingEmail.verificationLink.linkSentTime", expireVerificationLink(link))

  def markPreferenceForDeEnrolment(m: MarkForDeEnrolment): Bson =
    set("markForDeEnrolment", Codecs.toBson(m))

  def unsetDeEnrolmentJson: Bson =
    unset("markForDeEnrolment")

  def setEmailMailVerifiedUpdate(time: Instant, pendingEmail: PendingEmailAddress, lang: Option[Language]): Bson =
    combine(
      set(
        "email",
        Codecs.toBson(
          EmailAddress(
            email = pendingEmail.email,
            lastBounce = pendingEmail.lastBounce,
            verifiedOn = Some(time),
            verifiedWithLink = pendingEmail.verificationLink,
            language = lang
          )
        )
      ),
      set("updatedAt", time),
      set("termsAndConditions.generic.accepted", true),
      unset("pendingEmail")
    )

  def setUpdatedAtUpdate(time: Instant): Bson =
    set("updatedAt", time)

  def getPendingEmailUpdate(pendingEmail: PendingEmailAddress, time: Instant): Bson = {
    val list1: List[Bson] = List(
      set("pendingEmail.email", pendingEmail.email),
      set("pendingEmail.lowercaseEmail", pendingEmail.email.toLowerCase),
      set("pendingEmail.reminder.status", Codecs.toBson(ToDo.name)),
      set("pendingEmail.reminder.updatedAt", time),
      set("pendingEmail.secondReminder.status", Codecs.toBson(ToDo.name)),
      set("pendingEmail.secondReminder.updatedAt", time)
    )

    val list2 = list1 ::: pendingEmail.language.fold(List[Bson]())(language =>
      List[Bson](set("pendingEmail.language", Codecs.toBson(language)))
    ) // TODO
    val list3 = list2 ::: pendingEmail.verificationLink.fold(List[Bson]())(evl =>
      List[Bson](set("pendingEmail.verificationLink", Codecs.toBson(evl)))
    )

    combine(list3.map(a => a): _*)
  }

  private def expireVerificationLink(link: EmailVerificationLink): Instant =
    link.linkSentTime.minus(EmailVerificationLink.verificationLinkTimeout)

}
