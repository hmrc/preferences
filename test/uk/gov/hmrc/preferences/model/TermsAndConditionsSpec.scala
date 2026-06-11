/*
 * Copyright 2025 HM Revenue & Customs
 *
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
