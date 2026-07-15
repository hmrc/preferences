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

import org.bson.types.ObjectId
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{ doNothing, verify, when }
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.concurrent.{ Eventually, ScalaFutures }
import org.scalatestplus.play.PlaySpec
import play.api.http.Status.{ INTERNAL_SERVER_ERROR, OK }
import play.api.test.Helpers.*
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse, UpstreamErrorResponse }
import uk.gov.hmrc.preferences.Auditable
import uk.gov.hmrc.preferences.connector.{ EntityResolverConnector, PreferencesChangedNotifierConnector }
import uk.gov.hmrc.preferences.controllers.model.PreferencesChangedRequest
import uk.gov.hmrc.preferences.exceptions.EntityTaxIdLookupException
import uk.gov.hmrc.preferences.model.MessageDeliveryFormat.{ Digital, Paper }
import uk.gov.hmrc.preferences.model.{ EntityId, P2Bounced, TaxId }
import utils.TestData.TEST_ERROR_MESSAGE

import scala.concurrent.{ ExecutionContext, Future }

class PreferencesChangedNotifierServiceSpec extends PlaySpec with ScalaFutures with Eventually with MockitoSugar {

  "Calling notifyPreferencesChanged" should {

    "notify correctly when supplied with a nino" in new TestCase {
      val svc: PreferencesChangedNotifierService = createNotifier()
      val pcr: ArgumentCaptor[PreferencesChangedRequest] = ArgumentCaptor.forClass(classOf[PreferencesChangedRequest])

      when(mockErConnector.getTaxId(any[EntityId])(any[HeaderCarrier])).thenReturn(
        Future.successful(TaxId(_id = "123", nino = Option("AB112233A"), sautr = None))
      )
      when(mockPcnConnector.preferencesChanged(pcr.capture())(any[HeaderCarrier])).thenReturn(
        Future.successful(HttpResponse(OK, ""))
      )

      svc.notifyPreferencesChanged(new ObjectId(), EntityId("1111"), Digital).futureValue

      verify(mockErConnector).getTaxId(any[EntityId])(any[HeaderCarrier])
      verify(mockPcnConnector).preferencesChanged(any[PreferencesChangedRequest])(any[HeaderCarrier])
      pcr.getValue.bounced mustBe false
    }

    "continue when not supplied with a taxid" in new TestCase {
      val svc: PreferencesChangedNotifierService = createNotifier()
      when(mockErConnector.getTaxId(any[EntityId])(any[HeaderCarrier])).thenReturn(
        Future.successful(TaxId(_id = "123", nino = None, sautr = None))
      )
      when(mockPcnConnector.preferencesChanged(any[PreferencesChangedRequest])(any[HeaderCarrier])).thenReturn(
        Future.successful(HttpResponse(OK, ""))
      )

      svc.notifyPreferencesChanged(new ObjectId(), EntityId("1111"), Digital).futureValue

      verify(mockErConnector).getTaxId(any[EntityId])(any[HeaderCarrier])
      verify(mockPcnConnector).preferencesChanged(any[PreferencesChangedRequest])(any[HeaderCarrier])
    }

    "notify correctly when supplied with a matching nino & form type P2 " in new TestCase {
      val svc: PreferencesChangedNotifierService = createNotifier()
      val pcr: ArgumentCaptor[PreferencesChangedRequest] = ArgumentCaptor.forClass(classOf[PreferencesChangedRequest])
      when(mockErConnector.getTaxId(any[EntityId])(any[HeaderCarrier])).thenReturn(
        Future.successful(TaxId(_id = "123", nino = Option("AB112233A"), sautr = None))
      )
      when(mockPcnConnector.preferencesChanged(pcr.capture())(any[HeaderCarrier])).thenReturn(
        Future.successful(HttpResponse(OK, ""))
      )

      svc
        .notifyPreferencesChanged(
          new ObjectId(),
          EntityId("1111"),
          Paper,
          false,
          Some(P2Bounced(Some("P2"), Some("AB112233A")))
        )
        .futureValue

      verify(mockPcnConnector).preferencesChanged(any[PreferencesChangedRequest])(any[HeaderCarrier])
      pcr.getValue.bounced mustBe true
    }

    "throw EntityTaxIdLookupException when entity resolver connector receives a upstream error response" in new TestCase {
      intercept[EntityTaxIdLookupException] {
        val svc: PreferencesChangedNotifierService = createNotifier()

        when(mockErConnector.getTaxId(any[EntityId])(any[HeaderCarrier])).thenReturn(
          Future.failed(UpstreamErrorResponse(TEST_ERROR_MESSAGE, INTERNAL_SERVER_ERROR))
        )

        doNothing().when(mockAuditable).sendDataEvent(any, any, any, any)(any, any)

        await(svc.notifyPreferencesChanged(new ObjectId(), EntityId("1111"), Digital))

        verify(mockErConnector).getTaxId(any[EntityId])(any[HeaderCarrier])
      }
    }

    "throw Exception when entity resolver connector gets an unexpected error while making taxId api call" in new TestCase {
      intercept[RuntimeException] {
        val svc: PreferencesChangedNotifierService = createNotifier()

        when(mockErConnector.getTaxId(any[EntityId])(any[HeaderCarrier])).thenReturn(
          Future.failed(new RuntimeException("Unexpected error occured"))
        )

        doNothing().when(mockAuditable).sendDataEvent(any, any, any, any)(any, any)

        await(svc.notifyPreferencesChanged(new ObjectId(), EntityId("1111"), Digital))

        verify(mockErConnector).getTaxId(any[EntityId])(any[HeaderCarrier])
      }
    }
  }

  trait TestCase {
    implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val mockErConnector: EntityResolverConnector = mock[EntityResolverConnector]
    val mockPcnConnector: PreferencesChangedNotifierConnector = mock[PreferencesChangedNotifierConnector]
    val mockAuditable: Auditable = mock[Auditable]

    def createNotifier(): PreferencesChangedNotifierService =
      new PreferencesChangedNotifierService(
        entityResolverConnector = mockErConnector,
        pcnConnector = mockPcnConnector,
        auditable = mockAuditable
      )
  }
}
