/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.controllers

import cats.data.EitherT
import cats.instances.future.*

import javax.inject.{ Inject, Singleton }
import play.api.Logger
import play.api.libs.json.*
import play.api.mvc.{ Action, ControllerComponents, Request, Result }
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.auth.core.{ AuthConnector, AuthorisedFunctions, MissingBearerToken }
import uk.gov.hmrc.http.NotFoundException
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendBaseController
import uk.gov.hmrc.preferences.TaxIdParams
import uk.gov.hmrc.preferences.connector.EntityResolverConnector
import uk.gov.hmrc.preferences.controllers.model.{ Credentials, TermsAndConditionsRequest }
import uk.gov.hmrc.preferences.exceptions.{ EntityBadRequest, EntityNotFound, EntityRequestServerError, EntityUnauthorised, PreferenceNotFound }
import uk.gov.hmrc.preferences.model.*
import uk.gov.hmrc.preferences.repository.*
import uk.gov.hmrc.preferences.service.TermsAndConditionsService

import java.time.Instant
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

@Singleton
class TermsAndConditionsController @Inject() (
  termsAndConditionsService: TermsAndConditionsService,
  entityResolverConnector: EntityResolverConnector,
  override val controllerComponents: ControllerComponents,
  val authConnector: AuthConnector,
  implicit val timeSource: () => Instant
)(implicit ec: ExecutionContext)
    extends BackendBaseController with AuthorisedFunctions {

  private val logger: Logger = Logger(getClass)

  private val retrievals = Retrievals.affinityGroup and Retrievals.confidenceLevel

  // Optin / Optout/ SetLanguage By auth
  def updatePreferences(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[TermsAndConditionsRequest] { genericTAndCsRequest =>
      authorised().retrieve(retrievals) { case affinityGroup ~ confidenceLevel =>
        setUserPreferences(genericTAndCsRequest, Some(Credentials(affinityGroup, confidenceLevel)))
      }
    }.recover(httpResultFromError)
  }

  // The with regime variants are ONLY used for ITSA calls at this time.
  def updatePreferencesWithRegime(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[TermsAndConditionsRequest] { genericTAndCsRequest =>
      authorised().retrieve(retrievals) { case affinityGroup ~ confidenceLevel =>
        setUserPreferencesWithRegime(genericTAndCsRequest, Some(Credentials(affinityGroup, confidenceLevel)))
      }
    }.recover(httpResultFromError)
  }

  private val combinedResultHandler = httpSpecificResultFrom orElse httpResultFrom

  private def setUserPreferences(genericTAndCsRequest: TermsAndConditionsRequest, credentials: Option[Credentials])(
    implicit r: Request[JsValue]
  ) = {
    val result = for {
      eId <- entityResolverConnector.getEntityIdByAuth(Some(true), Some(true))
      res <- updateConsentAndLanguage(eId, genericTAndCsRequest, credentials)
    } yield res
    result.value.map(combinedResultHandler)
  }

  private def setUserPreferencesWithRegime(
    genericTAndCsRequest: TermsAndConditionsRequest,
    credentials: Option[Credentials]
  )(implicit
    r: Request[JsValue]
  ) = {
    val result = for {
      eId <- entityResolverConnector.getEntityIdByAuthWithRegime(Some(true), Some(true))
      res <- updateConsentAndLanguage(eId, genericTAndCsRequest, credentials)
    } yield res
    result.value.map(combinedResultHandler)
  }

  private def updateConsentAndLanguage(
    entityId: EntityId,
    genericTAndCsRequest: TermsAndConditionsRequest,
    credentials: Option[Credentials]
  )(implicit r: Request[JsValue]): EitherT[Future, Throwable, PreferenceUpdateResult] =
    EitherT(
      termsAndConditionsService
        .handleTermsAndConditionsRequest(entityId, genericTAndCsRequest, credentials)
        .transform {
          case Success(value) => Success(Right(value))
          case Failure(ex)    => Success(Left(ex))
        }
    )

  private def httpSpecificResultFrom: PartialFunction[Either[Throwable, PreferenceUpdateResult], Result] = {
    case Right(NewPreferenceCreated) => Created
  }

  private def httpResultFrom: PartialFunction[Either[Throwable, PreferenceUpdateResult], Result] = {
    case Right(PreferenceUpdated) | Right(NoTermsAndConditions) => Ok
    case Right(NoEmailForPreference) =>
      BadRequest(Json.obj("reason" -> "No email provided for user opting in for paperless"))
    case Right(LanguageNotUpdated)           => BadRequest(Json.obj("reason" -> "Unable to update language"))
    case Right(InvalidTermsAncConditions)    => BadRequest(Json.obj("reason" -> "Invalid terms and conditions type"))
    case Left(EntityBadRequest(message))     => BadRequest(message)
    case Left(PreferenceNotFound(message))   => NotFound(s"Preference not found: $message")
    case Left(EntityNotFound)                => NotFound("Entity not found")
    case Left(EntityUnauthorised(msg))       => Unauthorized(msg)
    case Left(EntityRequestServerError(msg)) => InternalServerError(s"Error, $msg")
    case Left(ex)                            => InternalServerError(s"Error, ${ex.getMessage}")
    case invalidMatch =>
      InternalServerError(Json.obj("reason" -> s"Invalid match condition '$invalidMatch'"))
  }

  private def httpResultFromError: PartialFunction[Throwable, Result] = {
    case e: IllegalArgumentException => BadRequest(Json.obj("reason" -> e.getMessage))
    case e: MissingBearerToken       => Unauthorized(e.getMessage)
    case e: NotFoundException        => NotFound(e.getMessage)
    case ex                          => InternalServerError(ex.getMessage)
  }

  def store(entityId: EntityId): Action[JsValue] = Action.async(parse.json) { implicit request =>
    authorised().retrieve(retrievals) { case affinityGroup ~ confidenceLevel =>
      save(entityId, Some(Credentials(affinityGroup, confidenceLevel)))
    }
  }

  // Will be called via Entity Resolver
  def optOut(entityId: EntityId): Action[JsValue] = Action.async(parse.json) { implicit request =>
    save(entityId, None)
  }

  private def save(entityId: EntityId, credentials: Option[Credentials])(implicit request: Request[JsValue]) =
    withJsonBody[TermsAndConditionsRequest] { genericTAndCsRequest =>
      termsAndConditionsService
        .handleTermsAndConditionsRequest(entityId, genericTAndCsRequest, credentials)
        .map {
          case NewPreferenceCreated                     => Created
          case PreferenceUpdated | NoTermsAndConditions => Ok
          case NoEmailForPreference =>
            BadRequest(Json.obj("reason" -> "No email provided for user opting in for paperless"))
          case LanguageNotUpdated        => BadRequest(Json.obj("reason" -> "Unable to update language"))
          case InvalidTermsAncConditions => BadRequest(Json.obj("reason" -> "Invalid terms and conditions type"))
          case invalidMatch =>
            InternalServerError(Json.obj("reason" -> s"Invalid match condition '$invalidMatch'"))
        }
    }.recover {
      case e: IllegalArgumentException =>
        BadRequest(Json.obj("reason" -> e.getMessage))
      case e: NotFoundException =>
        NotFound(e.getMessage)
      case ex =>
        logger.error(s"${ex.getMessage}")
        InternalServerError(ex.getMessage)
    }
}
