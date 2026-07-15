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
import uk.gov.hmrc.mongo.lock.LockService
import uk.gov.hmrc.preferences.Auditable
import uk.gov.hmrc.preferences.connector.Bounce
import uk.gov.hmrc.preferences.model.*
import uk.gov.hmrc.preferences.model.MessageDeliveryFormat.Paper
import uk.gov.hmrc.preferences.repository.{ PreferenceUpdateResult, PreferenceUpdated, PreferencesRepository }

import javax.inject.{ Inject, Named, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

final case class EmailBounceLock(lockService: LockService)
@Singleton
class EmailBounceQueueMonitorService @Inject() (
  etmpService: ETMPService,
  auditable: Auditable,
  individualPreferencesRepository: PreferencesRepository,
  pcnService: PreferencesChangedNotifierService,
  @Named("etmpUpdate") etmpUpdateFlag: Boolean = false
)(implicit ec: ExecutionContext)
    extends ServiceBase {

  private val logger: Logger = Logger(getClass)

  def markAsBounced(bounce: Bounce)(implicit hc: HeaderCarrier): Future[Unit] =
    for {
      prefs <- individualPreferencesRepository.findByEmail(bounce.emailAddress)
      _     <- bounceEmailsForMatchingPrefs(prefs, bounce)
      _     <- if (etmpUpdateFlag) updateETMP(prefs) else Future.successful((): Unit)
    } yield ()

  private def updateETMP(prefs: Seq[Preferences])(implicit hc: HeaderCarrier): Future[Unit] = {
    Future.traverse(prefs) { pref =>
      etmpService.checkAndUpdateETMP(pref.entityId, paperless = false, eventId = None)
    }
    Future.successful((): Unit)
  }

  private def bounceEmailsForMatchingPrefs(matchingPrefs: Seq[Preferences], b: Bounce)(implicit
    hc: HeaderCarrier
  ): Future[Unit] =
    Future.traverse(matchingPrefs)(updateAndAudit(_, b)).map(_ => ())

  private def updateAndAudit(prefs: Preferences, bounce: Bounce)(implicit hc: HeaderCarrier): Future[Unit] = {
    val prefsWithEvents = addOptionalBounceEvent(prefs, bounce)
    updatePreferenceWithBounce(prefsWithEvents, bounce).map {
      case PreferenceUpdated => auditPrintSuppression(prefsWithEvents, bounce.emailAddress)
      case _ => logger.error(s"Bounce detected for EntityId[${prefs.entityId} but preferences update failed")
    }
  }

  private def addOptionalBounceEvent(prefs: Preferences, bounce: Bounce): Preferences =
    getBounceEvent(prefs, bounce) match {
      case Some(bounceEvent) => prefs.copy(events = getAllEvents(prefs, Some(bounceEvent)))
      case _                 => prefs
    }

  private def getBounceEvent(prefs: Preferences, bounce: Bounce): Option[Event] =
    Some(
      EmailEvent(
        prefs.entityId,
        EmailEventType.EmailBounced,
        bounce.emailAddress,
        paperless = stillPaperless(prefs, bounce),
        bounce.detected
      )
    )

  private def stillPaperless(prefs: Preferences, bounce: Bounce): Option[Boolean] =
    prefs.email match {
      case Some(emailAddress) => Some(emailAddress.email.toLowerCase != bounce.emailAddress.toLowerCase)
      case _                  => Some(false)
    }

  private def auditPrintSuppression(preference: Preferences, emailAddress: String)(implicit hc: HeaderCarrier): Unit =
    auditable.sendDataEvent(
      "Print Suppression Off",
      tags = Map("reason" -> "Bounced Message detected"),
      detail = Map("entityId" -> preference.entityId.value, "emailAddress" -> emailAddress)
    )

  private def updatePreferenceWithBounce(preferences: Preferences, bounce: Bounce)(implicit
    hc: HeaderCarrier
  ): Future[PreferenceUpdateResult] = {
    val sourcesToIncrementBounce = Set("preferences")
    val shouldIncBounce = bounce.emailSource.exists { source =>
      sourcesToIncrementBounce.contains(source.trim.toLowerCase)
    }
    def bounceIfMatches(emailAddress: Option[String]): Option[EmailBounce] =
      emailAddress
        .filter(_ equalsIgnoreCase bounce.emailAddress)
        .map { _ =>
          EmailBounce(
            bounce.code,
            bounce.detected
          )
        }

    for {
      updateResult: PreferenceUpdateResult <- individualPreferencesRepository
                                                .addBouncesAndClearVerificationLink(
                                                  preferences,
                                                  bounceIfMatches(preferences.email.map(_.email)),
                                                  bounceIfMatches(preferences.pendingEmail.map(_.email)),
                                                  shouldIncBounce
                                                )
      _ <- pcnService
             .notifyPreferencesChanged(
               preferences._id,
               preferences.entityId,
               Paper,
               false, // This should be false to avoid sending update to NPS for non-P2 users
               Option(P2Bounced(bounce.formType, bounce.nino))
             )
    } yield updateResult
  }
}
