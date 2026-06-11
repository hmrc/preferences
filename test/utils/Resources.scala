/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package utils

import play.api.libs.json.{ JsValue, Json }

import scala.io.Source

object Resources {
  def readJson(fileName: String): JsValue =
    Json.parse(readFile(fileName))

  def readFile(fileName: String): String = {
    val resource = Source.fromURL(getClass.getResource("/" + fileName))
    val str = resource.mkString
    resource.close()
    str
  }
}
