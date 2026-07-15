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
import utils.TestData.{ TEST_FORENAME, TEST_HONOURS, TEST_LINE_1, TEST_LINE_2, TEST_LINE_3, TEST_SECOND_NAME, TEST_SUR_NAME, TEST_TITLE }

class TaxpayerNameSpec extends PlaySpec {

  "formats" should {
    "read the json correctly" in new Setup {
      Json.parse(taxpayerNameJsonString).as[TaxpayerName] mustBe taxpayerName
    }

    "throw the exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(taxpayerNameInvalidJsonString).as[TaxpayerName]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(taxpayerName) mustBe Json.parse(taxpayerNameJsonString)
    }
  }

  trait Setup {
    val taxpayerName: TaxpayerName = TaxpayerName(
      title = Some(TEST_TITLE),
      forename = Some(TEST_FORENAME),
      secondForename = Some(TEST_SECOND_NAME),
      surname = Some(TEST_SUR_NAME),
      honours = Some(TEST_HONOURS),
      line1 = Some(TEST_LINE_1),
      line2 = Some(TEST_LINE_2),
      line3 = Some(TEST_LINE_3)
    )

    val taxpayerNameJsonString: String =
      """{
        |"title":"test_title",
        |"forename":"test_forename",
        |"secondForename":"test_second_name",
        |"surname":"test_last_name",
        |"honours":"test_honours",
        |"line1":"test_line1",
        |"line2":"test_line2",
        |"line3":"test_line3"
        |}""".stripMargin

    val taxpayerNameInvalidJsonString: String =
      """{
        |"title":"test_title",
        |"forename":"test_forename",
        |"secondForename":"test_second_name",
        |"surname":"test_last_name",
        |"honours":"test_honours",
        |"line1":5,
        |"line2":165,
        |"line3":"test_line3"
        |}""".stripMargin
  }
}
