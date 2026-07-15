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

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonMappingException
import play.api.Application
import play.api.libs.json.Json.toJson
import play.api.libs.json.{ Format, JsObject, JsValue, Json }
import play.api.libs.ws.WSClient
import play.api.test.FakeRequest
import play.api.test.Helpers.{ route, * }
import uk.gov.hmrc.preferences.model.EntityId
import uk.gov.hmrc.preferences.util.{ DateFormats, Dc }

import java.time.Instant
import javax.inject.{ Inject, Singleton }
import scala.annotation.unused
import scala.util.control.Exception

@Singleton
class PreferencesTestRoutes @Inject() (@unused ws: WSClient, app: Application) {

  def workItemFilter(failedBefore: Instant = Dc.instantNow(), availableBefore: Instant = Dc.instantNow()): JsObject = {
    implicit val dtf: Format[Instant] = DateFormats.instantFormats
    Json.obj("filters" -> Json.obj("failedBefore" -> failedBefore, "availableBefore" -> availableBefore))
  }

  val emptyBody: JsValue = toJson(Map[String, String]())

  type Header = (String, String)

  case class FakeResponse(json: JsValue, status: Int, responseString: String = "") {
    def withFilter(value: Any) = ???

  }

  val jsonParseExceptions: Exception.Catch[Nothing] =
    Exception.catching(classOf[JsonParseException], classOf[JsonMappingException])

  def requestMethod(method: String): (String, Option[Header], JsValue) => FakeResponse =
    (url, headers, body) => {
      val request = FakeRequest(method, url).withBody(body)
      val withHeaders = headers.fold(request)(request.withHeaders(_))
      val result = route(app, withHeaders).get

      val resultString = contentAsString(result)

      val jsonOpt = jsonParseExceptions.opt(Json.parse(resultString))

      FakeResponse(jsonOpt.getOrElse(emptyBody), status(result), resultString)
    }

  def delete(url: String, headers: Option[Header] = None): FakeResponse =
    requestMethod("DELETE")(url, headers, emptyBody)

  def get(url: String, headers: Option[Header] = None): FakeResponse =
    requestMethod("GET")(url, headers, emptyBody)

  def post(url: String, body: JsValue = emptyBody, headers: Option[Header] = None) =
    requestMethod("POST")(url, headers, body)

  def put(url: String, body: JsValue = emptyBody, headers: Option[Header] = None): FakeResponse =
    requestMethod("PUT")(url, headers, body)

}

object PreferencesTestRoutes {

  def `/preferences/:entityId/orphan-status/status`(statusUrl: String): String = statusUrl.replaceAll("\"", "")

  def `/preferences/:entityId/optout`(entityId: EntityId): String =
    s"/preferences/${entityId.value}/optout"

  def `/preferences/:entityId/optin`(entityId: EntityId): String =
    s"/preferences/${entityId.value}/optin"

  val optin: String = "/preferences/optin"

  def `/preferences/email`: String = "/preferences/email"

  def `/preferences/stats`: String = "/preferences/stats"

  def `/preferences/:entityId/pending-email`(entityId: EntityId): String = s"/preferences/$entityId/pending-email"

  def `/preferences/updated-print-suppression/pull-work-item`: String =
    "/preferences/updated-print-suppression/pull-work-item"

  def `/preferences/:entityId/activate`(entityId: EntityId): String = s"/preferences/${entityId.value}/activate"

  def `/preferences/:entityId`(entityId: EntityId): String = s"/preferences/${entityId.value}"

  def `/preferences/email/:emailId`(emailId: String): String = s"/preferences/email/$emailId"

  def `/preferences/language/:emailId`(emailId: String): String = s"/preferences/language/$emailId"

  def `/preferences/:entityId/updated`(entityId: EntityId): String = s"/preferences/${entityId.value}/updated"

  def `/preferences-admin/:entityId`(entityId: EntityId): String = s"/test-only/preferences-admin/${entityId.value}"

  def `/preferences-admin/events/:entityId`(entityId: EntityId): String = s"/preferences-admin/events/${entityId.value}"

  def `/preferences-admin/:entityId/expire-email-verification-link`(entityId: EntityId): String =
    s"/test-only/preferences-admin/${entityId.value}/expire-email-verification-link"

  def `/preferences/:entityId/verified-email-address`(entityId: EntityId): String =
    s"/preferences/${entityId.value}/verified-email-address"

  def aBTestingCohortUri(entityId: EntityId, processId: String, taxRegime: String = "sa"): String =
    s"/a-b-testing/$entityId/cohort?processId=$processId&regime=$taxRegime"

  def `/preferences-admin/remove-bounces`: String = "/test-only/preferences-admin/remove-bounces"

  def `/preferences-admin/print-suppression`: String = "/test-only/preferences-admin/print-suppression"

  def `/preferences-admin/:entityId/verification-token`(entityId: EntityId): String =
    s"/test-only/preferences-admin/$entityId/verification-token"

  def `/preferences-admin/bounce-email`: String = "/test-only/preferences-admin/bounce-email"

  def `/preferences-admin/:entityId/verify-email`(entityId: EntityId): String =
    s"/test-only/preferences-admin/$entityId/verify-email"

  def adminOptOut(params: String): String =
    s"/preferences-admin/optout?$params"

}
