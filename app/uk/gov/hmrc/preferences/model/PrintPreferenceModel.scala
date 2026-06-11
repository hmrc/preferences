/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.model

import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.ToDo
import uk.gov.hmrc.preferences.model.TermsAndConditions.Acceptance
import uk.gov.hmrc.mongo.workitem._
import uk.gov.hmrc.preferences.model.OptEventType.{ AdminOptOut, SystemOptOut }
import uk.gov.hmrc.preferences.repository.ReminderWorkItem
import uk.gov.hmrc.preferences.util.Dc

import java.time.Instant

object EmailBounce {
  implicit val formats: OFormat[EmailBounce] = {
    implicit val dateTimeFormat: Format[Instant] = MongoJavatimeFormats.instantFormat
    Json.format[EmailBounce]
  }
}

case class EmailBounce(errorCode: Option[Int], timestamp: Instant)

trait BounceableEmail {
  val email: String
  val lastBounce: Option[EmailBounce]
  val isBounced: Boolean = lastBounce.isDefined
  val language: Option[Language]
}

object EmailAddress {
  implicit val formats: Format[EmailAddress] = {
    implicit val dateTimeFormat: Format[Instant] = MongoJavatimeFormats.instantFormat
    implicit val evFormat: Format[EmailVerificationLink] = EmailVerificationLink.evFormat

    val emailAddressReads: Reads[EmailAddress] = ((__ \ Symbol("email")).read[String] and
      (__ \ Symbol("verifiedOn")).readNullable[Instant] and
      (__ \ Symbol("lastBounce")).readNullable[EmailBounce] and
      (__ \ Symbol("bounceCount")).readNullable[Int] and
      (__ \ Symbol("verifiedWithLink")).readNullable[EmailVerificationLink] and
      (__ \ Symbol("language")).readNullable[Language])(
      (email, verifiedOn, lastBounce, bounceCount, verifiedWithLink, language) =>
        EmailAddress(email, verifiedOn, lastBounce, bounceCount.getOrElse(0), verifiedWithLink, language)
    )

    val emailAddressWrites: Writes[EmailAddress] = ((__ \ Symbol("email")).write[String] and
      (__ \ Symbol("lowercaseEmail")).write[String] and
      (__ \ Symbol("verifiedOn")).writeNullable[Instant] and
      (__ \ Symbol("lastBounce")).writeNullable[EmailBounce] and
      (__ \ Symbol("bounceCount")).write[Int] and
      (__ \ Symbol("verifiedWithLink")).writeNullable[EmailVerificationLink] and
      (__ \ Symbol("language")).writeNullable[Language])(asTuple _)
    Format(emailAddressReads, emailAddressWrites)
  }

  private def asTuple(emailAddress: EmailAddress) = {
    import emailAddress._
    (email, email.toLowerCase, verifiedOn, lastBounce, bounceCount, verifiedWithLink, language)
  }
}

case class EmailAddress(
  email: String,
  verifiedOn: Option[Instant] = None,
  lastBounce: Option[EmailBounce] = None,
  bounceCount: Int = 0,
  verifiedWithLink: Option[EmailVerificationLink] = None,
  language: Option[Language] = None
) extends BounceableEmail {
  val isVerified: Boolean = verifiedOn.isDefined
}

object Reminder {
  implicit val dateFormat: Format[Instant] = MongoJavatimeFormats.instantFormat
  import uk.gov.hmrc.mongo.workitem.ProcessingStatus.Implicits._
  implicit val reminderFormat: OFormat[Reminder] = Json.format[Reminder]
}

case class Reminder(status: ProcessingStatus, updatedAt: Instant)

object Reminders {
  val firstReminder = "pendingEmail.reminder"
  val secondReminder = "pendingEmail.secondReminder"

  def daysAgo(workItem: ReminderWorkItem): String =
    if (workItem.reminderField == firstReminder) "3" else "5"
}

object PendingEmailAddress {
  def create(
    emailAddress: String,
    createdAt: Instant,
    returnText: Option[String] = None,
    returnUrl: Option[String] = None,
    lan: Option[Language] = None
  ): PendingEmailAddress =
    PendingEmailAddress(
      emailAddress,
      verificationLink =
        Some(EmailVerificationLink(linkSentTime = createdAt, returnText = returnText, returnUrl = returnUrl)),
      language = lan
    )

  implicit val formats: Format[PendingEmailAddress] = {

    implicit val evFormat: Format[EmailVerificationLink] = EmailVerificationLink.evFormat

    val pendingEmailAddressReads: Reads[PendingEmailAddress] = ((__ \ Symbol("email")).read[String] and
      (__ \ Symbol("lastBounce")).readNullable[EmailBounce] and
      (__ \ Symbol("verificationLink")).readNullable[EmailVerificationLink] and
      (__ \ Symbol("reminder")).readNullable[Reminder] and
      (__ \ Symbol("language")).readNullable[Language] and
      (__ \ Symbol("secondReminder")).readNullable[Reminder])(
      (email, lastBounce, verificationLink, reminder, language, secondReminder) =>
        PendingEmailAddress(email, lastBounce, verificationLink, reminder, language, secondReminder)
    )

    implicit val pendingEmailAddressWrites: Writes[PendingEmailAddress] = (
      (__ \ Symbol("email")).write[String] and
        (__ \ Symbol("lowercaseEmail")).write[String] and
        (__ \ Symbol("lastBounce")).writeNullable[EmailBounce] and
        (__ \ Symbol("verificationLink")).writeNullable[EmailVerificationLink] and
        (__ \ Symbol("reminder")).writeNullable[Reminder] and
        (__ \ Symbol("language")).writeNullable[Language] and
        (__ \ Symbol("secondReminder")).writeNullable[Reminder]
    )(asTuple _)

    Format(pendingEmailAddressReads, pendingEmailAddressWrites)
  }

  private def asTuple(emailAddress: PendingEmailAddress) = {
    import emailAddress._
    (email, email.toLowerCase, lastBounce, verificationLink, reminder, language, secondReminder)
  }
}

case class PendingEmailAddress(
  email: String,
  lastBounce: Option[EmailBounce] = None,
  verificationLink: Option[EmailVerificationLink] = None,
  reminder: Option[Reminder] = Some(Reminder(ToDo, Dc.instantNow())),
  language: Option[Language] = None,
  secondReminder: Option[Reminder] = Some(Reminder(ToDo, Dc.instantNow()))
) extends BounceableEmail {}

trait TimeSource {
  def now: Instant
}

trait SystemTimeSource extends TimeSource {
  override def now: Instant = Instant.now
}

object SystemTimeSource extends SystemTimeSource

case class TermsAndConditions(generic: Acceptance) {

  def asMap(): Map[String, Acceptance] =
    Map(
      TermsAndConditions.GENERIC -> generic
    )

  def withTerms(name: String, value: Acceptance): TermsAndConditions =
    name match {
      case TermsAndConditions.GENERIC => this.copy(generic = value)
      case _                          => this
    }

  def findBy(termsAndConditionsName: String): Option[Acceptance] =
    termsAndConditionsName match {
      case TermsAndConditions.GENERIC => Some(generic)
      case _                          => None
    }
}

object TermsAndConditions {

  val GENERIC: String = "generic"

  val DEFAULT_VERSION: Version = Version(1, 0)
  val DEFAULT_COHORT = 8
  import uk.gov.hmrc.preferences.model.Event.optInPageFormats

  val empty = TermsAndConditions(generic = Unknown)

  sealed trait Acceptance
  sealed trait Known
  case object Unknown extends Acceptance
  case class Accepted(updatedAt: Instant, eventType: Option[OptEventType] = None, optInPage: Option[OptInPage] = None)
      extends Acceptance with Known
  case class Refused(updatedAt: Instant, eventType: Option[OptEventType] = None, optInPage: Option[OptInPage] = None)
      extends Acceptance with Known

  object Acceptance {
    private implicit val dateTimeFormat: Format[Instant] = MongoJavatimeFormats.instantFormat

    implicit val read: Reads[Acceptance] = new Reads[Acceptance] {
      private val fieldsReader = ((__ \ "accepted").read[Boolean] and
        (__ \ "updatedAt").read[Instant] and
        (__ \ "eventType").readNullable[OptEventType] and
        (__ \ "optInPage").readNullable[OptInPage]).tupled

      override def reads(json: JsValue): JsResult[Acceptance] = json match {
        case JsNull => JsSuccess(Unknown)
        case obj =>
          obj.validate(fieldsReader).map {
            case (true, updatedAt, Some(eventType), Some(optInPage)) =>
              Accepted(updatedAt, Some(eventType), Some(optInPage))
            case (true, updatedAt, _, _) => Accepted(updatedAt)
            case (false, updatedAt, Some(optOutAction), Some(optInPage)) =>
              Refused(updatedAt, Some(optOutAction), Some(optInPage))
            case (false, updatedAt, Some(optOutAction), _) => Refused(updatedAt, Some(optOutAction))
            case (false, updatedAt, _, _)                  => Refused(updatedAt)
          }
      }
    }

    implicit val write: Writes[Acceptance] = new Writes[Acceptance] {
      override def writes(a: Acceptance): JsValue = a match {
        case Accepted(updatedAt, Some(eventType), Some(optInPage)) =>
          Json.obj("accepted" -> true, "updatedAt" -> updatedAt, "eventType" -> eventType, "optInPage" -> optInPage)
        case Accepted(updatedAt, _, _) => Json.obj("accepted" -> true, "updatedAt" -> updatedAt)
        case Refused(updatedAt, Some(eventType), Some(optInPage)) =>
          Json.obj("accepted" -> false, "updatedAt" -> updatedAt, "eventType" -> eventType, "optInPage" -> optInPage)
        case Refused(updatedAt, Some(eventType), _) if Seq(SystemOptOut, AdminOptOut).contains(eventType) =>
          Json.obj("accepted" -> false, "updatedAt" -> updatedAt, "eventType" -> eventType)
        case Refused(updatedAt, _, _) => Json.obj("accepted" -> false, "updatedAt" -> updatedAt)
        case _ => throw new IllegalArgumentException("Unknown Terms and Condition cannot be written")
      }
    }
  }

  implicit val formats: Format[TermsAndConditions] = Format(
    fjs = ((__ \ "generic").readNullable[Acceptance]).map { case (gen) =>
      TermsAndConditions(gen.getOrElse(Unknown))
    },
    tjs = new Writes[TermsAndConditions] {
      def writes(a: TermsAndConditions): JsValue = a match {
        case TermsAndConditions(Unknown) => Json.obj()
        case TermsAndConditions(gen)     => Json.obj("generic" -> gen)
      }
    }
  )
}
