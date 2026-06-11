/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences

import org.bson.Document
import play.api.libs.json.{ JsObject, Json }

object JsonToBson {

  def jsobjToBson(js: JsObject): Document =
    Document.parse(Json.stringify(js))
}
