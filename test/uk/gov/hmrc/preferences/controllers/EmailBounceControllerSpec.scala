/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.controllers

import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{ times, verify, when }
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import play.api.http.{ ContentTypes, Status }
import play.api.libs.json.Json
import play.api.test.Helpers.{ CONTENT_TYPE, contentAsString, defaultAwaitTimeout, status, stubControllerComponents }
import play.api.test.{ FakeHeaders, FakeRequest }
import uk.gov.hmrc.auth.core.AffinityGroup
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.preferences.Auditable
import uk.gov.hmrc.preferences.connector.Bounce
import uk.gov.hmrc.preferences.model.TermsAndConditions.Accepted
import uk.gov.hmrc.preferences.model._
import uk.gov.hmrc.preferences.service._
import uk.gov.hmrc.preferences.util.Dc

import java.time.Instant
import scala.concurrent.Future

class EmailBounceControllerSpec extends PlaySpec {

  "process" must {
    "return 200 for valid payload" in new TestCase {
      val fakeRequest = FakeRequest(
        "POST",
        routes.EmailBounceController.process.url,
        FakeHeaders(Seq(CONTENT_TYPE -> ContentTypes.JSON)),
        Json.parse(validPayload)
      )

      val result = controller.process(fakeRequest)
      status(result) mustBe Status.OK
      contentAsString(result) mustBe "Bounce processed successfully for 1ebbc004-d2ce-11eb-b8bc-0242ac130003"
      verify(emailBounceQueueMonitorServiceMock, times(1)).markAsBounced(
        ArgumentMatchers.eq[Bounce](
          Bounce("test@test.com", Instant.parse("2021-02-11T23:00:00.000Z"), Some(2), Some("preferences"), None)
        )
      )(any[HeaderCarrier])

    }

    "return BAD_REQUEST for invalid payload" in new TestCase {
      val fakeRequest = FakeRequest(
        "POST",
        routes.EmailBounceController.process.url,
        FakeHeaders(Seq(CONTENT_TYPE -> ContentTypes.JSON)),
        Json.parse(invalidPayload)
      )

      val result = controller.process(fakeRequest)
      status(result) mustBe Status.BAD_REQUEST
    }
  }
  "bounceObject" must {
    "process the tags for bounce events - P2" in new TestCase {
      val event = Json.parse(bouncePayload).as[ProcessEvent]
      val bounce = controller.bounceObject(event)
      bounce.formType mustBe Some("P2")
      bounce.nino mustBe Some("AA000003B")
    }
  }

  class TestCase {
    import scala.concurrent.ExecutionContext.Implicits.global
    val emailBounceQueueMonitorServiceMock = mock[EmailBounceQueueMonitorService]
    val auditable = mock[Auditable]
    implicit val headerCarrier: HeaderCarrier = mock[HeaderCarrier]
    val validPayload = """{
                         |"eventId":"1ebbc004-d2ce-11eb-b8bc-0242ac130003",
                         |"subject":"subject",
                         |"groupId":"",
                         |"timestamp":"2021-02-11T23:00:00.000Z",
                         |"event": {
                         |"status":"Failed",
                         |"emailAddress":"test@test.com",
                         |"detected":"2021-02-11T23:00:00.000Z",
                         |"code":2,
                         |"reason":"Not delivering to previously bounced address",
                         |"enrolment":"HMRC-MTD-VAT~VRN~GB123456789"
                         |}
                         |}""".stripMargin

    val invalidPayload = """{
                           |"eventId":"1ebbc004-d2ce-11eb-b8bc-0242ac130003",
                           |"groupId":"",
                           |"timestamp":"2021-02-11T23:00:00.000Z",
                           |"event": {
                           |"status":"Failed"
                           |}
                           |}""".stripMargin

    val termsAndConditionsAcceptedForGenericOnly: TermsAndConditions =
      TermsAndConditions(Accepted(Dc.instantNow()))
    val pendingPreferences =
      Preferences(
        entityId = EntityId("2222"),
        termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
        userType = Option(UserType(Some(AffinityGroup.Individual))),
        email = Option(EmailAddress("test@test.com"))
      )

    val bouncePayload =
      """{
        |"eventId":"1ebbc004-d2ce-11eb-b8bc-0242ac130003",
        |"subject":"subject",
        |"groupId":"",
        |"timestamp":"2021-02-11T23:00:00.000Z",
        |"event": {
        |"status":"Failed",
        |"emailAddress":"test@test.com",
        |"detected":"2021-02-11T23:00:00.000Z",
        |"code":2,
        |"reason":"Not delivering to previously bounced address",
        |"tags":{"regime":"paye","templateId":"tax_estimate_message_alert","form-type":"P2","correlationId":"a8c039b9-eb48-4bdd-bc57-fc47d476c2de","platform":"mdtp","nino":"AA000003B","senderDomain":"qa.tax.service.gov.uk"},
        |"enrolment":"HMRC-MTD-VAT~VRN~GB123456789"
        |}
        |}""".stripMargin

    when(emailBounceQueueMonitorServiceMock.markAsBounced(any[Bounce])(any[HeaderCarrier]))
      .thenReturn(Future.successful(()))
    val controller =
      new EmailBounceController(
        emailBounceQueueMonitorServiceMock,
        stubControllerComponents(),
        auditable
      )
  }
}
