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

package uk.gov.hmrc.preferences.connector

import play.api.Logger
import play.api.http.Status.OK
import play.api.libs.json.{ Json, OFormat }
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse, StringContextOps }
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.preferences.model.TaxId
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import play.api.libs.ws.WSBodyWritables.writeableOf_JsValue
import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class ChannelPreferencesConnector @Inject() (httpClient: HttpClientV2, servicesConfig: ServicesConfig)(implicit
  ec: ExecutionContext
) {
  private val logger = Logger(getClass)
  val serviceUrl: String = servicesConfig.baseUrl("channel-preferences")

  case class PreferenceStatus(enrolment: String, status: Boolean)
  implicit val formats: OFormat[PreferenceStatus] = Json.format[PreferenceStatus]

  def updatePreferencesForItsa(hmrcMtdItsaId: String, paperless: Boolean, eventId: Option[String])(implicit
    hc: HeaderCarrier
  ): Future[Boolean] = {
    val eventIdLog = eventId.fold("")(e => s"eventId $e")
    // DC-9128 MTD signup service sends itsaId values with prefixed enrolment key,
    // until they have updated to send unprefixed values, we need to add this check
    val itsaIdWithEnrolmentPrefix: String =
      if (hmrcMtdItsaId.contains("HMRC-MTD-IT~")) hmrcMtdItsaId else s"HMRC-MTD-IT~MTDBSA~$hmrcMtdItsaId"
    httpClient
      .post(url"$serviceUrl/channel-preferences/preference/itsa/status")
      .withBody(Json.toJson(PreferenceStatus(itsaIdWithEnrolmentPrefix, paperless)))
      .execute[HttpResponse]
      .map {
        case r: HttpResponse if r.status == OK =>
          logger.info(s"Successfully done the ETMP update for eventIdLog $eventIdLog")
          true
        case e =>
          logger.error(
            s"Failed to update the ETMP status for the itsa entity $itsaIdWithEnrolmentPrefix and $eventIdLog, with: ${e.toString()}"
          )
          false
      }
  }
}
