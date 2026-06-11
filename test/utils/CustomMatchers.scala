/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package utils

import org.scalatest.matchers.{ MatchResult, Matcher }
import uk.gov.hmrc.paperless.controllers.model.AcceptanceResponse

import java.time.Instant

trait CustomMatchers {

  class TermsAndConditionsMatcher(expectedTermsAndConditions: Map[String, Boolean], beforeDateTime: Instant)
      extends Matcher[Map[String, AcceptanceResponse]] {
    def apply(termsAndConditions: Map[String, AcceptanceResponse]) = {
      val result = (for {
        key <- expectedTermsAndConditions.keySet
        keyCheck = termsAndConditions.keySet.contains(key)
        acceptCheck = termsAndConditions(key).accepted == expectedTermsAndConditions(key).booleanValue
        dateCheck = termsAndConditions(key).updatedAt.get.isAfter(beforeDateTime)
      } yield keyCheck && acceptCheck && dateCheck).forall(_.booleanValue == true)

      MatchResult(result, s"$termsAndConditions did not match expected", s"$termsAndConditions did not match expected")
    }
  }

  def matchPreferenceResponse(expectedTermsAndConditions: Map[String, Boolean], beforeDateTime: Instant) =
    new TermsAndConditionsMatcher(expectedTermsAndConditions, beforeDateTime)
}

object CustomMatchers extends CustomMatchers
