/*
 * Copyright 2026 HM Revenue & Customs
 *
 */

package controller

import conf.{ CleanMongoCollection, ISpec }
import play.api.libs.json.Json

class EmailBounceControllerISpec extends ISpec {

  "/preferences/email/bounce endpoint" must {
    "return 201" in new TestCase {
      val result = preferencesTestRoutes.post("/preferences/email/bounce", Json.parse(validPayload))
      result.status mustBe 200

    }
  }

  class TestCase extends ISpecTestCase {
    val validPayload = """{
                         |"eventId":"1ebbc004-d2ce-11eb-b8bc-0242ac130003",
                         |"subject":"subject",
                         |"groupId":"",
                         |"timestamp":"2021-02-11T23:00:00.000Z",
                         |"event": {
                         |"status":"Failed",
                         |"emailAddress":"test@test.com",
                         |"detected":"2021-01-11T23:00:00.000Z",
                         |"code":2,
                         |"reason":"Not delivering to previously bounced address",
                         |"enrolment":"HMRC-MTD-VAT~VRN~GB123456789"
                         |}
                         |}""".stripMargin
  }

  override def cleanMongoCollection: CleanMongoCollection = app.injector.instanceOf[CleanMongoCollection]
}
