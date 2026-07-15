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

package uk.gov.hmrc.preferences.model

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{ JsNumber, JsResultException, JsString, Json }
import uk.gov.hmrc.preferences.model.OptEventType.OptIn
import utils.TestData.{ TEST_EMAIL, TEST_EMAIL_ADDRESS, TEST_TIME_INSTANT }

import java.time.Instant

class EventsResponseSpec extends PlaySpec {

  "format" should {
    import EventsResponse.format

    "read the json correctly" in new Setup {
      Json.parse(eventsResponseJsonString).as[EventsResponse] mustBe eventsResponse
    }

    "throw the exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(eventsResponseInvalidJsonString).as[EventsResponse]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(eventsResponse) mustBe Json.parse(eventsResponseJsonString)
    }
  }

  trait Setup {
    val eventsResponse: EventsResponse = EventsResponse(
      eventType = OptIn,
      emailAddress = Some(TEST_EMAIL),
      timestamp = TEST_TIME_INSTANT,
      viaMobileApp = true
    )

    val eventsResponseJsonString: String =
      """{
        |"eventType":"opt-in",
        |"emailAddress":"test@test.com",
        |"timestamp":"1972-02-24T21:04:16.000Z",
        |"viaMobileApp":true
        |}""".stripMargin

    val eventsResponseInvalidJsonString: String =
      """{
        |"emailAddress":"test@test.com",
        |"timestamp":"1972-02-24T21:04:16.000Z",
        |"viaMobileApp":true
        |}""".stripMargin
  }
}
