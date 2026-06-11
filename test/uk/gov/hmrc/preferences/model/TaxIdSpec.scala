/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.model

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{ JsResultException, Json }
import utils.TestData.{ TEST_ID, TEST_ITSA_ID, TEST_NINO, TEST_SAUTR }

class TaxIdSpec extends PlaySpec {

  "formats" should {
    import TaxId.formats

    "read the json correctly" in new Setup {
      Json.parse(taxIdJsonStringWithHMRCMDTITIdentifier).as[TaxId] mustBe taxId
    }

    "throw exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(taxIdJsonInvalidString).as[TaxId]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(taxId) mustBe Json.parse(taxIdJsonString)
    }
  }

  trait Setup {
    val taxId: TaxId =
      TaxId(_id = TEST_ID, sautr = Some(TEST_SAUTR), nino = Some(TEST_NINO), hmrcMtdItsa = Some(TEST_ITSA_ID))

    val taxIdJsonString: String =
      """{"_id":"test_id","sautr":"2000029888","nino":"AB112233A","hmrcMtdItsa":"test-itsa-id"}""".stripMargin

    val taxIdJsonStringWithHMRCMDTITIdentifier: String =
      """{"_id":"test_id","sautr":"2000029888","nino":"AB112233A","HMRC-MTD-IT":"test-itsa-id"}""".stripMargin

    val taxIdJsonInvalidString: String =
      """{"sautr":"2000029888","nino":"AB112233A","hmrcMtdItsa":"test-itsa-id"}""".stripMargin
  }
}
