/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
