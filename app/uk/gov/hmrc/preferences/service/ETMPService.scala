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
        channelPreferencesConnector.updatePreferencesForItsa(taxId.hmrcMtdItsa.get, paperless, eventId) map {
          successful =>
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
