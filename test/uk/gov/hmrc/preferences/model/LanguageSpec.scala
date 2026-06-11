/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.model

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{ JsString, Json }
import Language._

class LanguageSpec extends PlaySpec {

  "Language" should {
    """Language(cy) should be successfully deserialized from string "cy" """ in {
      JsString("cy").as[Language] must be(Language.Welsh)
    }

    """Language(en) should be successfully deserialized from string "en" """ in {
      JsString("en").as[Language] must be(Language.English)
    }

    """Language(en) should be successfully deserialized from any other string """ in {
      JsString("foobar").as[Language] must be(Language.English)
    }

    """Language(en) should successfully serialized to JsString("en")""" in {
      Json.toJson(Language.English) must be(JsString("en"))
    }

    """Language(cy) should successfully serialized to JsString("cy")""" in {
      Json.toJson(Language.Welsh) must be(JsString("cy"))
    }
  }

}
