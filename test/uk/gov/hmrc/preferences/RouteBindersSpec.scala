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

package uk.gov.hmrc.preferences

import org.bson.types.ObjectId
import org.scalatestplus.play.PlaySpec

class RouteBindersSpec extends PlaySpec {

  "bson object binder" should {
    "return nothing if the parameter is not present" in {
      ObjectIdBinder.bind("offset", Map()) mustBe None
    }

    "return BsonObjectId for valid input" in {
      val id = ObjectId.get().toString
      ObjectIdBinder.bind("offset", Map("offset" -> Seq(id))) must be(Some(Right(new ObjectId(id))))
    }

    "give error for invalid input" in {
      ObjectIdBinder.bind("offset", Map("offset" -> Seq("invalid"))) must be(
        Some(Left("Cannot parse parameter 'offset' with parameters 'Map(offset -> List(invalid))' as 'ObjectId'"))
      )
    }
  }
}
