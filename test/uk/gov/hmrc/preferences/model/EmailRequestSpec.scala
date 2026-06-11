/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.model

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{ JsResultException, Json }
import utils.TestData.TEST_EMAIL

class EmailRequestSpec extends PlaySpec {

  "formats" should {
    "read the valid json" in new Setup {
      Json.parse(emailReqJsonString).as[EmailRequest] mustBe emailReq
    }

    "throw exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(emailReqInvalidJsonString).as[EmailRequest]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(emailReq) mustBe Json.parse(emailReqJsonString)
    }
  }

  trait Setup {
    val emailReq: EmailRequest = EmailRequest(TEST_EMAIL)

    val emailReqJsonString: String = """{"email":"test@test.com"}""".stripMargin
    val emailReqInvalidJsonString: String = """{}""".stripMargin
  }
}
