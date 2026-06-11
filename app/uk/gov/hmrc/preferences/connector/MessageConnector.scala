/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.connector

import com.google.inject.Inject
import play.api.http.Status
import play.api.libs.json.JsValue
import play.api.libs.ws.writeableOf_JsValue
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse, StringContextOps }
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2

import scala.concurrent.{ ExecutionContext, Future }

class MessageConnector @Inject() (httpClient: HttpClientV2, servicesConfig: ServicesConfig)(implicit
  ec: ExecutionContext
) extends Status {

  val secureMessageBaseUrl = url"${servicesConfig.baseUrl("secure-message")}/secure-messaging/v4/message"

  def postMessage(body: JsValue)(implicit hc: HeaderCarrier): Future[HttpResponse] =
    httpClient
      .post(secureMessageBaseUrl)
      .withBody(body)
      .execute[HttpResponse]

}
