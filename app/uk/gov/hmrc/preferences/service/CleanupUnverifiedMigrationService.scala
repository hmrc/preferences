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
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.lock.{ LockService, MongoLockRepository }
import uk.gov.hmrc.preferences.CurrentTime
import uk.gov.hmrc.preferences.jobs.RunModeBridge
import uk.gov.hmrc.preferences.model.OptEventType.SystemOptOut
import uk.gov.hmrc.preferences.model.TermsAndConditions.{ Accepted, GENERIC }
import uk.gov.hmrc.preferences.model.{ EmailEvent, EmailEventType, OptInBundle, Preferences, TermsAndConditions }
import uk.gov.hmrc.preferences.repository.{ PreferenceUpdateResult, PreferenceUpdated, PreferencesRepository }
import uk.gov.hmrc.preferences.scheduling.Result
import uk.gov.hmrc.preferences.util.Dc

import java.time.temporal.ChronoUnit
import java.time.{ Duration, Instant }
import java.util.concurrent.atomic.AtomicLong
import javax.inject.{ Inject, Singleton }
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future }
import scala.jdk.DurationConverters.JavaDurationOps
import scala.util.{ Failure, Success, Try }

@Singleton
class CleanupUnverifiedMigrationService @Inject() (
  lockRepository: MongoLockRepository,
  preferencesRepository: PreferencesRepository,
  optOutService: OptOutService,
  runModeBridge: RunModeBridge
) {
  val logger: Logger = Logger(getClass)

  val name: String = "cleanupUnverifiedJob"

  lazy val lockDuration: Option[FiniteDuration] = runModeBridge.getOptionalMillisForScheduling(name, "lockDuration")
  lazy val expiryFromConfig: FiniteDuration = runModeBridge.getMillisForScheduling(name, "expireUnverified")
  lazy val batchSize: Int = runModeBridge.getBatchSize(name, "batchSize")
  lazy val isDryRun: Boolean = runModeBridge.getEnabledFlag(name, "dryRun")

  val totalTimeElapsed = new AtomicLong(0)
  val maxLockHours = 1
  val OptOutReason: Option[String] = Some("SYSTEM OPT-OUT - PARTIAL OPT-IN")

  val releaseLockAfter: Duration = lockDuration match {
    case Some(duration) => Duration.ofMillis(duration.toMillis)
    case _              => Duration.ofHours(maxLockHours.toLong)
  }

  val ls: LockService = LockService(lockRepository = lockRepository, lockId = name, ttl = releaseLockAfter.toScala)

  def execute()(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Result] =
    ls.withLock {
      val start = Dc.instantNow()
      val expiryCutoffDate = start.minus(expiryFromConfig.toMillis, ChronoUnit.MILLIS)

      logger.warn(s"Starting $name with batch size:[$batchSize] at ${start.toString}")

      for {
        p <- preferencesRepository.findUnverifiedExpired(expiryCutoffDate) recover { case e =>
               logger.error(
                 s"$name - findUnverifiedExpired - Unable to find the preference records, reason: ${e.getMessage}"
               )
               Seq.empty[Preferences]
             }
        _ = logger.warn(s"Found ${p.size} users to opt out. Dry run mode: $isDryRun")
        optedOut <- if (isDryRun) {
                      logger.warn(s"$name: Job running in Dry Run mode: $isDryRun")
                      Future.successful {
                        logger.warn(
                          s"Found ${p.size} users to opt out based on Mongo query, here's an example of 2 of them. Not opting opt as we are in dry run mode."
                        )
                        p.take(2)
                          .foreach(pref =>
                            logger.warn(
                              s"Found example preference user: ${Json.toJson(pref).toString()}. Not opting out as we are running in dry run mode."
                            )
                          )
                        Seq.empty[Preferences]
                      }
                    } else {
                      logger.warn(s"Running full opt out mode for following preferences count: ${p.size}")
                      optOutPrefs(p)
                    }
        deletedPendingEmails <- deleteExpiredPendingEmailWhenVerifiedEmailExitst(expiryCutoffDate)
      } yield {
        val msg =
          s"""Completed $name batch migration. Found ${p.length} preferences. Actually opted out ${optedOut.length}.
             |Found: ${deletedPendingEmails.fndExpired} expired pending emails, deleted: ${deletedPendingEmails.numDeleted}, not deleted ${deletedPendingEmails.numNotDeleted}.
             |""".stripMargin
        logger.warn(msg)
        val end = Dc.instantNow()
        val delta = Duration.between(start, end)
        val elapsed = totalTimeElapsed.addAndGet(delta.getSeconds)
        logger.warn(
          s"Total time elapsed: ${elapsed / 60} minutes, ${elapsed % 60} seconds elapsed; Users processed: ${p.length}"
        )
        Result(msg)
      }
    } map {
      case Some(Result(msg)) => Result(s"$msg")
      case None              => Result(s"Job with $name cannot acquire mongo lock, not running")
    }

  private[this] def optOutPrefs(
    prefs: Seq[Preferences]
  )(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Seq[PreferenceUpdateResult]] =
    Future.sequence(for {
      p     <- prefs
      terms <- getTnCs(p)
    } yield {
      logger.warn(s"opting out: ${p.entityId} terms: $terms")
      optOutService
        .optOutOfDigital(
          entityId = p.entityId,
          reason = OptOutReason,
          terms = terms,
          credentials = None,
          bundle = OptInBundle(None, Some(SystemOptOut)),
          lang = None,
          surveyType = None
        )
    })

  private[this] def getTnCs(p: Preferences): List[String] =
    p.termsAndConditions match {
      case TermsAndConditions(Accepted(_, _, _)) => List(GENERIC)
      case _ =>
        logger.warn(s"No terms and conditions found for ${p.entityId}")
        List.empty
    }

  private def deleteExpiredPendingEmailWhenVerifiedEmailExitst(
    cutoff: Instant
  )(implicit ec: ExecutionContext): Future[DeleteExpiredPendingResult] =
    if (isDryRun) {
      logger.warn("Dry Run mode for deleting expired pending emails")
      Future.successful(DeleteExpiredPendingResult(0, 0, 0))
    } else {
      preferencesRepository
        .findUnverifiedTwoEmailsExpired(cutoff)
        .flatMap { ps =>
          Future
            .sequence(ps.map { p =>
              preferencesRepository.unsetPendingEmail(p.entityId, getEmailEvent(p))
            })
            .transform {
              case Success(v) =>
                val (f, nf) = v.partition {
                  case PreferenceUpdated => true
                  case _                 => false
                }
                Try(DeleteExpiredPendingResult(ps.size, f.size, nf.size))
              case Failure(e) =>
                logger.error("Error deleting expired pending emails", e)
                Try(DeleteExpiredPendingResult(0, 0, 0))
            }
        }
    }

  def getEmailEvent(p: Preferences): EmailEvent =
    new CurrentTime {}.withCurrentTime { time =>
      EmailEvent(
        p.entityId,
        EmailEventType.SystemExpiredPendingEmailRemoval,
        p.pendingEmail.fold("no-email@none.com")(_.email),
        Some(p.isPaperless),
        time
      )
    }

  case class DeleteExpiredPendingResult(fndExpired: Int, numDeleted: Int, numNotDeleted: Int)

}
