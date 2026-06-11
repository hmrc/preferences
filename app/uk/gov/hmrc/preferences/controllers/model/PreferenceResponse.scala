/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.paperless.controllers.model

import ch.qos.logback.core.status.StatusUtil
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{ Format, JsError, JsPath, JsResult, JsString, JsSuccess, JsValue, Json, OFormat, Reads, Writes }
import uk.gov.hmrc.paperless.controllers.model.Category.{ ActionRequired, Info, OptInRequired, ReOptInRequired }
import uk.gov.hmrc.paperless.controllers.model.StatusName.{ Alright, BouncedEmail, EmailNotVerified, NewCustomer, NoEmail, OldVersion, Paper, ReOptInModified }
import uk.gov.hmrc.preferences.controllers.model.Credentials
import uk.gov.hmrc.preferences.model.EmailPreference.Status
import uk.gov.hmrc.preferences.model.TermsAndConditions.{ Accepted, Refused }
import uk.gov.hmrc.preferences.model.{ EntityId, Language, OptEventType, Preferences, Survey }
import uk.gov.hmrc.preferences.util.{ DateFormats, Dc }
import uk.gov.hmrc.preferences.util.Serializations.snakeToPascalCase
import uk.gov.hmrc.preferences.util.Serializations.pascalToSnakeCase
import java.time.temporal.ChronoUnit
import java.time.{ Instant, LocalDate }
import scala.annotation.unused

case class EmailPreference(
  email: String,
  isVerified: Boolean,
  hasBounces: Boolean,
  mailboxFull: Boolean,
  linkSent: Option[LocalDate],
  verifiedOn: Option[Instant],
  status: String,
  bounceCount: Int = 0,
  language: Option[Language] = None,
  pendingEmail: Option[String] = None
)

object EmailPreference {
  implicit val dateFormatDefault: Format[Instant] = DateFormats.instantFormats
  implicit val localDateFormatDefault: Format[LocalDate] = DateFormats.localDateFormats

  //  implicit val dateFormatDefault = new Format[Instant] {
  //    override def reads(json: JsValue): JsResult[Instant] = JodaReads.DefaultJodaDateTimeReads.reads(json)
  //    override def writes(o: Instant): JsValue = JodaWrites.JodaDateTimeNumberWrites.writes(o)
  //  }

  //  implicit val localDateFormatDefault = new Format[LocalDate] {
  //    override def reads(json: JsValue): JsResult[LocalDate] = JodaReads.DefaultJodaLocalDateReads.reads(json)
  //    override def writes(o: LocalDate): JsValue = JodaWrites.DefaultJodaLocalDateWrites.writes(o)
  //  }

  implicit val formats: OFormat[EmailPreference] = Json.format[EmailPreference]

  def apply(
    email: String,
    isVerified: Boolean,
    hasBounces: Boolean,
    mailboxFull: Boolean,
    linkSent: Option[LocalDate],
    verifiedOn: Option[Instant],
    bounceCount: Int,
    language: Option[Language],
    pendingEmail: Option[String]
  ): EmailPreference = {
    val status = (isVerified, hasBounces) match {
      case (true, _)      => Status.verified
      case (_, true)      => Status.bounced
      case (false, false) => Status.pending
    }
    new EmailPreference(
      email,
      isVerified,
      hasBounces,
      mailboxFull,
      linkSent,
      verifiedOn,
      status,
      bounceCount,
      language,
      pendingEmail
    )
  }
}

case class AcceptanceResponse(
  accepted: Boolean,
  updatedAt: Option[Instant],
  majorVersion: Option[Int],
  paperless: Option[Boolean],
  eventType: Option[OptEventType],
  isViaMobileApp: Option[Boolean] = None
)

object AcceptanceResponse {

  implicit val dateFormatDefault: Format[Instant] = DateFormats.instantFormats
//  implicit val dateFormatDefault = new Format[DateTime] {
//    override def reads(json: JsValue): JsResult[DateTime] = JodaReads.DefaultJodaDateTimeReads.reads(json)
//    override def writes(o: DateTime): JsValue = JodaWrites.JodaDateTimeNumberWrites.writes(o)
//  }
  implicit val formats: OFormat[AcceptanceResponse] = Json.format[AcceptanceResponse]
}

enum StatusName {
  case Paper
  case EmailNotVerified
  case BouncedEmail
  case Alright
  case NewCustomer
  case NoEmail
  case OldVersion
  case ReOptInModified
}

object StatusName {
  val paper: StatusName = StatusName.Paper
  val emailNotVerified: StatusName = StatusName.EmailNotVerified
  val bouncedEmail: StatusName = StatusName.BouncedEmail
  val alright: StatusName = StatusName.Alright
  val newCustomer: StatusName = StatusName.NewCustomer
  val noEmail: StatusName = StatusName.NoEmail
  val oldVersion: StatusName = StatusName.OldVersion
  val reOptInModified: StatusName = StatusName.ReOptInModified

  given statusNameReads: Reads[StatusName] = Reads {
    case JsString(value) =>
      val pascalCaseValue = snakeToPascalCase(value)
      StatusName.values.find(_.toString == pascalCaseValue) match {
        case Some(statusName) => JsSuccess(statusName)
        case None             => JsError(s"error.expected.validenumvalue")
      }
    case _ => JsError("Expected StatusName as JsString")
  }

  given statusNameWrites: Writes[StatusName] = Writes { statusName =>
    JsString(pascalToSnakeCase(statusName.toString))
  }

  given statusNameFormat: Format[StatusName] = new Format[StatusName] {
    override def reads(json: JsValue): JsResult[StatusName] = statusNameReads.reads(json)

    override def writes(statusName: StatusName): JsValue = statusNameWrites.writes(statusName)
  }

}

enum Category {
  case ActionRequired
  case Info
  case ReOptInRequired
  case OptInRequired
}

object Category {
  val actionRequired: Category = Category.ActionRequired
  val info: Category = Category.Info
  val reOptInRequired: Category = Category.ReOptInRequired
  val optInRequired: Category = Category.OptInRequired

  given categoryReads: Reads[Category] = Reads {
    case JsString(value) =>
      val pascalCaseValue = snakeToPascalCase(value)
      Category.values.find(_.toString == pascalCaseValue) match
        case Some(category) => JsSuccess(category)
        case None           => JsError(s"error.expected.validenumvalue")
    case _ => JsError("Expected Category as JsString")
  }

  given categoryWrites: Writes[Category] = Writes { category =>
    JsString(pascalToSnakeCase(category.toString))
  }

  given categoryFormat: Format[Category] = new Format[Category] {
    override def reads(json: JsValue): JsResult[Category] = categoryReads.reads(json)

    override def writes(lang: Category): JsValue = categoryWrites.writes(lang)
  }

}

case class PaperlessStatus(
  name: StatusName,
  category: Category,
  reoptinMajor: Option[Int] = None
)

object PaperlessStatus {
  implicit val formats: OFormat[PaperlessStatus] = Json.format[PaperlessStatus]
  def apply(
    response: PreferenceResponse,
    credentials: Option[Credentials],
    reoptinMajor: Int,
    gracePeriod: Int
  ): PaperlessStatus = {

    val genericTerms = response.termsAndConditions.get("generic")
    val genericTermsAccepted = genericTerms.fold(false)(_.accepted)
    val majorVersion = genericTerms.flatMap(_.majorVersion).getOrElse(reoptinMajor)
    val versionBehind = majorVersion < reoptinMajor

    def triggerReOptIn(emailPreference: EmailPreference, isPaperless: Boolean) = {
      val noPendingEmail = emailPreference.pendingEmail.isEmpty
      versionBehind && noPendingEmail && isPaperless
    }

    (response.email, genericTermsAccepted) match {
      case (Some(emailPreference), true) if emailPreference.hasBounces =>
        val noPendingEmail = emailPreference.pendingEmail.isEmpty
        if (noPendingEmail && versionBehind) {
          PaperlessStatus(ReOptInModified, ReOptInRequired, reoptinMajor = Some(reoptinMajor))
        } else {
          PaperlessStatus(BouncedEmail, ActionRequired)
        }
      case (Some(emailPreference), true) if !emailPreference.isVerified =>
        PaperlessStatus(EmailNotVerified, ActionRequired)

      case (None, true) => PaperlessStatus(NoEmail, ActionRequired)
      case (Some(emailPreference), true) =>
        val doTriggerReOptIn = {
          for {
            tc <- genericTerms
            p  <- tc.paperless
          } yield credentials.map(_ => triggerReOptIn(emailPreference, p))
        }.flatten.getOrElse(false)
        if (doTriggerReOptIn)
          PaperlessStatus(OldVersion, ReOptInRequired, reoptinMajor = Some(reoptinMajor))
        else
          PaperlessStatus(Alright, Info)
      case (_, false) =>
        genericTerms
          .map(_.updatedAt)
          .collect {
            case Some(at) if at.plus(gracePeriod, ChronoUnit.MINUTES).isAfter(Dc.instantNow()) =>
              PaperlessStatus(Paper, Info)
          }
          .getOrElse(PaperlessStatus(Paper, OptInRequired))
      case _ => PaperlessStatus(NewCustomer, Info)
    }
  }
}
case class PreferenceResponse(
  termsAndConditions: Map[String, AcceptanceResponse],
  email: Option[EmailPreference],
  digital: Boolean = false,
  entityId: Option[EntityId] = None,
  language: Option[Language] = None,
  status: Option[PaperlessStatus] = None,
  surveys: Option[List[Survey]] = None
)

object PreferenceResponse {

  private final val surveyReads: Reads[Survey] = (
    (JsPath \ "surveyType").read[String] and
      (JsPath \ "completedAt" \ "$date")
        .read[Long]
  )(Survey.create _)

  // Mongo date field has leaked out of the API, so to avoid breaking consumers,
  // this will reformat the date like the reactive mongo API
  private final val surveyWrites: Writes[Survey] = (survey: Survey) =>
    Json.obj(
      "surveyType" -> survey.surveyType,
      "completedAt" -> Json.obj(
        "$date" -> survey.completedAt.toEpochMilli
      )
    )
  implicit val dateFormatDefault: Format[Instant] = DateFormats.instantFormats
//  implicit val dateFormatDefault = new Format[DateTime] {
//    override def reads(json: JsValue): JsResult[Instant] = JodaReads.DefaultJodaDateTimeReads.reads(json)
//    override def writes(o: DateTime): JsValue = JodaWrites.JodaDateTimeNumberWrites.writes(o)
//  }

  implicit val surveyFormat: Format[Survey] = Format(surveyReads, surveyWrites)
  implicit val formats: OFormat[PreferenceResponse] = Json.format[PreferenceResponse]

  def from(paperlessPreference: Preferences, @unused gracePeriod: Int): PreferenceResponse = {
    val isPaperless = Some(paperlessPreference.isPaperless)

    val termsAndConditions: Map[String, AcceptanceResponse] = paperlessPreference.termsAndConditions.asMap().collect {
      case (key, Accepted(dateTime, eventType, optInPage)) =>
        (
          key,
          AcceptanceResponse(
            accepted = true,
            Some(dateTime),
            optInPage.map(_.version.major),
            isPaperless,
            eventType,
            optInPage.map(_.pageType.isMobile)
          )
        )
      case (key, Refused(dateTime, eventType, optInPage)) =>
        (
          key,
          AcceptanceResponse(
            accepted = false,
            Some(dateTime),
            optInPage.map(_.version.major),
            isPaperless,
            eventType,
            optInPage.map(_.pageType.isMobile)
          )
        )
    }

    val bounceCount = paperlessPreference.email.map(_.bounceCount).getOrElse(0)

    val email: Option[EmailPreference] = {
      val maybeEmailRepoPreference = paperlessPreference.mostRecentlyAddedEmail
      val verifiedOn = paperlessPreference.email.flatMap(_.verifiedOn)
      maybeEmailRepoPreference.map { emailAddress =>
        val isBounced = emailAddress.status == Status.bounced
        val isVerified = emailAddress.status == Status.verified
        EmailPreference(
          emailAddress.email,
          isVerified,
          isBounced,
          emailAddress.mailboxFull,
          emailAddress.linkSent,
          verifiedOn,
          bounceCount,
          emailAddress.language,
          paperlessPreference.pendingEmail.map(_.email)
        )
      }
    }

    val isDigital = termsAndConditions.get("generic").fold(false)(_.accepted)
    PreferenceResponse(termsAndConditions, email, digital = isDigital, surveys = paperlessPreference.surveys)
  }

  def fromPreferences(paperlessPreferences: Seq[Preferences], gracePeriod: Int): Seq[PreferenceResponse] =
    paperlessPreferences.map(preference =>
      from(preference, gracePeriod = gracePeriod).copy(entityId = Some(preference.entityId))
    )

  def withStatus(
    response: PreferenceResponse,
    credentials: Option[Credentials],
    reoptinMajor: Int,
    gracePeriod: Int
  ): PreferenceResponse = {
    val status = PaperlessStatus(response, credentials, reoptinMajor, gracePeriod)
    response.copy(status = Some(status))
  }
}
