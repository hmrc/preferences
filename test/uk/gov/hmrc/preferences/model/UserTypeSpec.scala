/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.model

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{ JsResultException, Json }
import uk.gov.hmrc.auth.core.AffinityGroup.Agent
import uk.gov.hmrc.auth.core.ConfidenceLevel.L200

class UserTypeSpec extends PlaySpec {

  "Json Reads" should {
    import UserType.userTypeReads

    "read the json correctly" in new Setup {
      Json.parse(userTypeJsonString).as[UserType] mustBe userType
    }

    "throw the exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(userTypeInvalidJsonString).as[UserType]
      }
    }
  }

  "Json Writes" should {
    "write the object correctly" in new Setup {
      Json.toJson(userType) mustBe Json.parse(userTypeJsonString)
    }
  }

  trait Setup {
    val userType: UserType = UserType(affinityGroup = Some(Agent), confidenceLevel = Some(L200))

    val userTypeJsonString: String = """{"affinityGroup":"Agent","confidenceLevel":200}""".stripMargin
    val userTypeInvalidJsonString: String = """{"affinityGroup":"Unknown","confidenceLevel":200}""".stripMargin
  }
}
