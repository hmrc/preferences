/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.service

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{ times, verify, when }
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.preferences.controllers.model.{ Credentials, TermsAndConditionsRequest }
import uk.gov.hmrc.preferences.model.{ EntityId, Language, OptInBundle, SurveyType }
import uk.gov.hmrc.preferences.repository.{ NoEmailForPreference, PreferenceUpdateResult, PreferenceUpdated }

import scala.concurrent.Future

class TermsAndConditionsServiceSpec extends PlaySpec with ScalaFutures {

  "Terms and conditions service" should {
    implicit val hc = HeaderCarrier()

    "save generic terms as a manual acceptance" in new TestCase {
      val entityId = EntityId("entity-id")
      val termsAndConditionsRequest = TermsAndConditionsRequest(
        generic = Some(TermsAndConditionsRequest.ManualOptOut),
        email = Some("email"),
        returnText = Some("return-text"),
        returnUrl = Some("return-url"),
        language = Some(Language.English)
      )

      when(
        mockOptOutService.optOutOfDigital(
          any[EntityId],
          any[Option[String]],
          any[String],
          any[Option[Credentials]],
          any[OptInBundle],
          any[Option[Language]],
          any[Option[SurveyType]]
        )(any[HeaderCarrier])
      )
        .thenReturn(Future.successful(PreferenceUpdated))

      val result: PreferenceUpdateResult =
        termsAndConditionsService.handleTermsAndConditionsRequest(entityId, termsAndConditionsRequest, None).futureValue
      result must be(PreferenceUpdated)
    }

    "return no email for preference" in new TestCase {
      val entityId = EntityId("entity-id")

      // UserAcceptance/True means opt in
      val terms = TermsAndConditionsRequest.UserAcceptance(accepted = true, None, None)

      val termsAndConditionsRequest = TermsAndConditionsRequest(
        generic = Some(terms), // None,
        email = None,
        returnText = None,
        returnUrl = None,
        language = Some(Language.English)
      )

      val result =
        termsAndConditionsService.handleTermsAndConditionsRequest(entityId, termsAndConditionsRequest, None).futureValue
      result must be(NoEmailForPreference)
    }

    "set language" in new TestCase {
      implicit val hc: HeaderCarrier = HeaderCarrier()

      val entityId = EntityId("entity-id")
      val termsAndConditionsRequest = TermsAndConditionsRequest(
        generic = None,
        email = Some("email"),
        returnText = Some("return-text"),
        returnUrl = Some("return-url"),
        language = None
      )

      when(mockOptInService.setLanguage(any[EntityId], any[Option[Language]]))
        .thenReturn(Future.successful(PreferenceUpdated))

      val result =
        termsAndConditionsService.handleTermsAndConditionsRequest(entityId, termsAndConditionsRequest, None).futureValue
      result must be(PreferenceUpdated)
      verify(mockOptInService, times(1)).setLanguage(any[EntityId], any[Option[Language]])
    }
  }
  trait TestCase {
    val mockOptInService = mock[OptInService]
    val mockOptOutService = mock[OptOutService]

    lazy val termsAndConditionsService: TermsAndConditionsService = new TermsAndConditionsService(
      optInService = mockOptInService,
      optOutService = mockOptOutService
    )
  }

}
