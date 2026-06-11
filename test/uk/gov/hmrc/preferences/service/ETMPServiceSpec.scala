/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.service

import org.scalatestplus.mockito.MockitoSugar
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.preferences.connector.{ ChannelPreferencesConnector, EntityResolverConnector }
import uk.gov.hmrc.preferences.model.{ EntityId, TaxId }
import utils.GenerateRandom

import scala.concurrent.Future

class ETMPServiceSpec extends PlaySpec with MockitoSugar with ScalaFutures with IntegrationPatience {

  "ETMPService" should {

    "return ETMPUpdateSuccessful on getting successful response from ChannelPreferences " in new TestCase {
      val entityId = GenerateRandom.entityId()
      val taxId = TaxId(entityId.value, None, None, hmrcMtdItsa = Some("test-itsa-id"))

      when(mockEntityResolver.getTaxIdOption(any[EntityId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(taxId)))
      when(
        mockChannelPreferences.updatePreferencesForItsa(any[TaxId], any[Boolean], any[Option[String]])(
          any[HeaderCarrier]
        )
      )
        .thenReturn(Future.successful(true))

      etmpService.checkAndUpdateETMP(entityId, true, None).futureValue must be(ETMPUpdateSuccess)
    }

    "return ETMPUpdateFailure on getting error/failure response from ChannelPreferences " in new TestCase {
      val entityId = GenerateRandom.entityId()
      val taxId = TaxId(entityId.value, None, None, hmrcMtdItsa = Some("test-itsa-id"))

      when(mockEntityResolver.getTaxIdOption(any[EntityId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(taxId)))
      when(
        mockChannelPreferences.updatePreferencesForItsa(any[TaxId], any[Boolean], any[Option[String]])(
          any[HeaderCarrier]
        )
      )
        .thenReturn(Future.successful(false))

      etmpService.checkAndUpdateETMP(entityId, true, None).futureValue must be(ETMPUpdateFailure)
    }

    "return ETMPUpdateNotRequired when no itsa-id present in the taxId " in new TestCase {
      val entityId = GenerateRandom.entityId()
      val taxId = TaxId(entityId.value, Some(GenerateRandom.nino().value), None, hmrcMtdItsa = None)

      when(mockEntityResolver.getTaxIdOption(any[EntityId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(taxId)))

      etmpService.checkAndUpdateETMP(entityId, true, None).futureValue must be(ETMPUpdateNotRequired)
    }

    "return TaxIdFetchFailed when getTaxId returns None" in new TestCase {
      val entityId = GenerateRandom.entityId()

      when(mockEntityResolver.getTaxIdOption(any[EntityId])(any[HeaderCarrier])).thenReturn(Future.successful(None))

      etmpService.checkAndUpdateETMP(entityId, true, None).futureValue must be(TaxIdFetchFailed)
    }
  }

  trait TestCase {

    import scala.concurrent.ExecutionContext.Implicits.global

    implicit val hc: HeaderCarrier = HeaderCarrier()

    val mockEntityResolver: EntityResolverConnector = mock[EntityResolverConnector]
    val mockChannelPreferences: ChannelPreferencesConnector = mock[ChannelPreferencesConnector]
    val etmpService = new ETMPService(mockEntityResolver, mockChannelPreferences)
  }
}
