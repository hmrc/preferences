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

package conf

import java.util.UUID

import javax.inject.{ Inject, Singleton }
import play.api.libs.ws.writeableOf_JsValue
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{ Seconds, Span }
import play.api.Logger
import play.api.libs.json.{ JsArray, JsObject, _ }
import play.api.libs.ws.WSClient
import uk.gov.hmrc.domain._

import scala.concurrent.Future

@Singleton
class ItAuthHelper @Inject() (ws: WSClient) extends ScalaFutures {

  private val logger: Logger = Logger(getClass)
  private def GG_BASE_PAYLOAD: JsObject = Json.obj(
    "credId"             -> s"${UUID.randomUUID.toString}",
    "affinityGroup"      -> "Individual",
    "confidenceLevel"    -> 200,
    "credentialStrength" -> "strong",
    "enrolments"         -> JsArray(),
    "usersName"          -> "Lisa Nicole Brennan",
    "email"              -> "lisa.brennan@some.domain.com"
  )

  private def taxIdKey(taxId: TaxIdentifier) = taxId match {
    case _: SaUtr => "IR-SA"
    case _        => "IR-CT"
  }

  private def enrolmentPayload(taxId: TaxIdentifier) =
    Json.obj(
      "key" -> s"${taxIdKey(taxId)}",
      "identifiers" -> JsArray(
        IndexedSeq(
          Json.obj(
            "key"   -> "UTR",
            "value" -> taxId.value
          )
        )
      ),
      "state" -> "Activated"
    )

  private def addUtrToPayload(payload: JsObject, utr: TaxIdentifier) =
    payload
      .transform((__ \ "enrolments").json.update(__.read[JsArray].map { case JsArray(arr) =>
        JsArray(arr :+ enrolmentPayload(utr))
      }))
      .get

  private def addNinoToPayload(payload: JsObject, nino: Nino) =
    payload ++ Json.obj("nino" -> nino.value)

  def authorisedTokenFor(ggAuthPort: Int, ids: TaxIdentifier*): Future[(String, String)] =
    buildUserToken(
      ids
        .foldLeft(GG_BASE_PAYLOAD)((payload, taxId) =>
          (taxId: @unchecked) match {
            case saUtr: SaUtr => addUtrToPayload(payload, saUtr)
            case nino: Nino   => addNinoToPayload(payload, nino)
          }
        ),
      ggAuthPort
    )

  def authHeader(taxId: TaxIdentifier, ggAuthPort: Int): (String, String) = {
    val (bearerToken, _) = authorisedTokenFor(ggAuthPort, taxId).futureValue
    ("Authorization", bearerToken)
  }

  def buildUserToken(payload: JsObject, ggAuthPort: Int): Future[(String, String)] = {
    logger.warn(s"Auth port:[$ggAuthPort] Payload:[${payload.toString()}]")
    val response = ws
      .url(s"http://localhost:$ggAuthPort/government-gateway/session/login")
      .withHttpHeaders(("Content-Type", "application/json"))
      .post(payload)
      .futureValue(timeout(Span(10, Seconds)))
    val authToken = response.header("Authorization").get
    val authUri = response.header("Location").get
    Future.successful((authToken, authUri))

  }

}
