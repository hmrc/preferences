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
import utils.TestData.TEST_ENTITY_ID

class DeleteBouncesResultSpec extends PlaySpec {

  "formats" should {
    import DeleteBouncesResult.formats

    "read the json correctly" in new Setup {
      Json.parse(deleteBouncesResultJsonString).as[DeleteBouncesResult] mustBe deleteBouncesResult
      Json.parse(defaultDeleteBouncesResultJsonString).as[DeleteBouncesResult] mustBe DeleteBouncesResult()
    }

    "throw exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(deleteBouncesResultInvalidJsonString).as[DeleteBouncesResult]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(deleteBouncesResult) mustBe Json.parse(deleteBouncesResultJsonString)
      Json.toJson(DeleteBouncesResult()) mustBe Json.parse(defaultDeleteBouncesResultJsonString)
    }
  }

  trait Setup {
    val deleteBouncesResult: DeleteBouncesResult =
      DeleteBouncesResult(
        preferenceNotFound = Seq(TEST_ENTITY_ID),
        noVerifiedEmail = Seq(TEST_ENTITY_ID),
        notBounced = Seq(TEST_ENTITY_ID),
        auditFailed = Seq(TEST_ENTITY_ID),
        failed = Seq(TEST_ENTITY_ID)
      )

    val defaultDeleteBouncesResultJsonString: String =
      """{
        |"preferenceNotFound":[],
        |"noVerifiedEmail":[],
        |"notBounced":[],
        |"auditFailed":[],
        |"failed":[]
        |}""".stripMargin

    val deleteBouncesResultJsonString: String =
      """{
        |"preferenceNotFound":["test_id"],
        |"noVerifiedEmail":["test_id"],
        |"notBounced":["test_id"],
        |"auditFailed":["test_id"],
        |"failed":["test_id"]
        |}""".stripMargin

    val deleteBouncesResultInvalidJsonString: String =
      """{
        |"preferenceNotFound":"test_id",
        |"noVerifiedEmail":["test_id"],
        |"notBounced":["test_id"],
        |"auditFailed":["test_id"],
        |"failed":["test_id"]
        |}""".stripMargin
  }
}
