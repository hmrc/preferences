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
import uk.gov.hmrc.auth.core.AffinityGroup.Agent
import uk.gov.hmrc.auth.core.ConfidenceLevel.L200

class UserTypeSpec extends PlaySpec {

  "Json Reads" should {
    import UserType.userTypeReads

    "read the json correctly" in new Setup {
      Json.parse(userTypeJsonString).as[UserType] mustBe userType
    }

    "throw the exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(userTypeInvalidJsonString).as[UserType]
      }
    }
  }

  "Json Writes" should {
    "write the object correctly" in new Setup {
      Json.toJson(userType) mustBe Json.parse(userTypeJsonString)
    }
  }

  trait Setup {
    val userType: UserType = UserType(affinityGroup = Some(Agent), confidenceLevel = Some(L200))

    val userTypeJsonString: String = """{"affinityGroup":"Agent","confidenceLevel":200}""".stripMargin
    val userTypeInvalidJsonString: String = """{"affinityGroup":"Unknown","confidenceLevel":200}""".stripMargin
  }
}
