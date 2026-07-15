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
