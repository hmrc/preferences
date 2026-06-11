/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.controllers.testonly

import javax.inject.{ Inject, Singleton }
import play.api.Logger
import play.api.libs.json._
import play.api.mvc.{ Action, AnyContent, ControllerComponents, Result }
import uk.gov.hmrc.http.{ HeaderCarrier, NotFoundException }
import uk.gov.hmrc.preferences.CurrentTime
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendBaseController
import uk.gov.hmrc.preferences.connector.Bounce
import uk.gov.hmrc.preferences.model._
import uk.gov.hmrc.preferences.repository._
import uk.gov.hmrc.preferences.service._
import uk.gov.hmrc.preferences.util.{ DateFormats, Dc }

import java.time.Instant
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class AdminController @Inject() (
  saIndividualPrintPreferencesRepository: PreferencesRepository,
  emailBounceQueueMonitorService: EmailBounceQueueMonitorService,
  verificationChaser: VerificationChaser,
  override val controllerComponents: ControllerComponents
)(implicit ec: ExecutionContext)
    extends BackendBaseController with CurrentTime {

  private val logger: Logger = Logger(getClass)

  def deletePreferences(entityId: EntityId): Action[AnyContent] = Action.async { implicit request =>
    saIndividualPrintPreferencesRepository
      .findBy(entityId)
      .flatMap {
        case Some(pref: Preferences) => saIndividualPrintPreferencesRepository.removeById(pref._id).map(_ => Ok)
        case _                       => Future.successful(Ok)
      }
      .recover { case e: NotFoundException =>
        NotFound(e.getMessage)
      }
  }

  def deleteAllPreferences(): Action[AnyContent] = Action.async { _ =>
    for {
      prefsRemoved <- saIndividualPrintPreferencesRepository.removeAll()
    } yield if (prefsRemoved) Ok else BadRequest
  }

  def expireEmailVerificationLink(entityId: EntityId): Action[AnyContent] = Action.async { implicit request =>
    saIndividualPrintPreferencesRepository
      .findBy(entityId)
      .flatMap {
        case None | Some(Preferences(_, _, _, _, None, _, _, _, _, _, _)) |
            Some(Preferences(_, _, _, _, Some(PendingEmailAddress(_, _, None, _, _, _)), _, _, _, _, _, _)) =>
          Future.successful(NotFound)
        case Some(
              Preferences(_, _, id, _, Some(PendingEmailAddress(_, _, Some(link), _, _, _)), _, _, _, _, _, _)
            ) =>
          saIndividualPrintPreferencesRepository
            .expireEmailVerificationLink(id, link)
            .map(done => if (done) Ok else InternalServerError)
      }
      .recover { case e: NotFoundException =>
        NotFound(e.getMessage)
      }
  }

  def verificationToken(entityId: EntityId): Action[AnyContent] = Action.async { implicit request =>
    saIndividualPrintPreferencesRepository
      .findBy(entityId)
      .map { preference =>
        preference
          .flatMap(pref => pref.pendingEmail.flatMap(pe => pe.verificationLink.map(link => Ok(link._id))))
          .getOrElse(NotFound("No verification link found"))
      }
      .recover { case e: NotFoundException =>
        NotFound(e.getMessage)
      }
  }

  def bounceEmail(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    import play.api.libs.functional.syntax._

    implicit val instantFormats: Format[Instant] = DateFormats.instantFormats

    implicit val reads: Reads[Bounce] = ((__ \ "emailAddress").read[String] and
      (__ \ "detected").readNullable[Instant] and
      (__ \ "code").readNullable[Int]) { (e, d, c) =>
      Bounce.apply(e, d.getOrElse(Dc.instantNow()), c)
    }
    withJsonBody[Bounce](
      emailBounceQueueMonitorService.markAsBounced(_).map(_ => NoContent).recover { case e: NotFoundException =>
        NotFound(e.getMessage)
      }
    )
  }

  def verifyEmail(entityId: EntityId): Action[AnyContent] = Action.async { implicit request =>
    saIndividualPrintPreferencesRepository
      .findBy(entityId)
      .flatMap {
        case Some(preference) if preference.pendingEmail.isDefined =>
          markEmailVerified(preference)
        case _ => Future.successful(NotFound)
      }
      .recover { case e: NotFoundException =>
        NotFound(e.getMessage)
      }
  }

  private def markEmailVerified(preference: Preferences)(implicit hc: HeaderCarrier): Future[Result] = {
    val pendingEmailAddress = preference.pendingEmail.get
    val link = pendingEmailAddress.verificationLink.get
    val language = pendingEmailAddress.language
    if (!link.isValid(Dc.instantNow())) {
      Future.successful(Gone("Email verification link has expired"))
    } else {
      saIndividualPrintPreferencesRepository
        .markEmailVerified(
          preference._id,
          pendingEmailAddress,
          language,
          getOptionalEvent(preference, pendingEmailAddress)
        )
        .map { _ =>
          NoContent
        } recover { case _: BrokenVerificationLinkException =>
        logger.error(s"Could not find print preference for email verification link: $link")
        BadRequest("Print preference did not exist")
      }
    }
  }

  private def getOptionalEvent(prefs: Preferences, pendingEmail: PendingEmailAddress): Option[Event] =
    withCurrentTime { time =>
      Some(
        EmailEvent(
          prefs.entityId,
          EmailEventType.EmailVerified,
          pendingEmail.email,
          Some(prefs.isPaperless),
          time
        )
      )
    }

  def processVerificationReminders(): Action[AnyContent] = Action.async { implicit request =>
    verificationChaser.chaseVerifications.map(_ => Ok)
  }

}
