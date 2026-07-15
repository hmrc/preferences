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

package uk.gov.hmrc.preferences.controllers

import org.apache.pekko.util.ByteString
import org.bson.types.ObjectId
import play.api.Logger
import play.api.http.{ HeaderNames, HttpEntity, MimeTypes }
import play.api.libs.json.{ Format, JsObject, JsValue, Json, OFormat, Writes }
import play.api.mvc.{ Action, Codec, ControllerComponents, Headers, Request, ResponseHeader, Result }
import uk.gov.hmrc.auth.core.AffinityGroup.{ Individual, Organisation }
import uk.gov.hmrc.domain.TaxIds.TaxIdWithName
import uk.gov.hmrc.domain.{ Nino, SaUtr, SimpleName, TaxIdentifier }
import uk.gov.hmrc.http.{ HeaderCarrier, NotFoundException }
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendBaseController
import uk.gov.hmrc.preferences.*
import uk.gov.hmrc.preferences.connector.{ CitizenDetailsConnector, EntityResolverConnector, MessageConnector, TaxpayerConnector }
import uk.gov.hmrc.preferences.controllers.ApiVersion.{ v1, v2 }
import uk.gov.hmrc.preferences.controllers.model.EmailToken
import uk.gov.hmrc.preferences.model.Language.{ English, Welsh }
import uk.gov.hmrc.preferences.model.MessageDeliveryFormat.Digital
import uk.gov.hmrc.preferences.model.VerifyStatus.{ AlreadyVerified, AlreadyVerifiedLinks }
import uk.gov.hmrc.preferences.model.{ Language, * }
import uk.gov.hmrc.preferences.repository.*
import uk.gov.hmrc.preferences.service.{ ETMPService, PreferencesChangedNotifierService }
import uk.gov.hmrc.preferences.templates.CustomerType.NinoPTA
import uk.gov.hmrc.preferences.templates.{ CustomerType, TemplateHelper, TemplateId }

import java.time.Instant
import java.util.UUID.randomUUID
import javax.inject.{ Inject, Named, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class EmailVerificationController @Inject() (
  now: () => Instant,
  prefsRepo: PreferencesRepository,
  entityResolverConnector: EntityResolverConnector,
  taxpayerNameConnector: TaxpayerConnector,
  citizenDetailsConnector: CitizenDetailsConnector,
  messageConnector: MessageConnector,
  templateHelper: TemplateHelper,
  externalVerificationLink: EmailVerificationLink => String,
  auditable: Auditable,
  etmpService: ETMPService,
  pcnService: PreferencesChangedNotifierService,
  override val controllerComponents: ControllerComponents,
  @Named("etmpUpdate") etmpUpdateFlag: Boolean
)(implicit ec: ExecutionContext)
    extends BackendBaseController with CurrentTime {

  private val logger: Logger = Logger(getClass)

  def verifyEmail(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[EmailToken] {
      case token if token.isValid => verifyTokenAgainstPendingEmails(token)
      case invalidToken =>
        logger.warn(s"Token $invalidToken is not a valid format")
        Future.successful(BadRequest(s"Token $invalidToken is not a valid format"))
    }
  }

  private[controllers] def verifyTokenAgainstPendingEmails(
    token: EmailToken
  )(implicit hc: HeaderCarrier): Future[Result] =
    prefsRepo
      .findByVerificationToken(token)
      .flatMap(pref => markVerified(token, pref))
      .recover { case e: NotFoundException =>
        NotFound(e.getMessage)
      }

  private def markVerified(token: EmailToken, preference: Option[Preferences])(implicit
    hc: HeaderCarrier
  ): Future[Result] = {
    val Token = token.token
    preference match {
      case None => Future.successful(Conflict(s"Token $token not found"))
      case Some(
            Preferences(
              _,
              _,
              _,
              Some(EmailAddress(_, _, _, _, Some(EmailVerificationLink(Token, _, _, _)), _)),
              Some(_),
              _,
              _,
              _,
              _,
              _,
              _
            )
          ) =>
        Future.successful(
          Result(
            header = ResponseHeader(CONFLICT),
            body = HttpEntity.Strict(
              ByteString(
                Json
                  .toJson(
                    EmailVerification(AlreadyVerified, s"Token $token for already verified when expecting a new token")
                  )
                  .toString
              ),
              Some(ApiVersion.v2.header)
            )
          )
        )
      case Some(
            Preferences(
              _,
              _,
              _,
              Some(EmailAddress(_, _, _, _, Some(link @ EmailVerificationLink(Token, _, _, _)), _)),
              None,
              _,
              _,
              _,
              _,
              _,
              _
            )
          ) =>
        Future.successful(alreadyVerifiedResultV2(token, link))
      case Some(
            Preferences(
              identifier,
              _,
              id,
              _,
              Some(
                pendingEmail @ PendingEmailAddress(
                  _,
                  _,
                  Some(link @ EmailVerificationLink(Token, _, _, _)),
                  _,
                  language,
                  _
                )
              ),
              _,
              _,
              _,
              _,
              _,
              _
            )
          ) =>
        saveVerifiedEmail(id, identifier, link, pendingEmail, language, getOptionalEvent(preference, pendingEmail))
          .andThen { case _ =>
            sendSecureMessage(token)
          }
      case nonMatchingPrefs =>
        throw new IllegalStateException(
          s"Found prefs with verification link '$token', but returned preferences did not contain it: $nonMatchingPrefs"
        )
    }
  }

  private def getOptionalEvent(prefs: Option[Preferences], pendingEmail: PendingEmailAddress): Option[Event] =
    withCurrentTime { time =>
      prefs.flatMap { p =>
        Some(EmailEvent(p.entityId, EmailEventType.EmailVerified, pendingEmail.email, paperless = Some(true), time))
      }
    }

  private def alreadyVerifiedResultV2(token: EmailToken, link: EmailVerificationLink): Result =
    link match {
      case EmailVerificationLink(_, _, Some(returnLinkText), Some(returnUrl)) =>
        Ok(
          Json
            .toJson(
              EmailVerification(
                AlreadyVerifiedLinks,
                s"Token $token already verified",
                Some(returnLinkText),
                Some(returnUrl)
              )
            )
        ).as(ApiVersion.v2.header)
      case _ =>
        Ok(
          Json
            .toJson(
              EmailVerification(AlreadyVerified, s"Token $token already verified")
            )
        ).as(ApiVersion.v2.header)
    }

  private[controllers] def sendSecureMessage(token: EmailToken)(implicit hc: HeaderCarrier): Future[Unit] =
    for {
      preferences <- prefsRepo.findByVerificationToken(token)
      taxId <- preferences.fold(throw new Exception(s"Preference not found for $token"))(p =>
                 entityResolverConnector.getTaxId(p.entityId)
               )
      messageSent <- sendMessagePossibly(preferences.flatMap(_.userType), preferences.flatMap(_.email), taxId)
    } yield messageSent

  private def sendMessagePossibly(userType: Option[UserType], emailAddress: Option[EmailAddress], taxId: TaxId)(implicit
    hc: HeaderCarrier
  ): Future[Unit] =
    (taxId.sautr, taxId.nino, taxId.hmrcMtdItsa, userType, emailAddress) match {
      case (_, _, _, Some(userType), _) if userType.affinityGroup.isEmpty =>
        logger.error("Could not Send Message Notification because userType.affinityGroup is missing in preferences")
        Future.successful(())
      case (_, _, Some(itsa), Some(userType), Some(email)) =>
        sendMessage(taxId, userType, email)
      case (Some(sautr), _, _, Some(userType), Some(email)) if userType.affinityGroup.get == Individual =>
        sendMessage(taxId, userType, email)
      case (_, Some(nino), _, Some(userType), Some(email)) if userType.affinityGroup.get == Individual =>
        sendMessage(taxId, userType, email)
      case (_, _, _, Some(userType), Some(_)) =>
        logger.error(
          s"Could not Send Message Notification because affinityGroup is ${userType.affinityGroup.get}"
        )
        Future.successful(())
      case (_, _, _, Some(_), None) =>
        logger.error("Could not Send Message Notification because email is missing in preferences")
        Future.successful(())
      case (_, _, _, None, Some(_)) =>
        logger.error("Could not Send Message Notification because userType is missing in preferences")
        Future.successful(())
      case (_, _, _, None, None) =>
        logger.error("Could not Send Message Notification because userType and email are missing in preferences")
        Future.successful(())
      case _ =>
        logger.error("Could not Send Message Notification because taxId is missing in preferences")
        Future.successful(())
    }

  private def sendMessage(taxId: TaxId, userType: UserType, email: EmailAddress)(implicit hc: HeaderCarrier) =
    for {
      taxpayerName <- getTaxPayerName(taxId)
      message = messageBuilder(
                  if (userType.affinityGroup.get == Organisation) CustomerType.BTA else CustomerType.PTA,
                  taxId,
                  email,
                  taxpayerName
                )
      _ <- messageConnector.postMessage(message)
    } yield ()

  /*ToDo when ITSA provide an API for retrieving the taxpayer name this should be moved to a connector
      First see if we can get a taxpayer name from the SA if not try the NINO
   */
  private def getTaxPayerName(taxId: TaxId)(implicit hc: HeaderCarrier): Future[Option[TaxpayerName]] = {
    def helper[T <: TaxIdWithName](id: Option[T], f: T => Future[Option[TaxpayerName]]): Future[Option[TaxpayerName]] =
      id match {
        case Some(v) => f(v)
        case None    => Future.successful(None)
      }
    helper(taxId.sautr.map(SaUtr(_)), taxpayerNameConnector.getTaxpayerName(_: SaUtr)(hc)).flatMap {
      case a @ Some(_) => Future.successful(a)
      case _           => helper(taxId.nino.map(Nino(_)), citizenDetailsConnector.getTaxpayerName(_: Nino)(hc))
    }
  }

  def messageBuilder(
    customerType: CustomerType,
    taxId: TaxId,
    emailAddress: EmailAddress,
    taxpayerName: Option[TaxpayerName]
  ): JsValue = {
    import MessageFormat._
    val externalRef = ExternalRef(randomUUID.toString, "preferences")
    val taxIdentifier: TaxIdWithName = taxIdWithName(taxId)
    val regime = TaxEntity.idToRegime(taxIdentifier)
    Json.obj(
      "externalRef" -> externalRef,
      "recipient"   -> TaxEntity(regime, taxIdentifier, Option(emailAddress.email), taxpayerName),
      "messageType" -> TemplateId.DIGITAL_OPTIN,
      "content" ->
        List(
          Content(lang = English, subject = "Your online tax letters", body = templateHelper.getMessageContent()),
          Content(
            lang = Welsh,
            subject = "Eich llythyrau treth ar-lein",
            body = templateHelper.getWelshMessageContent()
          )
        )
    )
  }

  private def taxIdWithName(taxId: TaxId): TaxIdWithName = {
    def createTaxID(n: String, v: String) = new TaxIdentifier with SimpleName {
      override val name: String = n
      override def value: String = v
    }
    taxId match {
      case TaxId(_, _, _, Some(itsa))  => createTaxID("HMRC-MTD-IT", itsa)
      case TaxId(_, Some(saUtr), _, _) => createTaxID("sautr", saUtr)
      case TaxId(_, _, nino, _)        => createTaxID("nino", nino.getOrElse(""))
    }
  }

  private def saveVerifiedEmail(
    id: ObjectId,
    entityId: EntityId,
    link: EmailVerificationLink,
    pendingEmail: PendingEmailAddress,
    language: Option[Language],
    event: Option[Event]
  )(implicit hc: HeaderCarrier): Future[Result] =
    if (!link.isValid(now())) {
      Future.successful(Gone("Email verification link has expired"))
    } else {
      (for {
        _ <- prefsRepo.markEmailVerified(id, pendingEmail, language, event)
        _ <- pcnService.notifyPreferencesChanged(id, entityId, Digital)
        _ <- if (etmpUpdateFlag) {
               etmpService.checkAndUpdateETMP(entityId, paperless = true, eventId = None)
             } else { Future.successful(()) }
      } yield {
        auditEmailVerified(entityId, link, pendingEmail.email)
        link match {
          case EmailVerificationLink(_, _, Some(returnText), Some(returnUrl)) =>
            Created(s"""{"returnLinkText": "$returnText", "returnUrl": "$returnUrl"}""")
          case _ => NoContent
        }
      }).recover {
        case _: BrokenVerificationLinkException =>
          logger.error(s"Could not find print preference for email verification link: $link")
          InternalServerError
        case ex =>
          logger.error(s"${ex.toString}")
          InternalServerError(s"${ex.getMessage}")
      }
    }

  private def auditEmailVerified(entityId: EntityId, link: EmailVerificationLink, emailAddress: String)(implicit
    hc: HeaderCarrier
  ): Unit =
    auditable.sendDataEvent(
      "Email Verified",
      detail = Map(
        "entityId"         -> entityId.value,
        "emailAddress"     -> emailAddress,
        "verificationLink" -> externalVerificationLink(link)
      )
    )
}

enum ApiVersion(val header: String):
  case v1 extends ApiVersion("application/json")
  case v2 extends ApiVersion("application/verify-email.v2+json")
