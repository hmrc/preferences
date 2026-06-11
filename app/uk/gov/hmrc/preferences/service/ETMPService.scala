/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.service

import play.api.Logger
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.preferences.connector.{ ChannelPreferencesConnector, EntityResolverConnector }
import uk.gov.hmrc.preferences.model.EntityId

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

sealed trait ETMPUpdateStatus

case object ETMPUpdateSuccess extends ETMPUpdateStatus

case object ETMPUpdateFailure extends ETMPUpdateStatus

case object TaxIdFetchFailed extends ETMPUpdateStatus

case object ETMPUpdateNotRequired extends ETMPUpdateStatus

case class ETMPUpdateError(error: String) extends ETMPUpdateStatus

class ETMPService @Inject() (
  entityResolverConnector: EntityResolverConnector,
  channelPreferencesConnector: ChannelPreferencesConnector
)(implicit ec: ExecutionContext) {

  private val logger: Logger = Logger(getClass)

  def checkAndUpdateETMP(entityId: EntityId, paperless: Boolean, eventId: Option[String])(implicit
    hc: HeaderCarrier
  ): Future[ETMPUpdateStatus] = {
    val eventIdLog = eventId.fold("")(e => s"eventId $e")
    entityResolverConnector.getTaxIdOption(entityId) flatMap {
      case Some(taxId) if taxId.hmrcMtdItsa.isDefined =>
        logger.info(s"ETMP Updated: retrieved tax id for itsa for $entityId and $eventIdLog")
        channelPreferencesConnector.updatePreferencesForItsa(taxId, paperless, eventId) map { successful =>
          if (successful) ETMPUpdateSuccess else ETMPUpdateFailure
        }
      case Some(_) =>
        logger.info(s"ETMP Update is not required for non-itsa taxId and $eventIdLog")
        Future.successful(ETMPUpdateNotRequired)
      case None =>
        logger.error(s"Entity resolver failed to fetch TaxId as part of ETMP update $eventIdLog, entityId: $entityId")
        Future.successful(TaxIdFetchFailed)
    } recover { case e =>
      logger.error(s"Failed in the process of ETMPUpdate $eventIdLog", e)
      ETMPUpdateError(e.getMessage)
    }
  }
}
