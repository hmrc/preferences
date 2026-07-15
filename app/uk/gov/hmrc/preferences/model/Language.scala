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

import play.api.libs.json._

enum Language(val entryName: String) {
  case English extends Language("en")

  case Welsh extends Language("cy")
}

object Language {

  private def withNameInsensitiveOption(name: String): Option[Language] =
    Language.values.find(_.entryName.equalsIgnoreCase(name))

  given languageReads: Reads[Language] = new Reads[Language] {
    override def reads(json: JsValue): JsResult[Language] = json match {
      case JsString(value) => JsSuccess(withNameInsensitiveOption(value).getOrElse(Language.English))
      case _               => JsSuccess(Language.English)
    }
  }

  given languageWrites: Writes[Language] = new Writes[Language] {
    override def writes(lang: Language): JsValue = JsString(lang.entryName)
  }

  given languageFormat: Format[Language] = new Format[Language] {
    override def reads(json: JsValue): JsResult[Language] = languageReads.reads(json)

    override def writes(lang: Language): JsValue = languageWrites.writes(lang)
  }

}
