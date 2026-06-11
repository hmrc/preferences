/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.connector

import com.google.inject.Inject
import play.api.http.Status
import play.api.Logger
import play.api.http.HeaderNames.*
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.http.{ HeaderCarrier, StringContextOps, UpstreamErrorResponse }
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.{ HttpClientV2, RequestBuilder }
import uk.gov.hmrc.preferences.model.{ NameFromHods, TaxpayerName }
import uk.gov.hmrc.preferences.model.NameFromHods.*

import java.util.{ Base64, UUID }
import scala.concurrent.{ ExecutionContext, Future }

class TaxpayerConnector @Inject() (httpClient: HttpClientV2, servicesConfig: ServicesConfig)(implicit
  ec: ExecutionContext
) extends Status {
  private val logger: Logger = Logger(getClass)

  val serviceUrl: String = servicesConfig.baseUrl("taxpayer-data")

  def getTaxpayerName(utr: SaUtr)(implicit hc: HeaderCarrier): Future[Option[TaxpayerName]] = {
    val taxPayerClient: RequestBuilder =
      if (hipSwitchOn)
        httpClient
          .get(url"$serviceUrlViaHip/ods-sa/v1/self-assessment/individual/$utr/designatory-details/taxpayer")
          .setHeader(requestHeaders: _*)
      else
        httpClient
          .get(url"$serviceUrl/self-assessment/individual/$utr/designatory-details/taxpayer")

    taxPayerClient
      .execute[NameFromHods]
      .map(_.name)
      .recover {
        case a: UpstreamErrorResponse if a.statusCode == NOT_FOUND =>
          logger.info(s"No taxpayer name found for utr: $utr")
          None
        case b: UpstreamErrorResponse =>
          logger.warn(s"Unable to get taxpayer name for utr: $utr, ${b.statusCode}")
          None
        case ex =>
          logger.warn(s"Unable to get taxpayer name for utr: $utr, $ex")
          None
      }
  }

  private val hipSwitchOn: Boolean = servicesConfig.getConfBool("taxpayer-data-hip.enabled", false)
  private val serviceUrlViaHip: String = servicesConfig.baseUrl("taxpayer-data-hip")
  private val requestHeaders = {
    val clientId = servicesConfig.getConfString("taxpayer-data-hip.client-id", "unknown")
    val clientSecret = servicesConfig.getConfString("taxpayer-data-hip.client-secret", "unknown")
    val credentials = s"$clientId:$clientSecret"
    val b64Encoded = Base64.getEncoder.encodeToString(credentials.getBytes("UTF-8"))
    Seq(AUTHORIZATION -> s"Basic $b64Encoded", "correlationId" -> UUID.randomUUID.toString)
  }
}
