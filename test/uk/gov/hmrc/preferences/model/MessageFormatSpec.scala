/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.model

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{ JsResultException, JsValue, Json }
import uk.gov.hmrc.domain.{ SimpleName, TaxIdentifier, TaxIds }
import uk.gov.hmrc.domain.TaxIds.TaxIdWithName
import uk.gov.hmrc.preferences.model.Language.English
import utils.TestData.{ TEST_BODY, TEST_EMAIL, TEST_FORENAME, TEST_ID, TEST_REGIME, TEST_SOURCE, TEST_SUBJECT, TEST_TAX_IDENTIFIER, TEST_TEMPLATE_ID, TEST_TITLE }

class MessageFormatSpec extends PlaySpec {

  "taxpayerNameWrites" should {
    "write the object correctly" in {
      import MessageFormat.taxpayerNameWrites

      val taxPayerName: TaxpayerName = TaxpayerName(title = Some(TEST_TITLE), forename = Some(TEST_FORENAME))
      val result: JsValue = Json.parse("""{"title":"test_title","forename":"test_forename"}""".stripMargin)

      Json.toJson(taxPayerName) mustBe result
    }
  }

  "externalRefWrites" should {
    "write the object correctly" in {
      import MessageFormat.externalRefWrites

      val externalRef: ExternalRef = ExternalRef(id = TEST_ID, source = TEST_SOURCE)
      val result: JsValue = Json.parse("""{"id":"test_id","source":"test_source"}""".stripMargin)

      Json.toJson(externalRef) mustBe result
    }
  }

  "recipientWrites" should {
    "write the object correctly" in {
      import MessageFormat.recipientWrites

      val recipient: Recipient = Recipient(taxIdentifier = TEST_TAX_IDENTIFIER, email = TEST_EMAIL)
      val result: JsValue = Json.parse(
        """{"taxIdentifier":{"name":"test_name","value":"test_value"},"email":"test@test.com"}""".stripMargin
      )

      Json.toJson(recipient) mustBe result
    }
  }

  "alertDetailsWrites" should {
    import MessageFormat.alertDetailsWrites

    "write the object correctly" in {
      val taxPayerName: TaxpayerName = TaxpayerName(title = Some(TEST_TITLE))

      val alertDetails: AlertDetails =
        AlertDetails(templateId = TEST_TEMPLATE_ID, recipientName = Some(taxPayerName), data = Map())

      val result: JsValue =
        Json.parse("""{"templateId":"test_template","recipientName":{"title":"test_title"},"data":{}}""".stripMargin)

      Json.toJson(alertDetails) mustBe result
    }
  }

  "contentFormat" should {
    import MessageFormat.contentFormat

    val content: Content = Content(lang = English, subject = TEST_SUBJECT, body = TEST_BODY)
    val contentJsonString: String = """{"lang":"en","subject":"test_subject","body":"test_body"}""".stripMargin
    val contentInvalidJsonString: String = """{"subject":"test_subject","body":"test_body"}""".stripMargin

    "read the json correctly" in {
      Json.parse(contentJsonString).as[Content] mustBe content
    }

    "throw exception for invalid json" in {
      intercept[JsResultException] {
        Json.parse(contentInvalidJsonString).as[Content]
      }
    }

    "write the object correctly" in {
      Json.toJson(content) mustBe Json.parse(contentJsonString)
    }
  }

  "taxEntityWrites" should {
    import MessageFormat.taxEntityWrites

    "write the object correctly" in {
      val taxEntity: TaxEntity =
        TaxEntity(regime = TEST_REGIME, taxIdentifier = TEST_TAX_IDENTIFIER, email = Some(TEST_EMAIL))

      val result: JsValue = Json.parse(
        """{
          |"regime":"test_regime",
          |"taxIdentifier":{"name":"test_name","value":"test_value"},
          |"email":"test@test.com"
          |}""".stripMargin
      )

      Json.toJson(taxEntity) mustBe result
    }
  }
}
