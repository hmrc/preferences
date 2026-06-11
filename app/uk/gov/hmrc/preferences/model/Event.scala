/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.model

import play.api.libs.json._
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import java.time.Instant
import scala.collection.immutable

sealed trait EventType {
  def entryName: String
}

object EventType {

  given eventTypeReads: Reads[EventType] = Reads {
    case JsString(value) =>
      OptEventType.values.find(_.entryName == value) match {
        case Some(eventType) => JsSuccess(eventType)
        case None =>
          EmailEventType.values.find(_.entryName == value) match {
            case Some(eventType) => JsSuccess(eventType)
            case None            => JsError(s"Invalid NamedEnum value: $value")
          }
      }

    case _ => JsError("Expected a JsString for NamedEnum")
  }

  given eventTypeWrites: Writes[EventType] = Writes {
    case eventYpe: OptEventType    => JsString(eventYpe.entryName)
    case eventType: EmailEventType => JsString(eventType.entryName)
  }
}

enum OptEventType(val entryName: String) extends EventType {
  case OptIn extends OptEventType("opt-in")
  case ReOptIn extends OptEventType("re-opt-in")
  case CustomerOptOut extends OptEventType("customer-opt-out")
  case CustomerReOptOut extends OptEventType("customer-re-opt-out")
  case AdminOptOut extends OptEventType("admin-opt-out")
  case SystemOptOut extends OptEventType("system-opt-out")
  case SystemOptOutMissingEmail extends OptEventType("system-opt-out-missing-email")
  case ReOptInModifiedJourney extends OptEventType("re-opt-in-modified-journey")
}

object OptEventType {

  given eventTypeReads: Reads[OptEventType] = Reads {
    case JsString(value) =>
      OptEventType.values.find(_.entryName == value) match {
        case Some(eventType) => JsSuccess(eventType)
        case None            => JsError(s"Invalid OptEventType: $value")
      }
    case _ => JsError("Expected OptEventType as JsString")
  }

  given eventTypeWrites: Writes[OptEventType] = Writes { eventType =>
    JsString(eventType.entryName)
  }

  given eventTypeFormat: Format[OptEventType] = Format(eventTypeReads, eventTypeWrites)
}

enum EmailEventType(val entryName: String) extends EventType {
  case EmailVerified extends EmailEventType("email-verified")
  case EmailBounced extends EmailEventType("email-bounced")
  case EmailChanged extends EmailEventType("email-changed")
  case EmailReVerifyJourney extends EmailEventType("email-re-verify-journey")
  case EmailBounceJourney extends EmailEventType("email-bounce-journey")
  case SystemExpiredPendingEmailRemoval extends EmailEventType("system-expired-pending-email-removal")
}

object EmailEventType {
  given emailActionReads: Reads[EmailEventType] = Reads {
    case JsString(value) =>
      EmailEventType.values.find(_.entryName == value) match {
        case Some(action) => JsSuccess(action)
        case None         => JsError(s"Invalid EmailEventType: $value")
      }
    case _ => JsError("Expected EmailEventType as JsString")
  }

  given emailActionWrites: Writes[EmailEventType] = Writes { action =>
    JsString(action.entryName)
  }

  given emailActionFormat: Format[EmailEventType] = Format(emailActionReads, emailActionWrites)
}

enum PageType {
  case IPage
  case TCPage
  case UPage
  case ReOptInPage
  case CYSConfirmPage
  case AndroidOptInPage
  case AndroidReOptInPage
  case AndroidOptOutPage
  case AndroidReOptOutPage
  case IosOptInPage
  case IosReOptInPage
  case IosOptOutPage
  case IosReOptOutPage

  def isMobile: Boolean = Set("android", "ios").exists(toString.toLowerCase.startsWith)
}

object PageType {
  given pageTypeReads: Reads[PageType] = Reads {
    case JsString(value) =>
      try
        JsSuccess(PageType.valueOf(value))
      catch {
        case _: IllegalArgumentException => JsError(s"Invalid PageType: $value")
      }
    case _ => JsError("Expected PageType as JsString")
  }

  given pageTypeWrites: Writes[PageType] = Writes { pageType =>
    JsString(pageType.toString)
  }

  given pageTypeFormat: Format[PageType] = Format(pageTypeReads, pageTypeWrites)

}

case class OptInEvent(
  eventType: OptEventType,
  optInPage: OptInPage,
  entityId: EntityId,
  time: Instant,
  language: Language,
  paperless: Option[Boolean] = None,
  emailAddress: Option[EmailAddress] = None
) extends OptPageEvent

case class CustomerOptOutEvent(
  eventType: OptEventType,
  optInPage: OptInPage,
  entityId: EntityId,
  time: Instant,
  language: Language,
  paperless: Option[Boolean]
) extends OptPageEvent

case class AdminOptOutEvent(eventType: OptEventType, entityId: EntityId, time: Instant, paperless: Option[Boolean])
    extends OptEvent

case class SystemOptOutEvent(eventType: OptEventType, entityId: EntityId, time: Instant, paperless: Option[Boolean])
    extends OptEvent

case class Version(major: Int, minor: Int)

case class EmailEvent(
  entityId: EntityId,
  eventType: EmailEventType,
  emailAddress: String,
  paperless: Option[Boolean],
  time: Instant
) extends Event

case class OptInPage(version: Version, cohort: Int, pageType: PageType)

case class OptInBundle(optInPage: Option[OptInPage] = None, eventType: Option[OptEventType] = None)

sealed trait Event {
  def entityId: EntityId
  def time: Instant
  def paperless: Option[Boolean]
}

sealed trait OptEvent extends Event {
  def eventType: OptEventType
}

sealed trait OptPageEvent extends OptEvent {
  def optInPage: OptInPage
}

object Event {
  implicit val eventTimeFormats: Format[Instant] = MongoJavatimeFormats.instantFormat

  implicit val versionFormats: OFormat[Version] = Json.format[Version]
  implicit val optInPageFormats: OFormat[OptInPage] = Json.format[OptInPage]
  implicit val optInEventFormats: OFormat[OptInEvent] = Json.format[OptInEvent]
  implicit val emailEventFormats: OFormat[EmailEvent] = Json.format[EmailEvent]
  implicit val customerOptOutFormats: OFormat[CustomerOptOutEvent] = Json.format[CustomerOptOutEvent]
  implicit val adminOptOutEventFormats: OFormat[AdminOptOutEvent] = Json.format[AdminOptOutEvent]
  implicit val systemOptOutEventFormats: OFormat[SystemOptOutEvent] = Json.format[SystemOptOutEvent]

  implicit val eventReads: Reads[Event] =
    (json: JsValue) =>
      (json \ "_type").validate[String].flatMap {
        case "uk.gov.hmrc.preferences.model.EmailEvent" =>
          json.validate[EmailEvent]
        case "uk.gov.hmrc.preferences.model.AdminOptOutEvent" =>
          json.validate[AdminOptOutEvent]
        case "uk.gov.hmrc.preferences.model.SystemOptOutEvent" =>
          json.validate[SystemOptOutEvent]
        case "uk.gov.hmrc.preferences.model.OptInEvent" =>
          json.validate[OptInEvent]
        case "uk.gov.hmrc.preferences.model.CustomerOptOutEvent" =>
          json.validate[CustomerOptOutEvent]

        case unexpectedType =>
          JsError(s"Unexpected type $unexpectedType")
      }

  implicit val eventWrites: Writes[Event] = {
    case ee: EmailEvent =>
      Json.obj("_type" -> "uk.gov.hmrc.preferences.model.EmailEvent") deepMerge emailEventFormats.writes(ee)
    case aooe: AdminOptOutEvent =>
      Json.obj("_type" -> "uk.gov.hmrc.preferences.model.AdminOptOutEvent") deepMerge adminOptOutEventFormats
        .writes(aooe)
    case sooe: SystemOptOutEvent =>
      Json.obj("_type" -> "uk.gov.hmrc.preferences.model.SystemOptOutEvent") deepMerge systemOptOutEventFormats
        .writes(sooe)
    case oie: OptInEvent =>
      Json.obj("_type" -> "uk.gov.hmrc.preferences.model.OptInEvent") deepMerge optInEventFormats.writes(oie)
    case cooe: CustomerOptOutEvent =>
      Json.obj("_type" -> "uk.gov.hmrc.preferences.model.CustomerOptOutEvent") deepMerge customerOptOutFormats
        .writes(cooe)
  }

  implicit val eventFormats: Format[Event] = Format(eventReads, eventWrites)
}
