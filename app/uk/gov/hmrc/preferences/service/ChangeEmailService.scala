/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.service

import cats.data.EitherT

import javax.inject.{ Inject, Singleton }
import play.api.Logger
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.preferences.*
import uk.gov.hmrc.preferences.connector.{ EmailConnector, EntityResolverConnector }
import uk.gov.hmrc.preferences.model.EmailEventType.{ EmailBounceJourney, EmailReVerifyJourney }
import uk.gov.hmrc.preferences.model.*
import uk.gov.hmrc.preferences.repository.PreferencesRepository
import java.time.Instant
import scala.annotation.unused
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class ChangeEmailService @Inject() (
  repo: PreferencesRepository,
  emailConnector: EmailConnector,
  externaliseLink: EmailVerificationLink => String,
  auditable: Auditable,
  timeSource: () => Instant,
  entityResolverConnector: EntityResolverConnector
)(implicit ec: ExecutionContext)
    extends CurrentTime {

  private val logger: Logger = Logger(getClass)

  def setPending(entityId: EntityId, changeTo: String, journey: Option[String] = None)(implicit
    hc: HeaderCarrier
  ): Future[Unit] = {

    def sendChangeOfAddressEmails(
      existing: Option[EmailAddress],
      pendingEmailAddress: PendingEmailAddress
    ): Future[Unit] = {
      val emailsToSend =
        sendVerificationLinkToNewEmailAddress(pendingEmailAddress) ::
          existing.toList.map { verified =>
            emailConnector.sendEmailChangedNotification(verified.email)
          }

      Future
        .sequence(emailsToSend)
        .map { _ =>
          ()
        }
        .recover { case ex => logger.error("errors sending change email address notifications", ex) }
    }

    def sendVerificationLinkToNewEmailAddress(pendingEmailAddress: PendingEmailAddress): Future[Unit] = {
      val link = externaliseLink(pendingEmailAddress.verificationLink.get)
      emailConnector.sendChangedEmailAddressVerification(pendingEmailAddress.email, link).map { _ =>
        doAudit(entityId, pendingEmailAddress.email, link, "emailAddressChanged")
      }
    }

    // TODO: is this verificationType correct?
    def doAudit(entityId: EntityId, email: String, link: String, @unused verificationType: String): Unit =
      auditable.sendDataEvent(
        transactionName = "Email Verification Link Sent",
        detail = Map(
          "entityId"         -> entityId.value,
          "emailAddress"     -> email,
          "verificationLink" -> link,
          "verificationType" -> "emailAddressChanged"
        )
      )

    def sendDigitalOptInEmailVerification(entityId: EntityId, link: String): Future[Unit] =
      emailConnector
        .sendDigitalOptInEmailVerification(changeTo, link, true)
        .map { _ =>
          doAudit(entityId, changeTo, link, "optedIn")
        }
        .recover { case ex =>
          logger.error(s"Could not contact EMAIL service and send verification link for ${entityId.toString}", ex)
        }

    lazy val loadPreferences: Future[Preferences] =
      repo.findBy(entityId).flatMap {
        case Some(p) if p.mostRecentlyAddedEmail.isDefined => Future.successful(p)
        case Some(_) =>
          Future
            .failed(NoEmailExists("changing email address when preference has no existing verified or pending email"))
        case _ => Future.failed(NoPreferenceExists(s"no preferences == no change of email for entity id $entityId"))
      }

    def isResend(previousPrefs: Preferences): Boolean =
      previousPrefs.email.isEmpty ||
        previousPrefs.email.exists(_.email == changeTo) ||
        previousPrefs.pendingEmail.exists(_.email == changeTo)

    def notifyRelevantEmailAddresses(prefs: Preferences, pending: PendingEmailAddress) = {
      val link = externaliseLink(pending.verificationLink.get)

      if (isResend(prefs)) sendDigitalOptInEmailVerification(prefs.entityId, link)
      else sendChangeOfAddressEmails(prefs.email, pending)
    }

    def getLanguageFromPreference(preferences: Preferences): Option[Language] =
      (preferences.email, preferences.pendingEmail) match {
        case (Some(email), _)        => email.language
        case (_, Some(pendingEmail)) => pendingEmail.language
        case _                       => None
      }

    val journeyEvents: Map[String, EmailEventType] = Map(
      "re-verify" -> EmailReVerifyJourney,
      "bounce"    -> EmailBounceJourney
    )

    def getOptionalEvent(prefs: Preferences, pendingEmail: PendingEmailAddress): Seq[Event] =
      withCurrentTime { time =>
        val events = Seq(
          EmailEvent(
            prefs.entityId,
            EmailEventType.EmailChanged,
            pendingEmail.email,
            Some(prefs.isPaperless),
            time
          )
        )
        events ++ journey.map { e =>
          EmailEvent(prefs.entityId, journeyEvents(e), pendingEmail.email, Some(prefs.isPaperless), time)
        }
      }

    for {
      prefs <- loadPreferences
      language = getLanguageFromPreference(prefs)
      pending = prefs.resetPending(changeTo, timeSource, None, None, language)
      _ <- repo.setUnverifiedEmailAddress(entityId, pending, getOptionalEvent(prefs, pending))
      _ <- notifyRelevantEmailAddresses(prefs, pending)
    } yield ()
  }

  def setPendingEmail(changeTo: String, journey: Option[String] = None)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Throwable, Unit] =
    for {
      entityId <- entityResolverConnector.getEntityIdByAuth()
      prefs    <- loadPreferences(entityId)
      language = getLanguageFromPreference(prefs)
      pending = prefs.resetPending(changeTo, timeSource, None, None, language)
      _ <- EitherT.liftF(repo.setUnverifiedEmailAddress(entityId, pending, getOptionalEvent(prefs, pending, journey)))
      _ <- EitherT.liftF(notifyRelevantEmailAddresses(entityId, prefs, pending, changeTo))
    } yield ()

  private def loadPreferences(
    entityId: EntityId
  )(implicit headerCarrier: HeaderCarrier): EitherT[Future, Throwable, Preferences] =
    EitherT(repo.findBy(entityId).map {
      case Some(p) if p.mostRecentlyAddedEmail.isDefined => Right(p)
      case Some(_) =>
        Left(NoEmailExists("changing email address when preference has no existing verified or pending email"))
      case _ => Left(NoPreferenceExists(s"no preferences == no change of email for entity id $entityId"))
    })

  private def getLanguageFromPreference(preferences: Preferences): Option[Language] =
    preferences.email.flatMap(_.language).orElse(preferences.pendingEmail.flatMap(_.language))

  private def isResend(previousPrefs: Preferences, changeTo: String): Boolean =
    previousPrefs.email.isEmpty ||
      previousPrefs.email.exists(_.email == changeTo) ||
      previousPrefs.pendingEmail.exists(_.email == changeTo)

  private def notifyRelevantEmailAddresses(
    entityId: EntityId,
    prefs: Preferences,
    pending: PendingEmailAddress,
    changeTo: String
  )(implicit headerCarrier: HeaderCarrier) = {
    val link: String = pending.verificationLink.fold(
      throw new RuntimeException("Verification link can not be empty here")
    )(externaliseLink)

    if (isResend(prefs, changeTo)) sendDigitalOptInEmailVerification(prefs.entityId, link, changeTo)
    else sendChangeOfAddressEmails(entityId, prefs.email, pending)
  }

  private def sendDigitalOptInEmailVerification(entityId: EntityId, link: String, changeTo: String)(implicit
    headerCarrier: HeaderCarrier
  ): Future[Unit] =
    emailConnector
      .sendDigitalOptInEmailVerification(changeTo, link, true)
      .map { _ =>
        doAudit(entityId, changeTo, link, "optedIn")
      }
      .recover { case ex =>
        logger.error("could not contact EMAIL service and send verification link for ${entityId.toString}", ex)
      }

  def doAudit(entityId: EntityId, email: String, link: String, @unused verificationType: String)(implicit
    headerCarrier: HeaderCarrier
  ): Unit =
    auditable.sendDataEvent(
      transactionName = "Email Verification Link Sent",
      detail = Map(
        "entityId"         -> entityId.value,
        "emailAddress"     -> email,
        "verificationLink" -> link,
        "verificationType" -> "emailAddressChanged"
      )
    )

  private def sendChangeOfAddressEmails(
    entityId: EntityId,
    existing: Option[EmailAddress],
    pendingEmailAddress: PendingEmailAddress
  )(implicit headerCarrier: HeaderCarrier): Future[Unit] = {
    val emailsToSend =
      sendVerificationLinkToNewEmailAddress(entityId, pendingEmailAddress) ::
        existing.toList.map { verified =>
          emailConnector.sendEmailChangedNotification(verified.email)
        }

    Future
      .sequence(emailsToSend)
      .map { _ =>
        ()
      }
      .recover { case ex => logger.error("errors sending change email address notifications", ex) }
  }

  private def sendVerificationLinkToNewEmailAddress(entityId: EntityId, pendingEmailAddress: PendingEmailAddress)(
    implicit headerCarrier: HeaderCarrier
  ): Future[Unit] = {
    val link = externaliseLink(pendingEmailAddress.verificationLink.get)
    emailConnector.sendChangedEmailAddressVerification(pendingEmailAddress.email, link).map { _ =>
      doAudit(entityId, pendingEmailAddress.email, link, "emailAddressChanged")
    }
  }

  private def getOptionalEvent(
    prefs: Preferences,
    pendingEmail: PendingEmailAddress,
    journey: Option[String]
  ): Seq[Event] =
    withCurrentTime { time =>
      val emailChangedEvent = EmailEvent(
        prefs.entityId,
        EmailEventType.EmailChanged,
        pendingEmail.email,
        Some(prefs.isPaperless),
        time
      )

      val journeyEvents: Map[String, EmailEventType] = Map(
        "re-verify" -> EmailReVerifyJourney,
        "bounce"    -> EmailBounceJourney
      )

      val journeyEvent = journey.flatMap(e =>
        journeyEvents
          .get(e)
          .map(action => EmailEvent(prefs.entityId, action, pendingEmail.email, Some(prefs.isPaperless), time))
      )
      emailChangedEvent +: journeyEvent.toSeq
    }

}

final case class NoEmailExists(msg: String) extends Exception(msg)

final case class NoPreferenceExists(msg: String) extends Exception(msg)
