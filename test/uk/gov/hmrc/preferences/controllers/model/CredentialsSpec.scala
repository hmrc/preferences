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

package uk.gov.hmrc.preferences.controllers.model

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{ JsResultException, Json }
import uk.gov.hmrc.auth.core.AffinityGroup.Individual
import uk.gov.hmrc.auth.core.ConfidenceLevel.L600
import uk.gov.hmrc.auth.core.{ AffinityGroup, ConfidenceLevel }

class CredentialsSpec extends PlaySpec {

  "credentialsFormat" should {
    import Credentials.credentialsFormat

    "read the json correctly" in new Setup {
      Json.parse(credentialsJsonString).as[Credentials] mustBe credentials
    }

    "throw exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(credentialsInvalidJsonString).as[Credentials]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(credentials) mustBe Json.parse(credentialsJsonString)
    }
  }

  trait Setup {
    val credentials: Credentials = Credentials(affinityGroup = Some(Individual), confidenceLevel = L600)

    val credentialsJsonString: String = """{"affinityGroup":"Individual","confidenceLevel":600}""".stripMargin
    val credentialsInvalidJsonString: String = """{"affinityGroup":"Individual"}""".stripMargin
  }
}
