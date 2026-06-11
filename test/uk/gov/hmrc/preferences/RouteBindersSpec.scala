/*
 * Copyright 2023 HM Revenue & Customs
 *
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
