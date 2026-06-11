/*
 * Copyright 2025 HM Revenue & Customs
 *
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
