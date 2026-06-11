/*
 * Copyright 2025 HM Revenue & Customs
 *
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
