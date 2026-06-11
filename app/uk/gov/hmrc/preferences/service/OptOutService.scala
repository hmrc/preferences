/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.service

import play.api.Logger
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.preferences.CurrentTime
import uk.gov.hmrc.play.audit.model.{ AuditAsMagnet, TransactionSuccess }
import uk.gov.hmrc.preferences.Auditable
import uk.gov.hmrc.preferences.connector.EmailConnector
import uk.gov.hmrc.preferences.controllers.model.Credentials
import uk.gov.hmrc.preferences.model.OptEventType.{ AdminOptOut, CustomerOptOut, CustomerReOptOut, SystemOptOut }
import uk.gov.hmrc.preferences.model.MessageDeliveryFormat.Paper
import uk.gov.hmrc.preferences.model.TermsAndConditions.{ Accepted, GENERIC, Refused }
import uk.gov.hmrc.preferences.model.*
import uk.gov.hmrc.preferences.repository.PreferencesMetricsRepository.*
import uk.gov.hmrc.preferences.repository.*

import java.time.Instant
import javax.inject.{ Inject, Named, Singleton }
import scala.annotation.unused
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class OptOutService @Inject() (
  etmpService: ETMPService,
  preferencesRepository: PreferencesRepository,
  preferencesMetricsRepository: PreferencesMetricsRepository,
  emailConnector: EmailConnector,
  pcnService: PreferencesChangedNotifierService,
  auditable: Auditable,
  @Named("etmpUpdate") etmpUpdateFlag: Boolean
)(implicit ec: ExecutionContext)
    extends ServiceBase with CurrentTime {

  private val logger: Logger = Logger(getClass)

  def optOutOfDigital(
    entityId: EntityId,
    reason: Option[String],
    terms: String,
    credentials: Option[Credentials],
    bundle: OptInBundle,
    lang: Option[Language] = None,
    surveyType: Option[SurveyType] = None
  )(implicit hc: HeaderCarrier): Future[PreferenceUpdateResult] = {

    val setPrintPrefsTransaction: AuditAsMagnet[Future[PreferenceUpdateResult]] = (
      "Set Print Preference",
      Map(
        "entityId"           -> entityId.value,
        "preference-digital" -> "false",
        "preference-reason"  -> reason.getOrElse("User Selected to Opt Out"),
        "termsAndConditions" -> terms
      ) ++
        credentials
          .flatMap(_.affinityGroup)
          .fold(Map[String, String]())(a => Map("affinityGroup" -> a.toString)) ++
        credentials
          .map(_.confidenceLevel)
          .fold(Map[String, String]())(c => Map("confidenceLevel" -> c.toString)) ++
        bundle.optInPage.map(_.cohort).fold(Map.empty[String, String])(c => Map("optInPageCohort" -> s"$c")) ++
        bundle.optInPage.map(_.pageType).fold(Map.empty[String, String])(p => Map("optInPagePageType" -> s"$p")) ++
        bundle.optInPage
          .map(_.version)
          .fold(Map[String, String]())(v =>
            Map("optInPageMajor" -> s"${v.major}", "optInPageMinor" -> s"${v.minor}")
          ) ++
        bundle.eventType.fold(Map[String, String]())(e => Map("eventType" -> e.toString)),
      (_: Future[PreferenceUpdateResult]) => TransactionSuccess()
    )

    auditable.audit.as(setPrintPrefsTransaction) { () =>
      preferencesRepository.findBy(entityId).flatMap { maybePrefs =>
        createOrUpdatePrefsWithOptOut(entityId, terms, bundle, credentials, reason, maybePrefs, lang, surveyType)
      }
    }
  }

  private def createOrUpdatePrefsWithOptOut(
    entityId: EntityId,
    terms: String,
    bundle: OptInBundle,
    creds: Option[Credentials],
    reason: Option[String],
    maybePrefs: Option[Preferences],
    lang: Option[Language],
    surveyType: Option[SurveyType]
  )(implicit hc: HeaderCarrier): Future[PreferenceUpdateResult] =
    withCurrentTime { time =>
      (maybePrefs, terms) match {
        case (Some(prefs), _) => optOut(prefs, time, terms, creds, reason, bundle, lang, surveyType)
        case (None, GENERIC) =>
          val genericPrefs = createNewGenericPreferences(entityId, time, bundle)
          optOut(genericPrefs, time, terms, creds, reason, bundle, lang, surveyType)
        case _ => Future.successful(NoTermsAndConditions)
      }
    }

  private def createNewGenericPreferences(entityId: EntityId, time: Instant, bundle: OptInBundle): Preferences =
    Preferences(entityId, TermsAndConditions(generic = Refused(time, bundle.eventType, bundle.optInPage)))

  private def hasAcceptedTermsFor(terms: String, termsAndConditions: TermsAndConditions): Boolean =
    termsAndConditions.findBy(terms) match {
      case Some(Accepted(_, _, _)) => true
      case _                       => false
    }

  private def getOptOutEvent(
    optInPage: Option[OptInPage],
    eventType: Option[OptEventType],
    language: Option[Language],
    prefs: Preferences,
    time: Instant
  ): Option[Event] =
    (optInPage, eventType, language) match {
      case (Some(page), Some(CustomerOptOut), Some(lang)) =>
        Some(CustomerOptOutEvent(CustomerOptOut, page, prefs.entityId, time, lang, paperless = Some(false)))
      case (Some(page), Some(CustomerReOptOut), Some(lang)) =>
        Some(CustomerOptOutEvent(CustomerReOptOut, page, prefs.entityId, time, lang, paperless = Some(false)))
      case (_, Some(AdminOptOut), _) =>
        Some(AdminOptOutEvent(AdminOptOut, prefs.entityId, time, paperless = Some(false)))
      case (_, Some(SystemOptOut), _) =>
        Some(SystemOptOutEvent(SystemOptOut, prefs.entityId, time, paperless = Some(false)))

      case (Some(_), Some(_), None) =>
        logger.error("Could not create OptOutEvent because language is not supplied")
        None
      case (Some(_), None, Some(_)) =>
        logger.error("Could not create OptOutEvent because eventType is not supplied")
        None
      case (None, Some(_), _) =>
        logger.error("Could not create OptOutEvent because optInPage is not supplied")
        None
      case evt =>
        logger.error(s"Could not save OptOutEvent - some elements are missing $evt")
        None
    }

  private def optOut(
    inputprefs: Preferences,
    time: Instant,
    terms: String,
    credentials: Option[Credentials],
    reason: Option[String],
    bundle: OptInBundle,
    lang: Option[Language],
    surveyType: Option[SurveyType]
  )(implicit hc: HeaderCarrier): Future[PreferenceUpdateResult] = {

    val prefs = inputprefs
      .copy(surveys = (surveyType, inputprefs.surveys) match {
        case (None, oss)         => oss
        case (Some(t), None)     => Option(List(Survey(t, time)))
        case (Some(t), Some(ss)) => Option(Survey(t, time) :: ss)
      })

    val userInitiatedOptOut = if (reason.isDefined) false else true

    val hasPreviouslyAccepted = hasAcceptedTermsFor(terms, prefs.termsAndConditions)

    val sendEmail = userInitiatedOptOut && hasPreviouslyAccepted

    def createOptOutWithEvent(event: Option[Event], terms: TermsAndConditions) =
      optOutWithEvent(event, prefs, terms, credentials, bundle.eventType != Some(SystemOptOut))

    for {
      _ <- recordGenericOptOutMetric(userInitiatedOptOut, hasPreviouslyAccepted, terms)
      _ <- if (sendEmail) sendOptOutMessages(prefs) else Future.successful((): Unit)
      newTermsAndConditions = prefs.termsAndConditions
                                .withTerms(terms, Refused(time, bundle.eventType, bundle.optInPage))

      updatePrintSuppression = bundle.eventType != Some(SystemOptOut)
      preferencesUpdated <- if (terms == GENERIC) {
                              val event = getOptOutEvent(bundle.optInPage, bundle.eventType, lang, prefs, time)
                              createOptOutWithEvent(event, newTermsAndConditions)
                            } else {
                              val prefsWithNewTandC = prefs.copy(termsAndConditions = newTermsAndConditions)

                              preferencesRepository
                                .createOrUpdateTermsAndConditions(
                                  prefsWithNewTandC,
                                  credentials
                                )
                            }

      _ <- if (updatePrintSuppression) {
             pcnService.notifyPreferencesChanged(prefs._id, prefs.entityId, Paper)
           } else { Future.successful(()) }

      _ <- if (etmpUpdateFlag) etmpService.checkAndUpdateETMP(prefs.entityId, paperless = false, eventId = None)
           else Future.successful((): Unit)
    } yield {
      auditable.sendDataEvent(
        "Opt Out Email Reminders",
        tags = Map("reason" -> reason.getOrElse("User Selected to Opt Out")),
        detail = Map(
          "entityId"           -> prefs.entityId.value,
          "wasVerified"        -> prefs.email.exists(_.isVerified).toString,
          "wasDigital"         -> (!prefs.isOptedOut(terms)).toString,
          "wasBounced"         -> prefs.email.exists(_.isBounced).toString,
          "termsAndConditions" -> terms
        )
      )
      preferencesUpdated
    }
  }

  private def optOutWithEvent(
    event: Option[Event],
    prefs: Preferences,
    newTermsAndConditions: TermsAndConditions,
    credentials: Option[Credentials],
    @unused updatePrintSuppression: Boolean
  )(implicit hc: HeaderCarrier): Future[PreferenceUpdateResult] =
    event match {
      case Some(optOutEvent) =>
        val prefsWithNewTandCandEvents =
          prefs.copy(events = getAllEvents(prefs, Some(optOutEvent)), termsAndConditions = newTermsAndConditions)

        for {
          result <- preferencesRepository
                      .createOrUpdateTermsAndConditions(prefsWithNewTandCandEvents, credentials)
          _ <- pcnService
                 .notifyPreferencesChanged(prefs._id, prefs.entityId, prefsWithNewTandCandEvents.messageDeliveryFormat)
        } yield result

      case None => Future.successful(LanguageNotUpdated)
    }

  private def recordGenericOptOutMetric(
    isUserOptOut: Boolean,
    isPaperless: Boolean,
    termsAndConditions: String
  ): Future[Unit] =
    (isUserOptOut, isPaperless, termsAndConditions) match {
      case (false, _, "generic")   => preferencesMetricsRepository.increment(manualOptOut, 1)
      case (true, true, "generic") => preferencesMetricsRepository.increment(userOptOut, 1)
      case _                       => Future.successful((): Unit)
    }

  private def sendOptOutMessages(prefs: Preferences)(implicit hc: HeaderCarrier): Future[Unit] =
    prefs.email match {
      case Some(email) if email.isVerified && !email.isBounced =>
        emailConnector.sendDigitalOptOutEmail(email.email)
      case _ =>
        logger.info(
          s"Unable to send opt out email to user ${prefs.entityId.value}, because they do not have a usable email"
        )
        Future.successful(())
    }

}
