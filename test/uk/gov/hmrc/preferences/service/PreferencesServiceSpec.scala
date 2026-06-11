/*
 * Copyright 2024 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.service

import org.mockito.ArgumentMatchers
import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.*
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatest.matchers.should.Matchers
import org.scalatest.matchers.should.Matchers.{ shouldBe, shouldEqual }
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.preferences.exceptions.*
import uk.gov.hmrc.preferences.model.TermsAndConditions.Accepted
import uk.gov.hmrc.preferences.model.{ EntityId, Preferences, TermsAndConditions }
import uk.gov.hmrc.preferences.repository.PreferencesRepository
import uk.gov.hmrc.preferences.util.Dc
import utils.GenerateRandom

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PreferencesServiceSpec extends PlaySpec with MockitoSugar with ScalaFutures with IntegrationPatience {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  private val mockPreferencesRepository = mock[PreferencesRepository]

  private val preferenceService = new PreferenceService(mockPreferencesRepository)

  "findPreferenceByTaxId" should {

    "return the preferences when entityId is found and preferences exist" in {
      val entityId = GenerateRandom.entityId()
      val preferences =
        Preferences(
          entityId,
          termsAndConditions = TermsAndConditions(Accepted(Dc.instantNow()))
        )
      when(mockPreferencesRepository.findBy(ArgumentMatchers.eq(entityId))(any))
        .thenReturn(Future.successful(Some(preferences)))
      preferenceService.getPreferencesByEntityId(entityId).value.futureValue shouldBe Right(preferences)
    }

    "return not found when preference doesnt exist" in {
      val entityId = GenerateRandom.entityId()
      when(mockPreferencesRepository.findBy(ArgumentMatchers.eq(entityId))(any))
        .thenReturn(Future.successful(None))
      preferenceService.getPreferencesByEntityId(entityId).value.futureValue shouldBe Left(PreferenceNotFound())
    }
  }
}
