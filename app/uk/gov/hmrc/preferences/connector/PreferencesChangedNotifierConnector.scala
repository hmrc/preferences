/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.connector

import com.google.inject.Inject
import play.api.http.Status.OK
import play.api.libs.json.{ Json, OWrites }
import play.api.libs.ws.writeableOf_JsValue
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse }
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.preferences.Auditable
import uk.gov.hmrc.preferences.controllers.model.PreferencesChangedRequest
import uk.gov.hmrc.preferences.util.HttpResponseFormat.*
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2

import java.net.URL
import javax.inject.Singleton
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class PreferencesChangedNotifierConnector @Inject() (
  httpClient: HttpClientV2,
  servicesConfig: ServicesConfig,
  auditable: Auditable
)(implicit ec: ExecutionContext) {

  private val TransactionName: String = "notify-preference-changed"

  implicit val writer: OWrites[PreferencesChangedRequest] = PreferencesChangedRequest.writes
  private def preferencesChangedNotifierBaseUrl: String = servicesConfig.baseUrl("preferences-changed-notifier")

  private def url(path: String) = new URL(s"$preferencesChangedNotifierBaseUrl$path")

  def preferencesChanged(body: PreferencesChangedRequest)(implicit hc: HeaderCarrier): Future[HttpResponse] =
    httpClient
      .post(url("/preferences-changed"))
      .withBody(Json.toJson(body))
      .execute[HttpResponse]
      .map { response =>
        response.status match {
          case OK => response
          case _  => audit(response); response
        }
      }

  private def audit(httpResponse: HttpResponse)(implicit hc: HeaderCarrier): Unit =
    auditable.sendDataEvent(
      transactionName = TransactionName,
      tags = Map.empty,
      detail = Map(
        "status"   -> httpResponse.status.toString,
        "response" -> httpResponse.asString
      )
    )
}
