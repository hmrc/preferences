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
import play.api.libs.json._
import uk.gov.hmrc.preferences.model.TermsAndConditions.{ Acceptance, Accepted, Refused, Unknown }
import uk.gov.hmrc.preferences.util.Dc

class TermsAndConditionsSpec extends PlaySpec {

  "Converting Acceptance instances" should {
    "yield the same value round-trip" in {
      roundTrip[Acceptance](Accepted(Dc.instantNow())) must be(true)
      roundTrip[Acceptance](Refused(Dc.instantNow())) must be(true)
    }
  }

  "Converting TermsAndConditions instances" should {
    "yield the same value round-trip" in {

      val termsAndConditions = for {
        generic <- Seq(Accepted(Dc.instantNow()), Refused(Dc.instantNow()), Unknown)
      } yield TermsAndConditions(generic)

      termsAndConditions.foreach(tc => roundTrip[TermsAndConditions](tc) mustBe true)
    }
  }

  def roundTrip[A: Format](a: A) = Json.fromJson[A](Json.toJson(a)).get === a
}
