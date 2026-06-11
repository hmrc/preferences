/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.model

import play.api.libs.json.{ Format, JsError, JsResult, JsString, JsSuccess, JsValue, Json }

case class EmailVerification(
  verifyStatus: VerifyStatus,
  description: String,
  returnLinkText: Option[String] = None, // These are the correct Keys to return
  returnUrl: Option[String] = None
)

object EmailVerification:
  given Format[EmailVerification] = Json.format[EmailVerification]

enum VerifyStatus:
  case Success // 202 (links), 204 (no links)
  case AlreadyVerified // 400 BAD_REQUEST("Token $token already verified") => 200
  case AlreadyVerifiedLinks // 412 PRECONDITION_FAILED("Token $token already verified") => 200
  case NotFound // 404
  case InvalidToken // 400 BAD_REQUEST("Token $invalidToken is not a valid format")
  case ExpiredToken // 410 GONE("Email verification link has expired")
  case Error

object VerifyStatus:
  private final val SUCCESS: String = "success"
  private final val ALREADY_VERIFIED: String = "already_verified"
  private final val ALREADY_VERIFIED_LINKS: String = "already_verified_links"
  private final val NOT_FOUND: String = "not_found"
  private final val INVALID_TOKEN: String = "invalid_token"
  private final val EXPIRED_TOKEN: String = "expired_token"
  private final val ERROR: String = "error"

  given Format[VerifyStatus] = new Format[VerifyStatus]:
    def writes(status: VerifyStatus): JsValue = status match
      case Success              => JsString(SUCCESS)
      case AlreadyVerified      => JsString(ALREADY_VERIFIED)
      case AlreadyVerifiedLinks => JsString(ALREADY_VERIFIED_LINKS)
      case NotFound             => JsString(NOT_FOUND)
      case InvalidToken         => JsString(INVALID_TOKEN)
      case ExpiredToken         => JsString(EXPIRED_TOKEN)
      case Error                => JsString(ERROR)

    def reads(json: JsValue): JsResult[VerifyStatus] = json match
      case JsString(SUCCESS)                => JsSuccess(Success)
      case JsString(ALREADY_VERIFIED)       => JsSuccess(AlreadyVerified)
      case JsString(ALREADY_VERIFIED_LINKS) => JsSuccess(AlreadyVerifiedLinks)
      case JsString(NOT_FOUND)              => JsSuccess(NotFound)
      case JsString(INVALID_TOKEN)          => JsSuccess(InvalidToken)
      case JsString(EXPIRED_TOKEN)          => JsSuccess(ExpiredToken)
      case JsString(ERROR)                  => JsSuccess(Error)
      case _                                => JsError("Invalid status")
