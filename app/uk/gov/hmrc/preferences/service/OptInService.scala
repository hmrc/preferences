/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.service

import play.api.Logger
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.model.{ Audit, AuditAsMagnet, TransactionSuccess }
import uk.gov.hmrc.preferences._
import uk.gov.hmrc.preferences.connector.EmailConnector
import uk.gov.hmrc.preferences.controllers.model.{ Credentials, TermsAndConditionsRequest }
import uk.gov.hmrc.preferences.model.OptEventType.ReOptInModifiedJourney
import uk.gov.hmrc.preferences.model.TermsAndConditions._
import uk.gov.hmrc.preferences.model._
import uk.gov.hmrc.preferences.repository._

import java.time.Instant
import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class OptInService @Inject() (
  preferencesRepository: PreferencesRepository,
  timeSource: () => Instant,
  externalVerificationLink: EmailVerificationLink => String,
  emailConnector: EmailConnector,
  auditable: Auditable,
  changeEmailService: ChangeEmailService,
  pcnService: PreferencesChangedNotifierService
)(implicit ec: ExecutionContext)
    extends ServiceBase {

  private val logger: Logger = Logger(getClass)

  def optInToDigital(
    entityId: EntityId,
    email: String,
    terms: String,
    request: TermsAndConditionsRequest,
    credentials: Option[Credentials],
    bundle: OptInBundle
  )(implicit hc: HeaderCarrier): Future[PreferenceUpdateResult] =
    if (email.isEmpty) {
      Future.failed(new IllegalArgumentException("Email cannot be empty"))
    } else {
      existingPreferencesIfAny(entityId, email, terms).flatMap { maybePrefs =>
        withTransactionAuditing(entityId, email, terms, credentials, bundle) {
          createOrUpdatePreferencesWithOptIn(entityId, email, terms, request, maybePrefs, credentials, bundle)
        }.flatMap {
          case PreferenceUpdated =>
            auditOptingIn(entityId, email, maybePrefs, terms)
            Future.successful(PreferenceUpdated)
          case NewPreferenceCreated =>
            auditOptingIn(entityId, email, maybePrefs, terms)
            Future.successful(NewPreferenceCreated)
          case other => Future.successful(other)
        }
      }
    }

  private def existingPreferencesIfAny(entityId: EntityId, email: String, terms: String)(implicit
    hc: HeaderCarrier
  ): Future[Option[Preferences]] =
    preferencesRepository.findBy(entityId).flatMap[Option[Preferences]] {
      case p @ Some(Preferences(_, _, _, Some(verifiedEmail), _, _, _, _, _, _, _)) if verifiedEmail.email == email =>
        Future.successful(p)
      case p @ Some(Preferences(_, _, _, _, Some(pendingEmailAddress), _, _, _, _, _, _))
          if pendingEmailAddress.email == email =>
        Future.successful(p)
      case p @ (Some(Preferences(_, _, _, None, None, _, _, _, _, _, _)) | None) =>
        Future.successful(p)
      case p @ Some(Preferences(_, _, _, None, Some(_), _, _, _, _, _, _))
          if handleTerms(p.value.termsAndConditions, terms) =>
        Future.successful(p)
      case _ =>
        Future.failed(new IllegalArgumentException("Email cannot be changed while opting in."))
    }

  private def handleTerms(termsAndConditions: TermsAndConditions, terms: String): Boolean =
    (termsAndConditions, terms) match {
      case (TermsAndConditions(Accepted(_, _, _)), "generic") => true
      case _                                                  => false
    }

  private def createOrUpdatePreferencesWithOptIn(
    entityId: EntityId,
    email: String,
    terms: String,
    request: TermsAndConditionsRequest,
    maybePrefs: Option[Preferences],
    credentials: Option[Credentials],
    bundle: OptInBundle
  )(implicit hc: HeaderCarrier): () => Future[PreferenceUpdateResult] =
    (maybePrefs, terms) match {
      case (Some(prefs), _) if handlePrefs(prefs, email) =>
        val updatedPrefs = prefs.copy(
          termsAndConditions =
            prefs.termsAndConditions.withTerms(terms, Accepted(timeSource.apply(), bundle.eventType, bundle.optInPage))
        )
        optInFor(updatedPrefs, email, terms, request, bundle, credentials)
      case (Some(Preferences(_, _, _, None, Some(_), _, _, _, _, _, _)), _) =>
        () => changeEmailService.setPending(entityId, email).map(_ => PreferenceUpdated)
      case (None, GENERIC) =>
        val newPrefs = createNewGenericPreferences(entityId, bundle)
        optInFor(newPrefs, email, GENERIC, request, bundle, credentials)
      case _ =>
        () =>
          logger.error(s"Invalid terms and conditions type received at opt-in for entity:[$entityId]")
          Future.successful(InvalidTermsAncConditions)
    }

  private def handlePrefs(preferences: Preferences, email: String): Boolean =
    preferences match {
      case Preferences(_, _, _, Some(emailAddress), _, _, _, _, _, _, _) if emailAddress.email == email => true
      case Preferences(_, _, _, _, Some(emailAddress), _, _, _, _, _, _) if emailAddress.email == email => true
      case Preferences(_, _, _, None, None, _, _, _, _, _, _)                                           => true
      case _                                                                                            => false
    }

  private def createNewGenericPreferences(entityId: EntityId, bundle: OptInBundle): Preferences =
    Preferences(
      entityId,
      TermsAndConditions(
        generic = Accepted(timeSource.apply(), bundle.eventType, bundle.optInPage)
      )
    )

  private def optInFor(
    prefs: Preferences,
    email: String,
    terms: String,
    termsAndConditionsRequest: TermsAndConditionsRequest,
    bundle: OptInBundle,
    credentials: Option[Credentials]
  )(implicit hc: HeaderCarrier): () => Future[PreferenceUpdateResult] = { () =>
    val updateEmail = prefs.email.isEmpty && prefs.pendingEmail.isEmpty
    if (updateEmail) {
      val link = EmailVerificationLink.createOrUpdate(
        prefs,
        email,
        timeSource,
        termsAndConditionsRequest.returnText,
        termsAndConditionsRequest.returnUrl
      )
      val updatedPrefs =
        prefs.copy(
          pendingEmail =
            Some(PendingEmailAddress(email, None, Some(link), language = termsAndConditionsRequest.language))
        )
      doUpdate(
        updatedPrefs,
        bundle,
        email,
        terms,
        termsAndConditionsRequest.language,
        credentials,
        termsAndConditionsRequest.journey
      )
        .flatMap {
          case NewPreferenceCreated =>
            sendOptInMessages(prefs.entityId, email, externalVerificationLink(link))
            Future.successful(NewPreferenceCreated)
          case PreferenceUpdated =>
            sendOptInMessages(prefs.entityId, email, externalVerificationLink(link))
            Future.successful(PreferenceUpdated)
          case other =>
            Future.successful(other)
        }
        .recover { case _ =>
          ErrorResult
        }
    } else {
      doUpdate(
        prefs,
        bundle,
        email,
        terms,
        termsAndConditionsRequest.language,
        credentials,
        termsAndConditionsRequest.journey
      )
    }
  }

  private def doUpdate(
    prefs: Preferences,
    bundle: OptInBundle,
    email: String,
    terms: String,
    language: Option[Language],
    credentials: Option[Credentials],
    journey: Option[String]
  )(implicit hc: HeaderCarrier): Future[PreferenceUpdateResult] =
    updatePreferences(prefs, bundle, email, terms, language, credentials, journey) match {
      case Some(result) => result
      case None         => Future.successful(NoTermsAndConditions)
    }

  private def updatePreferences(
    prefs: Preferences,
    bundle: OptInBundle,
    email: String,
    terms: String,
    language: Option[Language],
    credentials: Option[Credentials],
    journey: Option[String]
  )(implicit hc: HeaderCarrier): Option[Future[PreferenceUpdateResult]] =
    (prefs.termsAndConditions.generic match {
      case Accepted(updatedAt, _, _) => Some(updatedAt)
      case _ =>
        logger.error("Preference record should have Accepted terms and conditions")
        None
    }).map { time =>
      if (terms == GENERIC) {
        val optInEvent = getOptInEvent(time, prefs, email, bundle.optInPage, bundle.eventType, language)
        val journeyEvent =
          journey.flatMap { _ =>
            getOptInEvent(time, prefs, email, bundle.optInPage, Some(ReOptInModifiedJourney), language)
          }
        optInWithEvent(optInEvent, prefs.copy(events = getAllEvents(prefs, journeyEvent)), credentials)
      } else {
        for {
          res <- preferencesRepository
                   .createOrUpdateTermsAndConditions(prefs, credentials)
          _ <- pcnService.notifyPreferencesChanged(prefs._id, prefs.entityId, prefs.messageDeliveryFormat)
        } yield res
      }
    }

  private def optInWithEvent(event: Option[Event], prefs: Preferences, credentials: Option[Credentials])(implicit
    hc: HeaderCarrier
  ): Future[PreferenceUpdateResult] =
    event match {
      case Some(optInEvent) =>
        val prefsWithEvents = prefs.copy(events = getAllEvents(prefs, Some(optInEvent)))
        for {
          res <- preferencesRepository
                   .createOrUpdateTermsAndConditions(prefsWithEvents, credentials)
          _ <- pcnService.notifyPreferencesChanged(prefs._id, prefs.entityId, prefs.messageDeliveryFormat)
        } yield res
      case None => Future.successful(LanguageNotUpdated)
    }

  private def getOptInEvent(
    time: Instant,
    prefs: Preferences,
    email: String,
    optInPage: Option[OptInPage],
    eventType: Option[OptEventType],
    language: Option[Language]
  ): Option[Event] =
    (optInPage, eventType, language) match {
      case (Some(page), Some(event), Some(lang)) =>
        Some(
          OptInEvent(
            event,
            page,
            prefs.entityId,
            time,
            lang,
            Some(prefs.isPaperless),
            Some(EmailAddress(email))
          )
        )
      case (Some(_), Some(_), None) =>
        logger.error("Could not create OptInEvent because language is not supplied")
        None
      case (Some(_), None, Some(_)) =>
        logger.error("Could not create OptInEvent because eventType is not supplied")
        None
      case (None, Some(_), _) =>
        logger.error("Could not create OptInEvent because optInPage is not supplied")
        None
      case _ =>
        logger.error(s"Could not create OptInEvent - some elements are missing: $optInPage :: $eventType")
        None
    }

  def setLanguage(entityId: EntityId, language: Option[Language]): Future[PreferenceUpdateResult] =
    for {
      result1 <- preferencesRepository.setUnverifiedEmailLanguage(entityId, language)
      result2 <- preferencesRepository.setVerifiedEmailLanguage(entityId, language)
    } yield (result1, result2) match {
      case (r1, r2) if r1 == r2   => r1
      case (_, PreferenceUpdated) => PreferenceUpdated
      case (PreferenceUpdated, _) => PreferenceUpdated
      case _                      => LanguageNotUpdated
    }

  private def withTransactionAuditing[A](
    entityId: EntityId,
    email: String,
    terms: String,
    credentials: Option[Credentials],
    bundle: OptInBundle
  )(auditBody: Audit.Body[A])(implicit hc: HeaderCarrier) = {
    val setPrintPrefsTransaction: AuditAsMagnet[A] = (
      "Set Print Preference",
      Map(
        "entityId"           -> entityId.value,
        "email"              -> email,
        "preference-digital" -> "true",
        "termsAndConditions" -> terms
      ) ++
        credentials
          .flatMap(_.affinityGroup)
          .fold(Map[String, String]())(a => Map("affinityGroup" -> a.toString)) ++
        credentials
          .map(_.confidenceLevel)
          .fold(Map[String, String]())(c => Map("confidenceLevel" -> c.toString)) ++
        bundle.optInPage.map(_.cohort).fold(Map[String, String]())(c => Map("optInPageCohort" -> s"$c")) ++
        bundle.optInPage.map(_.pageType).fold(Map[String, String]())(p => Map("optInPagePageType" -> s"$p")) ++
        bundle.optInPage
          .map(_.version)
          .fold(Map[String, String]())(v =>
            Map("optInPageMajor" -> s"${v.major}", "optInPageMinor" -> s"${v.minor}")
          ) ++
        bundle.eventType.fold(Map[String, String]())(e => Map("eventType" -> e.toString)),
      (_: A) => TransactionSuccess()
    )

    auditable.audit.as[A](setPrintPrefsTransaction)(auditBody)
  }

  private def auditOptingIn(entityId: EntityId, email: String, previousPreferences: Option[Preferences], terms: String)(
    implicit hc: HeaderCarrier
  ): Unit = {
    val optingIn = previousPreferences.forall(p =>
      p.termsAndConditions.findBy(terms) match {
        case Some(Refused(_, _, _)) | None => true
        case _                             => false
      }
    )

    if (optingIn) {
      auditable.sendDataEvent(
        transactionName = "Opt In Email Reminders",
        tags = Map("reason" -> "User Selected to Opt In"),
        detail = Map(
          "entityId"           -> entityId.value,
          "emailAddress"       -> email,
          "termsAndConditions" -> terms
        )
      )
    }
  }

  private def sendOptInMessages(entityId: EntityId, email: String, link: String)(implicit
    hc: HeaderCarrier
  ): Future[Unit] =
    emailConnector
      .sendDigitalOptInEmailVerification(email, link, force = false)
      .recover { case e =>
        logger.error(
          s"Could not contact EMAIL service and send verification link for ${entityId.toString}: ${e.getMessage}"
        )
      }
      .map { _ =>
        auditable.sendDataEvent(
          transactionName = "Email Verification Link Sent",
          detail = Map(
            "entityId"         -> entityId.value,
            "emailAddress"     -> email,
            "verificationLink" -> link,
            "verificationType" -> "optedIn"
          )
        )
      }
}
