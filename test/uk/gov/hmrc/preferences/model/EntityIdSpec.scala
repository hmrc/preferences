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
