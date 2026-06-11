/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.controllers

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import uk.gov.hmrc.http.BadRequestException
import uk.gov.hmrc.preferences.controllers.model.TermsAndConditionsRequest
import uk.gov.hmrc.preferences.controllers.model.TermsAndConditionsRequest.{ ManualOptOut, UserAcceptance }
import uk.gov.hmrc.preferences.model.Language
import uk.gov.hmrc.preferences.model.SurveyType.StandardInterruptOptOut

class GenericTermsAndConditionsUpdateSpec extends PlaySpec {

  "GenericTermsAndConditionsUpdate" should {

    "Create a user OptIn request out of the json request with accepted = true and email" in {
      val userOptOutRequest = Json.parse(
        s"""
           |{
           |   "generic" :
           |    {
           |      "accepted": true
           |    },
           |    "email":"john@smith.co.uk",
           |    "language": "en",
           |    "affinityGroup": "individual"
           |}
         """.stripMargin
      )

      userOptOutRequest.as[TermsAndConditionsRequest] mustBe TermsAndConditionsRequest(
        Some(UserAcceptance(accepted = true)),
        Some("john@smith.co.uk"),
        None,
        None,
        Some(Language.English)
      )
    }

    "Create a user OptIn request out of the json request with accepted = true and email and return text and url" in {
      val userOptOutRequest = Json.parse(
        s"""
           |{
           |   "generic" :
           |    {
           |      "accepted": true
           |    },
           |    "returnUrl":"return URL",
           |    "returnText":"return link text",
           |    "email":"john@smith.co.uk",
           |    "language": "en",
           |    "affinityGroup": "individual"
           |}
         """.stripMargin
      )

      userOptOutRequest.as[TermsAndConditionsRequest] mustBe TermsAndConditionsRequest(
        Some(UserAcceptance(accepted = true)),
        Some("john@smith.co.uk"),
        Some("return link text"),
        Some("return URL"),
        Some(Language.English)
      )
    }

    "Create a user OptIn request out of the json request with accepted = false survey" in {
      val userOptOutRequest = Json.parse(
        s"""
           |{
           |   "generic" :
           |    {
           |      "accepted": false,
           |      "surveyType": "StandardInterruptOptOut"
           |    }
           |}
         """.stripMargin
      )

      userOptOutRequest.as[TermsAndConditionsRequest] mustBe TermsAndConditionsRequest(
        Some(UserAcceptance(accepted = false, surveyType = Some(StandardInterruptOptOut))),
        None,
        None,
        None,
        None
      )
    }

    "Create a user OptOut request out of the json request with accepted = false" in {
      val userOptOutRequest = Json.parse(
        s"""
           |{
           |   "generic" :
           |    {
           |      "accepted": false
           |    },
           |   "affinityGroup": "individual"
           |}
         """.stripMargin
      )

      userOptOutRequest.as[TermsAndConditionsRequest] mustBe TermsAndConditionsRequest(
        Some(UserAcceptance(accepted = false)),
        None,
        None,
        None,
        None
      )
    }

    "Create a manual OptOut request out of the json request with accepted = false and manualOptOut flag true" in {
      val userOptOutRequest = Json.parse(
        s"""
           |{
           |   "generic" :
           |    {
           |      "accepted": false,
           |      "manualOptOut": true
           |    },
           |    "affinityGroup": "individual"
           |}
         """.stripMargin
      )

      userOptOutRequest
        .as[TermsAndConditionsRequest] mustBe TermsAndConditionsRequest(Some(ManualOptOut), None, None, None, None)
    }

    "Throw an error when receive accepted = true and manualOptOut = true" in {
      val userOptOutRequest = Json.parse(
        s"""
           |{
           |   "generic" :
           |    {
           |      "accepted": true,
           |      "manualOptOut": true
           |    },
           |    "email":"john@smith.co.uk",
           |    "language": "en",
           |    "affinityGroup": "individual"
           |}
         """.stripMargin
      )

      intercept[BadRequestException] {
        userOptOutRequest.as[TermsAndConditionsRequest]
      }
    }
  }
}
