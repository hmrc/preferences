/*
 * Copyright 2025 HM Revenue & Customs
 *
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
