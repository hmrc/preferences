/*
 * Copyright 2023 HM Revenue & Customs
 *
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
