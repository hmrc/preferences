/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.model

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{ JsResultException, Json }
import utils.TestData.{ TEST_FORENAME, TEST_SUR_NAME }

class UserNameSpec extends PlaySpec {

  "userNameFormat" should {
    import UserName.userNameFormat

    "read the json correctly" in new Setup {
      Json.parse(userNameJsonString).as[UserName] mustBe userName
    }

    "throw the exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(userNameInvalidJsonString).as[UserName]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(userName) mustBe Json.parse(userNameJsonString)
    }
  }

  trait Setup {
    val userName: UserName = UserName(name = Some(TEST_FORENAME), lastName = Some(TEST_SUR_NAME))

    val userNameJsonString: String = """{"name":"test_forename","lastName":"test_last_name"}""".stripMargin

    val userNameInvalidJsonString: String = """{"lastName":5}""".stripMargin
  }
}
