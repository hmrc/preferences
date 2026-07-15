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
import utils.TestData.{ TEST_FORENAME, TEST_TITLE }

class NameFromHodsSpec extends PlaySpec {

  "NameFromHods.formats" should {
    import NameFromHods.format

    "read the json correctly" in new Setup {
      Json.parse(nameFromHodsJsonString).as[NameFromHods] mustBe nameFromHods
    }

    "throw exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(nameFromHodsInvalidJsonString).as[NameFromHods]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(nameFromHods) mustBe Json.parse(nameFromHodsJsonString)
    }
  }

  trait Setup {
    val taxPayerName: TaxpayerName = TaxpayerName(title = Some(TEST_TITLE), forename = Some(TEST_FORENAME))
    val nameFromHods: NameFromHods = NameFromHods(name = Some(taxPayerName))

    val nameFromHodsJsonString: String = """{"name":{"title":"test_title","forename":"test_forename"}}""".stripMargin
    val nameFromHodsInvalidJsonString: String =
      """{"name":{"title":100,"forename":"test_forename"}}""".stripMargin
  }
}
