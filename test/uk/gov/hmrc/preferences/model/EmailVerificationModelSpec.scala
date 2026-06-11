/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.model

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{ Format, JsString, Json }

class EmailVerificationModelSpec extends PlaySpec:
  implicit val xxx: Format[EmailVerification] = EmailVerification.given_Format_EmailVerification
  "The serialiser" should {
    "serialise an emailverification object" in {
      val ev = EmailVerification(VerifyStatus.AlreadyVerified, "Already verified")
      val js = Json.toJson(ev)

      (js \ "verifyStatus").as[String] mustBe "already_verified"
      (js \ "description").as[String] mustBe "Already verified"
    }

    "deserialise an emailverification object" in {
      val js = """{
        "verifyStatus": "invalid_token",
        "description": "An invalid token"
      }"""
      val ev: EmailVerification = Json.fromJson(Json.parse(js)).get

      ev.verifyStatus mustBe (VerifyStatus.InvalidToken)
      ev.description mustBe "An invalid token"
    }
  }
