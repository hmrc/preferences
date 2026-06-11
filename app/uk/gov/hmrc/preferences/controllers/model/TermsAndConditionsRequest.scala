/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.controllers.model

import play.api.libs.functional.syntax.*
import play.api.libs.json.{ Json, OWrites, Reads, __ }
import uk.gov.hmrc.http.BadRequestException
import uk.gov.hmrc.preferences.model.Event.*
import uk.gov.hmrc.preferences.model.OptEventType.{ AdminOptOut, CustomerOptOut, CustomerReOptOut, OptIn, ReOptIn }
import uk.gov.hmrc.preferences.model.PageType.{ AndroidOptInPage, AndroidOptOutPage, AndroidReOptInPage, AndroidReOptOutPage, CYSConfirmPage, IPage, IosOptInPage, IosOptOutPage, IosReOptInPage, IosReOptOutPage, ReOptInPage }
import uk.gov.hmrc.preferences.model.{ Language, OptEventType, OptInPage, SurveyType }

case class TermsAndConditionsRequest(
  generic: Option[TermsAndConditionsRequest.Acceptance],
  email: Option[String],
  returnText: Option[String],
  returnUrl: Option[String],
  language: Option[Language] = None,
  journey: Option[String] = None
)

object TermsAndConditionsRequest {

  private val badRequestErrorMsg = "Input json is not valid to create TermsAndConditionsRequest"

  sealed trait Acceptance

  case class UserAcceptance(
    accepted: Boolean,
    optInPage: Option[OptInPage] = None,
    surveyType: Option[SurveyType] = None
  ) extends Acceptance

  case object ManualOptOut extends Acceptance {
    val reason: String = "Manual Opt Out"
  }

  implicit val acceptanceReads: Reads[Acceptance] = (
    (__ \ "manualOptOut").readNullable[Boolean] and
      (__ \ "accepted").readNullable[Boolean] and
      (__ \ "optInPage").readNullable[OptInPage] and
      (__ \ "surveyType").readNullable[SurveyType]
  ) { (manualOptOut, accepted, optInPage, surveyType) =>
    (manualOptOut, accepted, optInPage) match {
      case (Some(true), Some(false), _)    => ManualOptOut
      case (None, Some(accept), optInPage) => UserAcceptance(accept, optInPage, surveyType)
      case _                               => throw new BadRequestException("Insufficient information")
    }
  }

  implicit val genericTermsAndConditionsUpdateReads: Reads[TermsAndConditionsRequest] =
    ((__ \ "generic").readNullable[Acceptance] and
      (__ \ "returnText").readNullable[String] and
      (__ \ "returnUrl").readNullable[String] and
      (__ \ "language").readNullable[Language] and
      (__ \ "journey").readNullable[String] and
      (__ \ "email").readNullable[String]) { (genericTerms, returnText, returnUrl, language, journey, email) =>
      (genericTerms, language) match {
        case (Some(_), _) | (None, _) | (None, _) =>
          val tcRequest = TermsAndConditionsRequest
            .apply(genericTerms, email, returnText, returnUrl, language, journey)

          (tcRequest.generic.flatMap(eventType), tcRequest.language) match {
            case (Some(OptIn), None) =>
              throw new BadRequestException("missing language in OptIn request")

            case (Some(ReOptIn), None) =>
              throw new BadRequestException("missing language in ReOptIn request")

            case (Some(CustomerOptOut), None) =>
              throw new BadRequestException("missing language in CustomerOptOut request")

            case (Some(CustomerReOptOut), None) =>
              throw new BadRequestException("missing language in CustomerReOptOut request")

            case _ => tcRequest
          }
        case _ => throw new BadRequestException(badRequestErrorMsg)
      }
    }

  def eventType(acceptance: Acceptance): Option[OptEventType] =
    acceptance match {
      case ManualOptOut                                                            => Some(AdminOptOut)
      case UserAcceptance(true, Some(OptInPage(_, _, IPage)), _)                   => Some(OptIn)
      case UserAcceptance(true, Some(OptInPage(_, _, ReOptInPage)), _)             => Some(ReOptIn)
      case UserAcceptance(false, Some(OptInPage(_, _, IPage | CYSConfirmPage)), _) => Some(CustomerOptOut)
      case UserAcceptance(false, Some(OptInPage(_, _, ReOptInPage)), _)            => Some(CustomerReOptOut)
      case UserAcceptance(true, Some(OptInPage(_, _, AndroidOptInPage)), _)        => Some(OptIn)
      case UserAcceptance(false, Some(OptInPage(_, _, AndroidOptOutPage)), _)      => Some(CustomerOptOut)
      case UserAcceptance(true, Some(OptInPage(_, _, AndroidReOptInPage)), _)      => Some(ReOptIn)
      case UserAcceptance(false, Some(OptInPage(_, _, AndroidReOptOutPage)), _)    => Some(CustomerReOptOut)
      case UserAcceptance(true, Some(OptInPage(_, _, IosOptInPage)), _)            => Some(OptIn)
      case UserAcceptance(false, Some(OptInPage(_, _, IosOptOutPage)), _)          => Some(CustomerOptOut)
      case UserAcceptance(true, Some(OptInPage(_, _, IosReOptInPage)), _)          => Some(ReOptIn)
      case UserAcceptance(false, Some(OptInPage(_, _, IosReOptOutPage)), _)        => Some(CustomerReOptOut)
      case _                                                                       => None
    }

}
