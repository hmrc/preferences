/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.connector

import play.api.Logger
import play.api.http.Status.NOT_FOUND
import play.api.libs.json.{ Json, OFormat }
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{ HeaderCarrier, UpstreamErrorResponse }
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.preferences.model.TaxpayerName
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2

import java.net.URL
import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

case class CitizenDetails(
  firstName: Option[String],
  lastName: Option[String],
  title: Option[String],
  deceased: Option[Boolean] = None
)

object CitizenDetails {
  implicit val formats: OFormat[CitizenDetails] = Json.format[CitizenDetails]
}

@Singleton
class CitizenDetailsConnector @Inject() (
  http: HttpClientV2,
  servicesConfig: ServicesConfig
)(implicit ec: ExecutionContext) {

  val logger: Logger = Logger(this.getClass)

  private def url(nino: String) =
    new URL(servicesConfig.baseUrl("citizen-details") + s"/citizen-details/$nino/designatory-details/basic")

  /** Gets the person details
    */
  def getTaxpayerName(nino: Nino)(implicit hc: HeaderCarrier): Future[Option[TaxpayerName]] =
    http
      .get(url(nino.value))
      .execute[CitizenDetails]
      .map(Option(_))
      .recover {
        case ex: UpstreamErrorResponse if ex.statusCode == NOT_FOUND =>
          logger.info(s"No citizen details found for nino: $nino")
          Option.empty[CitizenDetails]
        case _ =>
          logger.warn(s"Unable to get citizen details for nino: $nino")
          Option.empty[CitizenDetails]
      }
      .map(_.map(cid => TaxpayerName(title = cid.title, forename = cid.firstName, surname = cid.lastName)))
}
