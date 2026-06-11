/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.controllers.model

import org.scalatestplus.play.*
import play.api.libs.json.{ JsResultException, Json }
import utils.TestData.TEST_TOKEN

import java.util.UUID

class EmailTokenSpec extends PlaySpec {

  "isValid" must {
    "return true for a valid uuid token" in {
      val wellFormattedToken: String = "12345678-abcd-4abc-abcd-123456789012"

      val emailToken = new EmailToken(wellFormattedToken)
      val valid = emailToken.isValid

      valid mustBe true
    }

    "return false if the token is not in a valid uuid format (extra characters)" in {
      val tokenWithSomeExtraStuff = "12345678-abcd-4abc-abcd-123456789012423"

      val emailToken = new EmailToken(tokenWithSomeExtraStuff)
      val valid = emailToken.isValid

      valid mustBe false
    }

    "return false if the token is not in a valid uuid format" in {
      val emailToken = new EmailToken(TEST_TOKEN)
      val valid = emailToken.isValid

      valid mustBe false
    }
  }

  "toString" should {

    "return the token for some string" in {
      EmailToken(TEST_TOKEN).toString mustBe TEST_TOKEN
    }

    "return the token for a UUID" in {
      val uuid = UUID.randomUUID().toString
      EmailToken(uuid).toString mustBe uuid
    }
  }

  "formats" should {
    import EmailToken.formats

    "read the json correctly" in new Setup {
      Json.parse(emailTokenJsonString).as[EmailToken] mustBe emailToken
    }

    "throw exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(emailTokenInvalidJsonString).as[EmailToken]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(emailToken) mustBe Json.parse(emailTokenJsonString)
    }
  }

  trait Setup {
    val emailToken: EmailToken = EmailToken(TEST_TOKEN)

    val emailTokenJsonString: String = """{"token":"test_token"}""".stripMargin
    val emailTokenInvalidJsonString: String = """{"token1":"test_token"}""".stripMargin
  }
}
