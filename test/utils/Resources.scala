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
