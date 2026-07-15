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

import org.bson.types.ObjectId
import play.api.Logging
import play.api.http.Status.OK
import uk.gov.hmrc.http.{ HeaderCarrier, UpstreamErrorResponse }
import uk.gov.hmrc.preferences.Auditable
import uk.gov.hmrc.preferences.connector.{ EntityResolverConnector, PreferencesChangedNotifierConnector }
import uk.gov.hmrc.preferences.controllers.model.PreferencesChangedRequest
import uk.gov.hmrc.preferences.exceptions.{ EntityTaxIdLookupException, FailedPreferencesChangedException }
import uk.gov.hmrc.preferences.model.{ EntityId, MessageDeliveryFormat, P2Bounced, TaxId }
import uk.gov.hmrc.preferences.util.HttpResponseFormat.HttpResponseString
import uk.gov.hmrc.preferences.util.Dc

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

@Singleton
class PreferencesChangedNotifierService @Inject() (
  entityResolverConnector: EntityResolverConnector,
  pcnConnector: PreferencesChangedNotifierConnector,
  auditable: Auditable
)(implicit ec: ExecutionContext)
    extends Logging {

  def notifyPreferencesChanged(
    id: ObjectId,
    entityId: EntityId,
    format: MessageDeliveryFormat,
    bounced: Boolean = false,
    p2Bounced: Option[P2Bounced] = None
  )(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Unit] =
    for {
      taxId <- getTaxId(id, entityId)
      // If P2 bounced exists, calculate based on NINO; otherwise use the bounced flag
      isBounced = p2Bounced match {
                    case Some(P2Bounced(Some("P2"), Some(nino))) => taxId.nino.contains(nino)
                    case _                                       => bounced
                  }
      _ = logger.debug(s"id: $id, entityId: $entityId, taxId: $taxId, isBounced: $isBounced")
      _ <- send(id, entityId.toString, taxId, format, isBounced)
    } yield ()

  private def send(
    id: ObjectId,
    entityId: String,
    taxId: TaxId,
    format: MessageDeliveryFormat,
    bounced: Boolean
  )(implicit hc: HeaderCarrier): Future[Unit] = {

    val ids =
      Seq((taxId.hmrcMtdItsa, "hmrcMtdItsa"), (taxId.nino, "nino"), (taxId.sautr, "sautr"))
        .filter(_._1.isDefined)
        .map(id => (id._2, id._1.get))
        .toMap

    val pcr = PreferencesChangedRequest(
      changedValue = format,
      preferenceId = id.toString,
      entityId = entityId,
      updatedAt = Dc.instantNow(),
      taxIds = ids,
      bounced = bounced
    )
    logger.debug(s"About to send ${pcr.toString} to the preferences-changed-notifier")
    audit(id, pcr.changedValue.name, ids ++ Map("bounced" -> s"${pcr.bounced}"))
    pcnConnector
      .preferencesChanged(pcr)
      .map { resp =>
        if (resp.status != OK) {
          val msg = s"Unexpected response from preferences changed: ${resp.asString}"
          throw new FailedPreferencesChangedException(msg)
        }
      }
  }

  private def getTaxId(id: ObjectId, entityId: EntityId)(implicit hc: HeaderCarrier) = {
    val taxid = Try {
      entityResolverConnector
        .getTaxId(entityId)
        .recover {
          case UpstreamErrorResponse(body, status, _, headers) =>
            val msg = s"Entity Resolver lookup TaxId failed for " +
              s"preferenceId: $id, entityId: $entityId with UpstreamErrorResponse($status $body)"
            audit(id, msg, body, status, headers)
            throw new EntityTaxIdLookupException(msg)
          case ex =>
            val msg = s"Entity Resolver lookup TaxId failed for " +
              s"preferenceId: $id, entityId: $entityId"
            audit(id, s"$msg, with exception ${ex.getMessage}")
            throw ex
        }
    }
    taxid match {
      case Success(value) => value
      case Failure(ex)    => logger.error(s"Failed to lookup taxid: ${ex.toString}"); throw ex
    }

  }

  private def audit(id: ObjectId, msg: String, tags: Map[String, String] = Map.empty)(implicit
    hc: HeaderCarrier
  ): Unit =
    auditable.sendDataEvent(
      "notify-preferences-changed",
      detail = Map[String, String]("preferenceId" -> id.toString, "message" -> msg),
      tags = tags
    )

  private def audit(id: ObjectId, msg: String, body: String, status: Int, headers: Map[String, Seq[String]])(implicit
    hc: HeaderCarrier
  ): Unit =
    auditable.sendDataEvent(
      "notify-preferences-changed",
      detail = Map[String, String](
        "preferenceId" -> id.toString,
        "message"      -> msg,
        "status"       -> status.toString,
        "body"         -> body,
        "headers"      -> headers.mkString(", ")
      ),
      tags = Map.empty
    )
}
