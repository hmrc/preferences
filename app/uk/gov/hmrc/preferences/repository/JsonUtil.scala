/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.repository

import play.api.libs.json.{ Format, JsObject, JsResult, JsValue, OFormat }

object JsonUtil {

  def oFormat[T](format: Format[T]): OFormat[T] = {
    val oFormat: OFormat[T] = new OFormat[T]() {
      override def writes(o: T): JsObject = format.writes(o).as[JsObject]
      override def reads(json: JsValue): JsResult[T] = format.reads(json)
    }
    oFormat
  }
}
