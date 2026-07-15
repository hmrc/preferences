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

import cats.data.EitherT
import play.api.http.Status.{ BAD_REQUEST, INTERNAL_SERVER_ERROR, NOT_FOUND, OK, UNAUTHORIZED }
import play.api.{ Configuration, Logger }
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse, NotFoundException, StringContextOps, UpstreamErrorResponse }
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.preferences.model.{ EntityId, TaxId }
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.preferences.exceptions.*

import java.net.{ URI, URL }
import java.net.URLEncoder
import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

/** Querystring parameters for the entity-resolver api.
  *
  * taxRegime - Nino, Sautr, if specified requires a taxId taxId - the value of the associated Nino/Sautr, if specified
  * requires a taxRegime resolve - entity-resolver has something called a resolution process. Normally this is true by
  * default, but in a limited set of circumstances it is overriden
  *
  * A note on auth. When a transaction has a logged-in user, an auth token will hold one or more taxIds associated with
  * that user. When this happens, the token is used by entity-resolver to extract those taxIds and use them to look up
  * (or create) an entity.
  *
  * In the case o optin/optout/change email language; if there is no matching entity, one will be created, but only if
  * the resolveIds flag is true.
  */

@Singleton
class EntityResolverConnector @Inject() (config: Configuration, httpClient: HttpClientV2)(implicit ec: ExecutionContext)
    extends ServicesConfig(config) {

  private val logger: Logger = Logger(getClass)

  private val serviceUrl = baseUrl("entity-resolver")

  private def encode(value: String): String = URLEncoder.encode(value, "UTF-8")

  def getEntityIdByTaxId(taxRegime: String, taxId: String)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, EntityResolverResponse, EntityId] = {
    val uri = url"$serviceUrl/entity-resolver?taxRegime=${encode(taxRegime)}&taxId=${encode(taxId)}"
    logger.trace(s"calling entity-resolver to retrieve entity by taxRegime: $taxRegime and taxId: $taxId $uri")
    EitherT(
      httpClient
        .get(uri)
        .execute[HttpResponse]
        .map(mapEntityReponse(_))
        .recover { case e =>
          logger.warn(s"Server Error while getting entityId - ${e.getMessage}")
          Left(EntityRequestServerError(e.getMessage))
        }
    )
  }

  private[connector] def getEntityResolverUrl(
    isRegimeUrl: Boolean,
    entityConflictResolutionFlag: Option[Boolean] = None,
    createNewEntityIfNotFoundFlag: Option[Boolean] = None
  ): URL = {

    val entityUrl = s"$serviceUrl/entity-resolver"

    val entityUrlWithRegime = s"$serviceUrl/regime/entity-resolver"

    def resolverUrl = if isRegimeUrl then entityUrlWithRegime else entityUrl

    val queryString: String = {
      val params = List(
        entityConflictResolutionFlag.map("resolve" -> _),
        createNewEntityIfNotFoundFlag.map("resolveIds" -> _)
      ).flatten

      if (params.nonEmpty) {
        params.map { case (key, value) => s"$key=$value" }.mkString("?", "&", "")
      } else {
        ""
      }
    }
    new URI(s"$resolverUrl$queryString").toURL
  }

  def getEntityIdByAuth(
    entityConflictResolutionFlag: Option[Boolean] = None,
    createNewEntityIfNotFoundFlag: Option[Boolean] = None
  )(implicit hc: HeaderCarrier): EitherT[Future, EntityResolverResponse, EntityId] = {
    val uri = getEntityResolverUrl(isRegimeUrl = false, entityConflictResolutionFlag, createNewEntityIfNotFoundFlag)
    logger.trace(s"calling entity-resolver to retrieve entity from auth: $uri")
    EitherT(
      httpClient
        .get(uri)
        .execute[HttpResponse]
        .map {
          mapEntityReponse(_)
        }
        .recover { case e =>
          logger.warn(s"Server Error while getting entityId - ${e.getMessage}")
          Left(EntityRequestServerError(e.getMessage))
        }
    )
  }

  // NOTE: ITSA only variant
  def getEntityIdByAuthWithRegime(
    entityConflictResolutionFlag: Option[Boolean] = None,
    createNewEntityIfNotFoundFlag: Option[Boolean] = None
  )(implicit hc: HeaderCarrier): EitherT[Future, EntityResolverResponse, EntityId] = {
    val uri = getEntityResolverUrl(isRegimeUrl = true, entityConflictResolutionFlag, createNewEntityIfNotFoundFlag)
    logger.debug(s"calling regime/entity-resolver to retrieve entity from auth: $uri")
    EitherT(
      httpClient
        .get(uri)
        .execute[HttpResponse]
        .map {
          mapEntityReponse(_)
        }
        .recover { case e =>
          logger.warn(s"Server Error while getting entityId - ${e.getMessage}")
          Left(EntityRequestServerError(e.getMessage))
        }
    )
  }

  private val mapEntityReponse: PartialFunction[HttpResponse, Either[EntityResolverResponse, EntityId]] = {
    case response @ r if r.status == OK         => Right((response.json \ "_id").as[EntityId])
    case r if r.status == BAD_REQUEST           => Left(EntityBadRequest(r.body))
    case r if r.status == UNAUTHORIZED          => Left(EntityUnauthorised(r.body))
    case r if r.status == NOT_FOUND             => Left(EntityNotFound)
    case r if r.status == INTERNAL_SERVER_ERROR => Left(EntityRequestServerError(r.body))
  }

  def getTaxIdOption(entityId: EntityId)(implicit hc: HeaderCarrier): Future[Option[TaxId]] =
    getTaxId(entityId).map(Option(_)).recover { case _ =>
      logger.warn(s"TaxId not found for $entityId")
      None
    }

  // NOTE: This returns an Entity
  def getTaxId(entityId: EntityId)(implicit hc: HeaderCarrier): Future[TaxId] =
    httpClient
      .get(url"$serviceUrl/entity-resolver?entityId=${entityId.value}")
      .execute[TaxId]

  def updateEntity(entityId: EntityId, identifier: String)(implicit hc: HeaderCarrier): Future[EntityResolverResponse] =
    httpClient
      .get(url"$serviceUrl/preferences/checkAndDelete/${entityId.value}/$identifier")
      .execute[HttpResponse]
      .map { r =>
        processResponseReason((r.json \ "reason").as[String])
      }
      .recover {
        case _: NotFoundException =>
          logger.warn(s"EntityId not found $entityId")
          InvalidEntity

        case e =>
          logger.warn(s"Error while processing $entityId error: ${e.getMessage}")
          EntityProcessError
      }

  def deleteEntity(entityId: EntityId)(implicit hc: HeaderCarrier): Future[EntityResolverResponse] =
    httpClient
      .delete(url"$serviceUrl/preferences/entity/${entityId.value}")
      .execute[HttpResponse]
      .map { r =>
        processResponseReason((r.json \ "reason").as[String])
      }
      .recover {
        case ex: UpstreamErrorResponse if ex.statusCode == NOT_FOUND =>
          logger.warn(s"EntityId not found $entityId")
          InvalidEntity

        case e =>
          logger.warn(s"Error while processing $entityId error: ${e.getMessage}")
          EntityProcessError
      }

  private def processResponseReason(str: String): EntityResolverResponse =
    str match {
      case "INVALID_ENTITY"          => InvalidEntity
      case "UNSET_MARK_DE_ENROLMENT" => UnsetMarkDeEnrolment
      case "DELETE_PREFERENCES"      => DeletePreferences
      case _                         => DoNotProcess
    }
}
