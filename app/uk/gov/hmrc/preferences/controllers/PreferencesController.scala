/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.controllers

import cats.data.EitherT
import cats.instances.future.*
import play.api.Logger
import play.api.libs.json.*
import play.api.mvc.{ Action, AnyContent, ControllerComponents, Request, Result }
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.auth.core.{ AuthConnector, AuthorisedFunctions }
import uk.gov.hmrc.crypto.{ Crypted, Decrypter, PlainText }
import uk.gov.hmrc.http.{ HeaderCarrier, NotFoundException }
import uk.gov.hmrc.paperless.controllers.model.PreferenceResponse
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendBaseController
import uk.gov.hmrc.preferences.connector.EntityResolverConnector
import uk.gov.hmrc.preferences.{ Auditable, PreferencesParams, ResolveParams, TaxIdParams }
import uk.gov.hmrc.preferences.controllers.model.Credentials
import uk.gov.hmrc.preferences.exceptions.{ EntityBadRequest, EntityNotFound, EntityRequestServerError, EntityResolverResponse, EntityUnauthorised, PreferenceNotFound }
import uk.gov.hmrc.preferences.model.{ Bounced, * }
import uk.gov.hmrc.preferences.repository.*
import uk.gov.hmrc.preferences.service.*

import javax.inject.{ Inject, Named, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

object FormattedUri {
  implicit val formats: OFormat[FormattedUri] = Json.format[FormattedUri]
}

case class FormattedUri(uri: String)

final case class EmailRequest(email: String, journey: Option[String] = None)

object EmailRequest {
  implicit val fmt: OFormat[EmailRequest] = Json.format[EmailRequest]
}

@Singleton
class PreferencesController @Inject() (
  individualPreferencesRepository: PreferencesRepository,
  val authConnector: AuthConnector,
  decrypter: Decrypter,
  changeEmailService: ChangeEmailService,
  pcnService: PreferencesChangedNotifierService,
  entityResolverConnector: EntityResolverConnector,
  preferenceService: PreferenceService,
  @Named("reoptinMajor") reoptinMajor: Int,
  @Named("gracePeriod") gracePeriod: Int,
  val audit: Auditable,
  override val controllerComponents: ControllerComponents
)(implicit ec: ExecutionContext)
    extends BackendBaseController with AuthorisedFunctions {

  private val logger: Logger = Logger(getClass)

  def findPreferencesByTaxIdOrAuth(query: PreferencesParams): Action[AnyContent] =
    Action.async { implicit request =>
      (query.taxIdParams, query.resolveParams) match {
        case (Some(TaxIdParams(tr, tid)), None)   => findByTaxId(tr, tid)
        case (None, Some(ResolveParams(resolve))) => findByAuth(resolve)
        case (None, None)                         => findByAuth(true)
        case _                                    => Future.successful(BadRequest("Invalid request format"))
      }
    }

  private def findByAuth(resolve: Boolean)(implicit hc: HeaderCarrier) = {
    val result = for {
      entityId       <- entityResolverConnector.getEntityIdByAuth(Some(resolve))
      preference     <- preferenceService.getPreferencesByEntityId(entityId)
      prefWithStatus <- mkResponse(preference)
    } yield prefWithStatus
    result.value.map(httpResponseFrom(_))
  }

  private def findByTaxId(tr: String, tid: String)(implicit hc: HeaderCarrier) = {
    val result = for {
      entityId   <- entityResolverConnector.getEntityIdByTaxId(taxRegime = tr, taxId = tid)
      preference <- preferenceService.getPreferencesByEntityId(entityId)
    } yield PreferenceResponse.from(preference, gracePeriod).copy(entityId = Some(entityId))
    result.value.map(httpResponseFrom(_))
  }

  private def httpResponseFrom: PartialFunction[Either[Throwable, PreferenceResponse], Result] = {
    case Right(preference)               => Ok(Json.toJson(preference))
    case Left(EntityBadRequest(message)) => BadRequest(message)
    case Left(PreferenceNotFound(message)) =>
      NotFound(if message != "" then s"Preference not found $message" else "Preference not found")
    case Left(EntityNotFound)                => NotFound("Entity not found")
    case Left(EntityUnauthorised(msg))       => Unauthorized(msg)
    case Left(EntityRequestServerError(msg)) => InternalServerError(s"Error, $msg")
    case Left(ex)                            => InternalServerError(s"Error, ${ex.getMessage}")
  }

  def mkResponse(
    preference: Preferences
  )(implicit hc: HeaderCarrier): EitherT[Future, Throwable, PreferenceResponse] = {
    val pr = PreferenceResponse.from(preference, gracePeriod).copy(entityId = Some(preference.entityId))
    EitherT(
      withCredentials(pr)
        .map(Right(_))
    )
  }

  private def withCredentials(response: PreferenceResponse)(implicit hc: HeaderCarrier): Future[PreferenceResponse] =
    authorised()
      .retrieve(Retrievals.affinityGroup and Retrievals.confidenceLevel) { case affinityGroup ~ confidenceLevel =>
        Future.successful(Some(Credentials(affinityGroup, confidenceLevel)))
      }
      .recoverWith { case _ =>
        Future.successful(Option.empty[Credentials])
      }
      .map { credentials =>
        PreferenceResponse.withStatus(response, credentials, reoptinMajor, gracePeriod)
      }

  def findPreferences(entityId: EntityId): Action[AnyContent] = Action.async { implicit request =>
    individualPreferencesRepository.findBy(entityId).flatMap {
      case Some(p) =>
        val response = PreferenceResponse.from(p, gracePeriod).copy(entityId = Some(entityId))
        withCredentials(response).map(p => Ok(Json.toJson(p)))

      case None => Future.successful(NotFound(s"Preferences for '$entityId' not found"))
    } recover { case e: NotFoundException =>
      NotFound(e.getMessage)
    }
  }

  def findPreferencesByEmail(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[EmailRequest] { emailRequest =>
      implicit val prf = PreferenceResponse.formats

      individualPreferencesRepository.findByEmail(emailRequest.email) map {
        case Nil => NotFound(s"Preferences for '${emailRequest.email}' not found")
        case preferences =>
          Ok(Json.toJson(PreferenceResponse.fromPreferences(preferences, gracePeriod)))
      } recover { case e: NotFoundException =>
        NotFound(e.getMessage)
      }
    }
  }

  def getLanguageOfEmail(emailAddress: String): Action[AnyContent] = Action.async { implicit request =>
    val decryptedEmail = decrypter.decrypt(Crypted.fromBase64(emailAddress)).value
    individualPreferencesRepository.findByEmail(decryptedEmail) map {
      case p :: Nil => (p.email orElse p.pendingEmail).flatMap(_.language).getOrElse(Language.English)
      case _        => Language.English
    } map { (lan: Language) =>
      Ok(Json.toJson(lan))
    } recover { case e: NotFoundException =>
      NotFound(e.getMessage)
    }
  }

  def updated(entityId: EntityId): Action[JsValue] = Action.async(parse.json) { implicit request =>
    logger.debug("PreferencesController.updated")
    (for {
      prefs <- individualPreferencesRepository.updated(entityId)
      _ <- prefs match {
             case Some(p) => pcnService.notifyPreferencesChanged(p._id, entityId, p.messageDeliveryFormat)
             case None    => throw PreferenceNotFound(s"No matching preference for $entityId")
           }
    } yield NoContent)
      .recover {
        case nfe: PreferenceNotFound =>
          NotFound(Json.obj("reason" -> s"${nfe.getMessage}"))
        case ex =>
          InternalServerError(ex.getMessage)
      }
  }

  def markForDeEnrolmentNew(params: TaxIdParams): Action[AnyContent] = Action.async { implicit request =>
    val result = for {
      entityId <- entityResolverConnector
                    .getEntityIdByTaxId(params.taxRegime, params.taxId)
                    .leftMap(httpErrorResultForEntity)
      result <- EitherT.liftF(processMarkForDeEnrolment(entityId, params.taxRegime))
    } yield result
    result.merge
  }

  private def processMarkForDeEnrolment(entityId: EntityId, taxRegime: String)(implicit r: Request[_]) =
    individualPreferencesRepository
      .findBy(entityId)
      .flatMap {
        case Some(pref: Preferences) if pref.markForDeEnrolment.isEmpty =>
          individualPreferencesRepository.markForDeEnrolment(entityId, taxRegime) flatMap (_ => Future.successful(Ok))
        case Some(_) => Future.successful(NoContent)
        case _       => Future.successful(NotFound(s"Unable to find the preferences for '$entityId''"))
      }
      .recover { case e: NotFoundException =>
        NotFound(e.getMessage)
      }

  def unsetDeEnrolmentNew(params: TaxIdParams): Action[AnyContent] = Action.async { implicit request =>
    val result = for {
      entityId <- entityResolverConnector
                    .getEntityIdByTaxId(params.taxRegime, params.taxId)
                    .leftMap(httpErrorResultForEntity)
      result <- EitherT.liftF(processUnsetDeEnrolment(entityId))
    } yield result
    result.merge
  }

  private def processUnsetDeEnrolment(entityId: EntityId)(implicit r: Request[_]) =
    individualPreferencesRepository
      .findBy(entityId)
      .flatMap {
        case Some(pref: Preferences) if pref.markForDeEnrolment.isDefined =>
          individualPreferencesRepository.unsetDeEnrolment(entityId) flatMap (_ => Future.successful(Ok))
        case Some(_) => Future.successful(NoContent)
        case _       => Future.successful(NotFound(s"Unable to find the preferences for '$entityId''"))
      }
      .recover { case e: NotFoundException =>
        NotFound(e.getMessage)
      }

  def setPendingEmail(entityId: EntityId): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[EmailRequest](changeEmailRequest =>
      changeEmailService
        .setPending(entityId, changeEmailRequest.email, changeEmailRequest.journey)
        .map(_ => Ok)
    )
      .recover {
        case NoEmailExists(msg)      => Conflict(Json.obj("reason" -> msg))
        case NoPreferenceExists(msg) => NotFound(Json.obj("reason" -> msg))
        case e: NotFoundException    => NotFound(e.getMessage)
      }
  }

  def initiatePendingEmail(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[EmailRequest](changeEmailRequest =>
      changeEmailService
        .setPendingEmail(changeEmailRequest.email, changeEmailRequest.journey)
        .value
        .map {
          case Right(_) => Ok
          case Left(e)  => NotFound(e.getMessage)
        }
    )
      .recover {
        case NoEmailExists(msg)      => Conflict(Json.obj("reason" -> msg))
        case NoPreferenceExists(msg) => NotFound(Json.obj("reason" -> msg))
        case e: NotFoundException    => NotFound(e.getMessage)
      }
  }

  def verifiedEmailAddressByRegime(regime: String, taxId: String): Action[AnyContent] = Action.async {
    implicit request =>
      logger.debug("PreferencesController.verifiedEmailAddressByRegime")
      for {
        entityId <- entityResolverConnector.getEntityIdByTaxId(taxRegime = regime, taxId = taxId).value
        email <- entityId match {
                   case Right(value) => verifiedEmailAddressTaxId(value)
                   case Left(error)  => Future.successful(processError(error))
                 }
      } yield email
  }
  def processError(error: EntityResolverResponse): Result = error match {
    case EntityBadRequest(message)     => BadRequest(message)
    case EntityNotFound                => NotFound("Entity not found")
    case EntityUnauthorised(msg)       => Unauthorized(msg)
    case EntityRequestServerError(msg) => InternalServerError(s"Error, $msg")
    case ex                            => InternalServerError(s"Error, ${ex.getMessage}")
  }

  private def verifiedEmailAddressTaxId(entityId: EntityId)(implicit hc: HeaderCarrier): Future[Result] = {
    def prefsNotFound(reasonMessage: String) =
      NotFound(Json.obj("reason" -> reasonMessage))

    def notContactable(reasonMessage: String) =
      prefsNotFound(reasonMessage)

    individualPreferencesRepository
      .findBy(entityId)
      .map {
        case Some(preference) =>
          preference.contactabilityStatus() match {
            case Contactable(emailAddress) => Ok(Json.toJson(Json.obj("email" -> emailAddress.email)))
            case OptedOut                  => notContactable("NOT_OPTED_IN")
            case Bounced                   => notContactable("EMAIL_ADDRESS_NOT_VERIFIED")
            case PendingVerification       => notContactable("EMAIL_ADDRESS_NOT_VERIFIED")
            case null                      => notContactable("OTHER_EXCEPTION")
          }
        case None =>
          prefsNotFound("PREFERENCES_NOT_FOUND")
      }
      .recover { case e: NotFoundException =>
        NotFound(e.getMessage)
      }
  }

}
