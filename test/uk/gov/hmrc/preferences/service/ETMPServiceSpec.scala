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
        mockChannelPreferences.updatePreferencesForItsa(any[String], any[Boolean], any[Option[String]])(
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
        mockChannelPreferences.updatePreferencesForItsa(any[String], any[Boolean], any[Option[String]])(
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
