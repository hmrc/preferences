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

package stubs

import play.api.libs.json.*
import uk.gov.hmrc.domain.{ Nino, SaUtr }

class AuthStubResponseBuilder {
  private var json = Json.obj(
    "internalId" -> "1",
    "email"      -> "test@test.com",
    "loginTimes" -> Json.obj(
      "currentLogin"  -> "2025-07-27T09:00:00.000Z",
      "previousLogin" -> "2025-07-01T12:00:00.000Z"
    )
  )

  def withEnrolments(enrolments: JsArray): AuthStubResponseBuilder = {
    json = json + ("allEnrolments" -> enrolments)
    this
  }

  def withAffinityGroup(group: String): AuthStubResponseBuilder = {
    json = json + ("affinityGroup" -> JsString(group))
    this
  }

  def withConfidenceLevel(level: Int): AuthStubResponseBuilder = {
    json = json + ("confidenceLevel" -> JsNumber(level))
    this
  }

  def withNino(nino: Nino): AuthStubResponseBuilder = {
    json = json + ("nino" -> JsString(nino.nino))
    this
  }

  def withUtr(utr: SaUtr): AuthStubResponseBuilder = {
    json = json + ("saUtr" -> JsString(utr.utr))
    this
  }

  def build(): String = Json.stringify(json)
}
