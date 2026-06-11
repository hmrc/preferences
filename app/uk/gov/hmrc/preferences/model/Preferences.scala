/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.model

import org.bson.types.ObjectId
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import uk.gov.hmrc.mongo.play.json.formats.{ MongoFormats, MongoJavatimeFormats }
import play.api.libs.json._
import uk.gov.hmrc.preferences.model.EmailPreference.Status._
import uk.gov.hmrc.preferences.model.MessageDeliveryFormat.{ Digital, Paper }
import uk.gov.hmrc.preferences.model.TermsAndConditions.Accepted
import uk.gov.hmrc.preferences.util.{ DateFormats, Dc }

import java.time.{ Instant, LocalDate, ZoneOffset }

case class EmailPreference(
  email: String,
  status: String,
  mailboxFull: Boolean,
  message: Option[String] = None,
  linkSent: Option[LocalDate] = None,
  language: Option[Language] = None
)

object EmailPreference {
  implicit val datetimeFormatDefault: Format[Instant] = DateFormats.instantFormats
  implicit val localdateFormatDefault: Format[LocalDate] = DateFormats.localDateFormats

  case class LocalDateOption(date: Option[LocalDate])
  implicit val formatLocalDateOption: OFormat[LocalDateOption] = Json.format[LocalDateOption]

  implicit val formats: OFormat[EmailPreference] = Json.format[EmailPreference]

  object Status {
    val pending = "pending"
    val bounced = "bounced"
    val verified = "verified"
  }

  def create(
    email: String,
    status: String,
    emailBounce: EmailBounce,
    language: Option[Language] = None
  ): EmailPreference =
    EmailPreference(
      email,
      status,
      emailFull(emailBounce),
      Some(errorCodeToMessage(emailBounce.errorCode)),
      language = language
    )

  private def emailFull(lastBounce: EmailBounce) = 552 == lastBounce.errorCode.getOrElse(-1)

  private def errorCodeToMessage(errorCode: Option[Int]): String =
    errorCode collect {
      case 421 => "Temporary failure - service unavailable"
      case 450 => "Temporary failure - mailbox unavailable"
      case 451 => "Temporary failure - local error in processing"
      case 452 => "Temporary failure - insufficient system storage"
      case 500 | 501 | 502 | 503 | 504 | 521 | 530 | 550 | 551 | 553 | 554 =>
        "your email service is unavailable - you might want to change the email address reminders are sent to."
      case 552 => "your inbox is full."
    } getOrElse "Permanent failure - requested Mailbox unavailable"
}

sealed trait Email {

  def email: Option[EmailAddress]

  def pendingEmail: Option[PendingEmailAddress]

  def resetPending(
    emailAddress: String,
    timeSource: () => Instant,
    returnText: Option[String] = None,
    returnUrl: Option[String] = None,
    language: Option[Language] = None
  ): PendingEmailAddress =
    pendingEmail.fold(PendingEmailAddress.create(emailAddress, timeSource(), returnText, returnUrl, language)) {
      existing =>
        val expiry = timeSource()
        PendingEmailAddress(
          email = emailAddress,
          verificationLink = existing.verificationLink
            .map { link =>
              if (link.isValid(expiry) && existing.email == emailAddress)
                link.copy(linkSentTime = expiry, returnText = returnText, returnUrl = returnUrl)
              else EmailVerificationLink(linkSentTime = expiry, returnText = returnText, returnUrl = returnUrl)
            }
            .orElse(Some(EmailVerificationLink(linkSentTime = expiry, returnText = returnText, returnUrl = returnUrl))),
          language = language
        )
    }

  def isVerifiedAndHasNoBounces: Boolean =
    email.exists(addr => addr.verifiedOn.isDefined && addr.lastBounce.isEmpty)

  def mostRecentlyAddedEmail: Option[EmailPreference] =
    toEmailPreference(pendingEmail, pending).orElse(toEmailPreference(email, verified))

  def toEmailPreference(bounceableEmail: Option[BounceableEmail], status: String): Option[EmailPreference] =
    bounceableEmail flatMap {
      case e if e.isBounced =>
        e.lastBounce.map(b => EmailPreference.create(e.email, bounced, b, e.language))
      case e =>
        val sentDate = for {
          email <- pendingEmail
          link  <- email.verificationLink
        } yield LocalDate.ofInstant(link.linkSentTime, ZoneOffset.UTC)
        Some(EmailPreference(e.email, status, mailboxFull = false, None, sentDate, e.language))
    }
}

sealed trait CreatedAndUpdated {

  def _id: ObjectId

  def createdAt: Instant

  def updatedAt: Instant
}

sealed trait Paperless { this: Email =>

  def termsAndConditions: TermsAndConditions
}

sealed trait DigitalSupport { this: Email with ContactablePref =>

  def isOptedOut(terms: String): Boolean

  def hasEmail = !(email.isEmpty && pendingEmail.isEmpty)
}

sealed trait ContactablePref { this: Email with Paperless with DigitalSupport =>

  def contactabilityStatus(): Contactability =
    (hasEmail, isVerifiedAndHasNoBounces, email) match {
      case (false, _, _)                  => OptedOut
      case (_, _, Some(e)) if e.isBounced => Bounced
      case (_, true, Some(e))             => Contactable(e)
      case _                              => PendingVerification
    }
}

case class MarkForDeEnrolmentOption(deEnrolmentOption: Option[MarkForDeEnrolment])
object MarkForDeEnrolmentOption {
  implicit val deFormat: OFormat[MarkForDeEnrolmentOption] = Json.format[MarkForDeEnrolmentOption]
}

case class MarkForDeEnrolment(time: Instant, identifier: String)
object MarkForDeEnrolment {
  implicit val dateFormat: Format[Instant] = MongoJavatimeFormats.instantFormat

  implicit val aaa: OFormat[MarkForDeEnrolmentOption] = MarkForDeEnrolmentOption.deFormat
  implicit def formatsDe: OFormat[MarkForDeEnrolment] = Json.format[MarkForDeEnrolment]
}

case class Preferences(
  entityId: EntityId,
  termsAndConditions: TermsAndConditions,
  _id: ObjectId = ObjectId.get(),
  email: Option[EmailAddress] = None,
  pendingEmail: Option[PendingEmailAddress] = None,
  createdAt: Instant = Dc.instantNow(),
  updatedAt: Instant = Dc.instantNow(),
  userType: Option[UserType] = None,
  events: Option[List[Event]] = None,
  surveys: Option[List[Survey]] = None,
  markForDeEnrolment: Option[MarkForDeEnrolment] = None
) extends CreatedAndUpdated with Email with ContactablePref with Paperless with DigitalSupport {

  override def isOptedOut(terms: String): Boolean =
    termsAndConditions.findBy(terms).fold(true)(!_.isInstanceOf[TermsAndConditions.Accepted])

  def isPaperless: Boolean =
    isVerifiedAndHasNoBounces && termsAndConditions.generic.isInstanceOf[Accepted]

  def messageDeliveryFormat: MessageDeliveryFormat =
    if (isPaperless) Digital else Paper
}

object Preferences {

  implicit val instantFormats: Format[Instant] = MongoJavatimeFormats.instantFormat
  implicit val objectIdFormats: Format[ObjectId] = MongoFormats.objectIdFormat
  implicit val surveyFormats: Format[Survey] = Survey.suveyFormat
  implicit val surveyTypeFormats: Format[SurveyType] = SurveyType.given_Format_SurveyType
  implicit val termAndConditionsFormats: Format[TermsAndConditions] = TermsAndConditions.formats
  implicit val markForDeEnrolmentFormats: OFormat[MarkForDeEnrolment] = MarkForDeEnrolment.formatsDe
  implicit val formatsMarkForDeEnrolmentOption: OFormat[MarkForDeEnrolmentOption] =
    Json.format[MarkForDeEnrolmentOption]

  val reads = (
    (__ \ "entityId").read[EntityId] and
      (__ \ "termsAndConditions").readNullable[TermsAndConditions].map(_.getOrElse(TermsAndConditions.empty)) and
      (__ \ "_id").read[ObjectId] and
      (__ \ "email").readNullable[EmailAddress] and
      (__ \ "pendingEmail").readNullable[PendingEmailAddress] and
      (__ \ "createdAt").read[Instant] and
      (__ \ "updatedAt").read[Instant] and
      (__ \ "userType").readNullable[UserType] and
      (__ \ "events").readNullable[List[Event]] and
      (__ \ "survey").readNullable[List[Survey]] and
      (__ \ "markForDeEnrolment").readNullable[MarkForDeEnrolment]
  )(Preferences.apply _)
  implicit val formats: Format[Preferences] = Format(reads, Json.writes[Preferences])
}

sealed trait Contactability
case object PendingVerification extends Contactability
case class Contactable(email: EmailAddress) extends Contactability
case object Bounced extends Contactability
case object OptedOut extends Contactability

sealed trait TermsResult
case object TermsAccepted extends TermsResult
case object TermsNotAccepted extends TermsResult
