/*
 * Copyright 2025 HM Revenue & Customs
 *
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
