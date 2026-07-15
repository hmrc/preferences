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

package uk.gov.hmrc.preferences.controllers

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{ verify, when }
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.{ contentAsJson, contentAsString, defaultAwaitTimeout, status, stubControllerComponents }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.preferences.model.PageType.IPage
import uk.gov.hmrc.preferences.model.{ EntityId, EventsResponse, Language, OptEventType, OptInEvent, OptInPage, Version }
import uk.gov.hmrc.preferences.service.EventService

import java.time.Instant
import java.util.UUID
import scala.concurrent.Future

class EventsControllerSpec extends PlaySpec {
  "get events" must {
    "return event list" in new TestCase {
      val entityId = EntityId(UUID.randomUUID().toString)
      val fakeRequest = FakeRequest("GET", routes.EventsController.getEvents(entityId).url)
      val time = Instant.now()

      when(mockEventService.getEvents(any[EntityId])(any[HeaderCarrier])).thenReturn(
        Future.successful(
          Seq(
            EventsResponse(
              eventType = OptEventType.OptIn,
              emailAddress = Some("test@email.com"),
              timestamp = time
            )
          )
        )
      )

      val result = controller.getEvents(entityId)(fakeRequest)
      status(result) mustBe Status.OK
      contentAsString(result) must include("opt-in")
      contentAsString(result) must include("test@email.com")
      verify(mockEventService).getEvents(any[EntityId])(any[HeaderCarrier])

    }

    "get empty event list" in new TestCase {
      val entityId = EntityId("111")
      val fakeRequest = FakeRequest("GET", routes.EventsController.getEvents(entityId).url)

      when(mockEventService.getEvents(any[EntityId])(any[HeaderCarrier])).thenReturn(Future.successful(List.empty))
      val result = controller.getEvents(entityId)(fakeRequest)

      status(result) mustBe Status.OK
      contentAsString(result) mustBe "[]"
      verify(mockEventService).getEvents(any[EntityId])(any[HeaderCarrier])
    }

    class TestCase {
      import scala.concurrent.ExecutionContext.Implicits.global
      val mockEventService: EventService = mock[EventService]
      val controller = new EventsController(mockEventService, stubControllerComponents())
    }
  }
}
