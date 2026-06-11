/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.model

import org.scalatestplus.play.PlaySpec
import play.api.libs.json._

class EntityIdSpec extends PlaySpec {

  "EntityId" should {
    "be successfully deserialized from string value" in {
      JsString("abc").as[EntityId] must be(EntityId("abc"))
    }

    "not be deserialized from null" in {
      a[JsResultException] must be thrownBy JsNull.as[EntityId]
    }

    "serialised to JsString" in {
      Json.toJson(EntityId("abc")) must be(JsString("abc"))
    }
  }
}
