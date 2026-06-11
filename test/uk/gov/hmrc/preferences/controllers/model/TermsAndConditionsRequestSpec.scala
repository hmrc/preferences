/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.controllers.model

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import uk.gov.hmrc.http.BadRequestException
import uk.gov.hmrc.preferences.controllers.model.TermsAndConditionsRequest.{ ManualOptOut, UserAcceptance }
import uk.gov.hmrc.preferences.model.OptEventType.{ AdminOptOut, CustomerOptOut, CustomerReOptOut, OptIn, ReOptIn }
import uk.gov.hmrc.preferences.model.PageType.CYSConfirmPage
import uk.gov.hmrc.preferences.model.{ Language, OptInPage, PageType, Version }
import utils.TestData.{ FIVE, TEST_EMAIL, TWO }

class TermsAndConditionsRequestSpec extends PlaySpec {

  "OptEventType" should {

    "return AdminOptOut eventType if adminOptOut is true" in {
      val acceptance = ManualOptOut
      TermsAndConditionsRequest.eventType(acceptance).get must be(AdminOptOut)
    }

    "return OptIn eventType if accepted on pageType IPage" in {
      val acceptance = UserAcceptance(accepted = true, Some(OptInPage(Version(1, 2), cohort = 1, PageType.IPage)))
      TermsAndConditionsRequest.eventType(acceptance).get must be(OptIn)
    }

    "return ReOptIn eventType if accepted on pageType ReOptInPage" in {
      val acceptance = UserAcceptance(accepted = true, Some(OptInPage(Version(1, 2), cohort = 1, PageType.ReOptInPage)))
      TermsAndConditionsRequest.eventType(acceptance).get must be(ReOptIn)
    }

    "return CustomerOptOut eventType if user opts out on IPage" in {
      val acceptance = UserAcceptance(accepted = false, Some(OptInPage(Version(1, 2), cohort = 1, PageType.IPage)))
      TermsAndConditionsRequest
        .eventType(acceptance)
        .get must be(CustomerOptOut)
    }

    "return CustomerOptOut eventType if user opts out on CYSConfirmPage" in {
      val acceptance =
        UserAcceptance(accepted = false, Some(OptInPage(Version(1, 0), cohort = 1, PageType.CYSConfirmPage)))
      TermsAndConditionsRequest.eventType(acceptance).get must be(CustomerOptOut)
    }

    "return CustomerReOptOut eventType if user opts out on ReOptInPage" in {
      val acceptance =
        UserAcceptance(accepted = false, Some(OptInPage(Version(1, 2), cohort = 1, PageType.ReOptInPage)))
      TermsAndConditionsRequest.eventType(acceptance).get must be(CustomerReOptOut)
    }

    "return OptIn eventType if user opts in with AndroidOptInPage pageType" in {
      val acceptance =
        UserAcceptance(accepted = true, Some(OptInPage(Version(1, 2), cohort = 1, PageType.AndroidOptInPage)))
      TermsAndConditionsRequest.eventType(acceptance).get must be(OptIn)
    }

    "return CustomerOptOut eventType if user opts in with AndroidOptOutPage pageType" in {
      val acceptance =
        UserAcceptance(accepted = false, Some(OptInPage(Version(1, 2), cohort = 1, PageType.AndroidOptOutPage)))
      TermsAndConditionsRequest.eventType(acceptance).get must be(CustomerOptOut)
    }

    "return ReOptIn eventType if user opts in with AndroidReOptInPage pageType" in {
      val acceptance =
        UserAcceptance(accepted = true, Some(OptInPage(Version(1, 2), cohort = 1, PageType.AndroidReOptInPage)))
      TermsAndConditionsRequest.eventType(acceptance).get must be(ReOptIn)
    }

    "return CustomerReOptOut eventType if user opts in with AndroidReOptOutPage pageType" in {
      val acceptance =
        UserAcceptance(accepted = false, Some(OptInPage(Version(1, 2), cohort = 1, PageType.AndroidReOptOutPage)))
      TermsAndConditionsRequest.eventType(acceptance).get must be(CustomerReOptOut)
    }

    "return OptIn eventType if user opts in with IosOptInPage pageType" in {
      val acceptance =
        UserAcceptance(accepted = true, Some(OptInPage(Version(1, 2), cohort = 1, PageType.IosOptInPage)))
      TermsAndConditionsRequest.eventType(acceptance).get must be(OptIn)
    }

    "return CustomerOptOut eventType if user opts in with IosOptOutPage pageType" in {
      val acceptance =
        UserAcceptance(accepted = false, Some(OptInPage(Version(1, 2), cohort = 1, PageType.IosOptOutPage)))
      TermsAndConditionsRequest.eventType(acceptance).get must be(CustomerOptOut)
    }

    "return ReOptIn eventType if user opts in with IosReOptInPage pageType" in {
      val acceptance =
        UserAcceptance(accepted = true, Some(OptInPage(Version(1, 2), cohort = 1, PageType.IosReOptInPage)))
      TermsAndConditionsRequest.eventType(acceptance).get must be(ReOptIn)
    }

    "return CustomerReOptOut eventType if user opts in with IosReOptOutPage pageType" in {
      val acceptance =
        UserAcceptance(accepted = false, Some(OptInPage(Version(1, 2), cohort = 1, PageType.IosReOptOutPage)))
      TermsAndConditionsRequest.eventType(acceptance).get must be(CustomerReOptOut)
    }
  }

  "genericTermsAndConditionsUpdateReads" should {
    import TermsAndConditionsRequest.{ acceptanceReads, genericTermsAndConditionsUpdateReads }

    "read the json correctly" in {
      val validOptInJsonString: String =
        """{"generic":{"accepted":true,"optInPage":{"version":{"major":2,"minor":5},"cohort":2,"pageType":"CYSConfirmPage"}},
          |"email":"test@test.com"
          |}""".stripMargin

      val termsAndConditionReq = TermsAndConditionsRequest(
        generic =
          Some(UserAcceptance(accepted = true, optInPage = Some(OptInPage(Version(TWO, FIVE), TWO, CYSConfirmPage)))),
        email = Some(TEST_EMAIL),
        returnText = None,
        returnUrl = None
      )

      Json.parse(validOptInJsonString).as[TermsAndConditionsRequest] mustBe termsAndConditionReq
    }

    "throw the exception for the invalid json" in {
      val invalidOptInJsonString: String =
        """{"generic":{"accepted":true,"optInPage":{"version":{"major":2,"minor":5},"cohort":2,"pageType":"IPage"}},
          |"email":"test@test.com"
          |}""".stripMargin

      val invalidReOptInJsonString: String =
        """{"generic":{"accepted":true,"optInPage":{"version":{"major":2,"minor":5},"cohort":2,"pageType":"ReOptInPage"}},
          |"email":"test@test.com"
          |}""".stripMargin

      val invalidCustomerOptOutJsonString: String =
        """{"generic":{"accepted":false,"optInPage":{"version":{"major":2,"minor":5},"cohort":2,"pageType":"CYSConfirmPage"}},
          |"email":"test@test.com"
          |}""".stripMargin

      val invalidCustomerReOptOutJsonString: String =
        """{"generic":{"accepted":false,"optInPage":{"version":{"major":2,"minor":5},"cohort":2,"pageType":"ReOptInPage"}},
          |"email":"test@test.com"
          |}""".stripMargin

      val invalidJsonString: String = """{"generic":5}""".stripMargin

      intercept[BadRequestException] {
        Json.parse(invalidOptInJsonString).as[TermsAndConditionsRequest]
      }

      intercept[BadRequestException] {
        Json.parse(invalidReOptInJsonString).as[TermsAndConditionsRequest]
      }

      intercept[BadRequestException] {
        Json.parse(invalidCustomerOptOutJsonString).as[TermsAndConditionsRequest]
      }

      intercept[BadRequestException] {
        Json.parse(invalidCustomerReOptOutJsonString).as[TermsAndConditionsRequest]
      }

      intercept[BadRequestException] {
        Json.parse(invalidJsonString).as[TermsAndConditionsRequest]
      }
    }
  }
}
