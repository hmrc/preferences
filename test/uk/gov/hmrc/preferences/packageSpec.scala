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

import org.mongodb.scala.bson
import org.scalatestplus.play.PlaySpec
import play.api.mvc.QueryStringBindable
import uk.gov.hmrc.preferences.model.EntityId

class packageSpec extends PlaySpec {

  "entityid binders" should {

    "bind entityid" in {
      val eitherEntityId = entityIdBinder.bind("key", "entity-id")
      eitherEntityId.fold(EntityId(_), id => id) must be(EntityId("entity-id"))
    }

    "unbind entityid" in {
      val entityId = entityIdBinder.unbind("key", EntityId("entity-id"))
      entityId must be("entity-id")
    }
  }

  "ObjectId query string binder" should {

    "bind a valid objectid" in {
      val obj = ObjectIdBinder.bind("key", Map("key" -> List("1a4b5ef397123459876ab5fa")))
      obj.get.fold[bson.ObjectId](s => new bson.ObjectId(s), id => id).toString must be("1a4b5ef397123459876ab5fa")
    }

    "not bind a valid objectid with invalid key" in {
      val obj = ObjectIdBinder.bind("wrongkey", Map("key" -> List("1a4b5ef397123459876ab5fa")))
      obj.isDefined must be(false)
    }

    "not bind an invalid objectid" in {
      val obj = ObjectIdBinder.bind("key", Map("key" -> List("1")))
      a[RuntimeException] must be thrownBy {
        obj.get.fold[bson.ObjectId](_ => throw new RuntimeException(), id => id)
      }
    }

    "unbind objectid" in {
      val str = ObjectIdBinder.unbind("key", new bson.ObjectId("1a4b5ef397123459876ab5fa"))
      str must be("key=1a4b5ef397123459876ab5fa")
    }
  }

  "ObjectId path binder" should {

    "bind a valid objectid" in {
      val obj = bsonIdBinder.bind("key", "1a4b5ef397123459876ab5fa")
      obj.fold[bson.ObjectId](s => new bson.ObjectId(s), id => id).toString must be("1a4b5ef397123459876ab5fa")
    }

    "not bind an invalid objectid" in {
      val obj = bsonIdBinder.bind("key", "1")
      a[RuntimeException] must be thrownBy {
        obj.fold[bson.ObjectId](_ => throw new RuntimeException(), id => id)
      }
    }

    "unbind objectid" in {
      val str = bsonIdBinder.unbind("key", new bson.ObjectId("1a4b5ef397123459876ab5fa"))
      str must be("1a4b5ef397123459876ab5fa")
    }
  }

  "taxIdParams querystring binder" should {
    val bindable = implicitly[QueryStringBindable[TaxIdParams]]

    "bind a valid taxid params" in {
      val params = Map("taxRegime" -> Seq("nino"), "taxId" -> Seq("AB112233A"))
      bindable.bind("", params) mustBe Some(Right(TaxIdParams("nino", "AB112233A")))
    }

    "bind invalid key" in {
      val params = Map("taxRegimey" -> Seq("nino"), "taxId" -> Seq("AB112233A"))
      bindable.bind("", params) mustBe None
    }

    "unbind taxid params" in {
      val taxIdParams = TaxIdParams("nino", "AB112233A")
      bindable.unbind("", taxIdParams) mustBe "taxRegime=nino&taxId=AB112233A"
    }

    "unbind blank taxid param" in {
      val taxIdParams = TaxIdParams("nino", "")
      bindable.unbind("", taxIdParams) mustBe "taxRegime=nino&taxId="
    }
  }

  "resolveParams querystring binder" should {
    val bindable = implicitly[QueryStringBindable[ResolveParams]]

    "bind a valid resolve params" in {
      val params = Map("resolve" -> Seq("true"))
      bindable.bind("", params) mustBe Some(Right(ResolveParams(true)))
    }

    "unbind resolve param" in {
      val resolveFalse = ResolveParams(false)
      bindable.unbind("", resolveFalse) mustBe "resolve=false"
      val resolveTrue = ResolveParams(true)
      bindable.unbind("", resolveTrue) mustBe "resolve=true"
    }
  }

  "preferencesParams querystring binder" should {
    val bindable = implicitly[QueryStringBindable[PreferencesParams]]

    "bind valid taxid params" in {
      val params = Map("taxRegime" -> Seq("nino"), "taxId" -> Seq("AB112233A"))
      bindable.bind("", params) mustBe Some(Right(PreferencesParams(Some(TaxIdParams("nino", "AB112233A")), None)))
    }

    "bind valid resolve params" in {
      val params = Map("resolve" -> Seq("true"))
      bindable.bind("", params) mustBe Some(Right(PreferencesParams(None, Some(ResolveParams(true)))))
    }

    "bind valid taxid and resolve params" in {
      val params = Map("taxRegime" -> Seq("nino"), "taxId" -> Seq("AB112233A"), "resolve" -> Seq("true"))
      bindable.bind("", params) mustBe Some(
        Right(PreferencesParams(Some(TaxIdParams("nino", "AB112233A")), Some(ResolveParams(true))))
      )
    }

    "unbind taxid param" in {
      val preferencesParams = PreferencesParams(Some(TaxIdParams("nino", "AB112233A")), None)
      bindable.unbind("", preferencesParams) mustBe "taxRegime=nino&taxId=AB112233A"
    }

    "unbind resolve param" in {
      val preferencesParams = PreferencesParams(None, Some(ResolveParams(true)))
      bindable.unbind("", preferencesParams) mustBe "resolve=true"
    }

    "unbind taxid and resolve param" in {
      val preferencesParams = PreferencesParams(Some(TaxIdParams("nino", "AB112233A")), Some(ResolveParams(true)))
      bindable.unbind("", preferencesParams) mustBe "taxRegime=nino&taxId=AB112233A&resolve=true"
    }
  }
}
