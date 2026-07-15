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
