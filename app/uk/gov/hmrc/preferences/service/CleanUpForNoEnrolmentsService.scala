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
import uk.gov.hmrc.mongo.lock.{ LockService, MongoLockRepository }
import uk.gov.hmrc.preferences.jobs.RunModeBridge
import uk.gov.hmrc.preferences.repository.PreferencesRepository
import uk.gov.hmrc.preferences.model.Preferences
import uk.gov.hmrc.preferences.connector.EntityResolverConnector
import uk.gov.hmrc.preferences.exceptions.{ DeletePreferences, UnsetMarkDeEnrolment }
import uk.gov.hmrc.preferences.scheduling.Result
import uk.gov.hmrc.preferences.util.Dc

import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.{ Inject, Singleton }
import scala.concurrent.duration.{ Duration, FiniteDuration, HOURS, MILLISECONDS }
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class CleanUpForNoEnrolmentsService @Inject() (
  lockRepository: MongoLockRepository,
  preferencesRepository: PreferencesRepository,
  entityResolverConnector: EntityResolverConnector,
  runModeBridge: RunModeBridge
)(implicit ec: ExecutionContext) {

  val logger: Logger = Logger(getClass)

  val name: String = "cleanUpForNoEnrolments"

  def now: Instant = Dc.instantNow()

  lazy val lockDuration: Option[FiniteDuration] = runModeBridge.getOptionalMillisForScheduling(name, "lockDuration")
  lazy val batchSize: Int = runModeBridge.getBatchSize(name, "batchSize")
  lazy val expireAfter: FiniteDuration = runModeBridge.getMillisForScheduling(name, "expireAfter")

  val maxLockHours = 1

  val releaseLockAfter: Duration = lockDuration match {
    case Some(duration) => Duration(duration.toMillis, MILLISECONDS)
    case _              => Duration(maxLockHours.toLong, HOURS)
  }

  val ls: LockService = LockService(lockRepository = lockRepository, lockId = name, ttl = releaseLockAfter)

  def execute: Future[Result] =
    ls.withLock {
      for {
        p <-
          preferencesRepository
            .findExpiredRecordsForDeEnrolment(batchSize, now.minus(expireAfter.toMillis, ChronoUnit.MILLIS)) recover {
            case e =>
              logger.error(
                s"$name - findExpiredRecordsForDeEnrolment - Unable to find the preference records, reason: ${e.getMessage}"
              )
              Seq.empty[Preferences]
          }
        _ = logger.warn(s"Found ${p.size} record(s) to delete because of no enrolments")
        r <- processPreferences(p)
      } yield {
        val recordsProcessed = r.count(p => p)
        logger.warn(s"Cleaned up $recordsProcessed record(s) from 'saIndividualPreferences'")
        Result(s"Completed the process '$name' for $recordsProcessed record(s)")
      }
    } map {
      case Some(Result(msg)) => Result(s"$msg")
      case None              => Result(s"$name cannot acquire mongo lock, not running")
    }

  private[this] def processPreferences(preferences: Seq[Preferences]): Future[Seq[Boolean]] = {
    logger.warn(s"Found ${preferences.size} records to process")
    Future.traverse(preferences) { p =>
      logger.warn(s"Process the preference record id:${p._id}")
      checkAndProcessPreferences(p) recover { case e =>
        logger.warn(s"Unable to process the preference record id:${p._id}, reason: $e")
        false
      }
    }
  }

  private[this] def checkAndProcessPreferences(p: Preferences): Future[Boolean] = {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    entityResolverConnector.updateEntity(p.entityId, p.markForDeEnrolment.fold("N/A")(_.identifier)).flatMap {
      case UnsetMarkDeEnrolment =>
        logger.warn(s"Unset 'markForDeEnrolment' for ${p._id}")
        preferencesRepository.unsetDeEnrolment(p.entityId)
      case DeletePreferences =>
        logger.warn(s"Delete the preference record for ${p._id}")
        preferencesRepository.removeById(p._id)
      case other =>
        logger.error(s"Invalid match condition for '$other'")
        Future.successful(false)
    }
  }

}
