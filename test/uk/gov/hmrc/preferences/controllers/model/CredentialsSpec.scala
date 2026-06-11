/*
 * Copyright 2025 HM Revenue & Customs
 *
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
