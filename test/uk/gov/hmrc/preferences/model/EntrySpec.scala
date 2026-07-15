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
import utils.TestData.{ FIVE, TEST_ID, TEST_LOCAL_DATE }

class EntrySpec extends PlaySpec {

  "Entry.formats" should {
    import Entry.formats

    "read the json correctly" in new Setup {
      Json.parse(entryJsonString).as[Entry](formats) mustBe entry
    }

    "throw exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(entryInvalidJsonString).as[Entry](formats)
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(entry)(formats) mustBe Json.parse(entryJsonString)
    }
  }

  "DatedCount.datedCountFormats" should {
    import DatedCount.datedCountFormats

    "read the json correctly" in new Setup {
      Json.parse(datedCountJsonString).as[DatedCount] mustBe datedCount
    }

    "throw exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(datedCountInvalidJsonString).as[DatedCount]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(datedCount) mustBe Json.parse(datedCountJsonString)
    }
  }

  trait Setup {
    val datedCount: DatedCount = DatedCount(count = FIVE, date = TEST_LOCAL_DATE)
    val entry: Entry = Entry(_id = TEST_ID, value = datedCount)

    val entryJsonString: String =
      """{"_id":"test_id","value":{"count":5,"date":{"$date":{"$numberLong":"1765324800000"}}}}""".stripMargin

    val entryInvalidJsonString: String =
      """{"value":{"count":5,"date":{"$date":{"$numberLong":"1765324800000"}}}}""".stripMargin

    val datedCountJsonString: String = """{"count":5,"date":"2025-12-10"}""".stripMargin
    val datedCountInvalidJsonString: String = """{"date":"2025-12-10"}""".stripMargin
  }
}
