/*
 * Copyright 2023 HM Revenue & Customs
 *
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

  def updatePreferencesForItsa(taxId: TaxId, paperless: Boolean, eventId: Option[String])(implicit
    hc: HeaderCarrier
  ): Future[Boolean] = {
    val eventIdLog = eventId.fold("")(e => s"eventId $e")
    httpClient
      .post(url"$serviceUrl/channel-preferences/preference/itsa/status")
      .withBody(Json.toJson(PreferenceStatus(s"HMRC-MTD-IT~MTDBSA~${taxId.hmrcMtdItsa.getOrElse("")}", paperless)))
      .execute[HttpResponse]
      .map {
        case r: HttpResponse if r.status == OK =>
          logger.info(s"Successfully done the ETMP update for eventIdLog $eventIdLog")
          true
        case e =>
          logger.error(s"Failed to update the ETMP status for the entity $taxId and $eventIdLog, with: ${e.toString()}")
          false
      }
  }
}
