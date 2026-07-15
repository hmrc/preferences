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

import org.mockito.ArgumentMatchers.{ any, eq => eqTo }
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.lock.{ Lock, MongoLockRepository }
import uk.gov.hmrc.preferences.jobs.RunModeBridge
import uk.gov.hmrc.preferences.model.OptEventType.{ OptIn, SystemOptOut }
import uk.gov.hmrc.preferences.model.TermsAndConditions.{ Accepted, GENERIC, Unknown }
import uk.gov.hmrc.preferences.model._
import uk.gov.hmrc.preferences.repository.{ NoEmailForPreference, PreferenceUpdated, PreferencesRepository }
import uk.gov.hmrc.preferences.util.Dc
import utils.GenerateRandom

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.concurrent.duration.Duration.Zero

class CleanupUnverifiedMigrationServiceSpec
    extends PlaySpec with MockitoSugar with ScalaFutures with IntegrationPatience with BeforeAndAfterEach {

  trait Setup {
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()
    val mockLockRespository: MongoLockRepository = mock[MongoLockRepository]
    val mockPreferencesRepository: PreferencesRepository = mock[PreferencesRepository]
    val mockOptOutService: OptOutService = mock[OptOutService]
    val mockRunModeBridge: RunModeBridge = mock[RunModeBridge]
    val prefUpdatResult = PreferenceUpdated
    val entityId: EntityId = GenerateRandom.entityId()
    val optInDate: Instant = Dc.instantNow()
    val reason: Option[String] = Some("SYSTEM OPT-OUT - PARTIAL OPT-IN")

    val prefNoTnC = new Preferences(
      entityId = entityId,
      pendingEmail = Some(PendingEmailAddress("pendingemail@test.com", None, language = Some(Language.English))),
      termsAndConditions = TermsAndConditions(Unknown)
    )

    val prefGenTnC = new Preferences(
      entityId = entityId,
      pendingEmail = Some(PendingEmailAddress("pendingemail@test.com", None, language = Some(Language.English))),
      termsAndConditions = TermsAndConditions(Accepted(optInDate, Some(OptIn)))
    )

    val cleanupUnverifiedMigrationService = new CleanupUnverifiedMigrationService(
      mockLockRespository,
      mockPreferencesRepository,
      mockOptOutService,
      mockRunModeBridge
    )

    val lock = Some(Lock("id", "owner", Dc.instantNow(), Dc.instantNow().plusSeconds(1)))
    when(mockLockRespository.takeLock(any[String], any[String], any[Duration])).thenReturn(Future.successful(lock))
    when(mockLockRespository.releaseLock(any[String], any[String])).thenReturn(Future.successful(()))

    when(mockRunModeBridge.getEnabledFlag(cleanupUnverifiedMigrationService.name, "taskEnabled")).thenReturn(true)
    when(mockRunModeBridge.getBatchSize(cleanupUnverifiedMigrationService.name, "batchSize")).thenReturn(100)
    when(mockRunModeBridge.getMillisForScheduling(any[String], any[String])).thenReturn(Zero)
    when(mockRunModeBridge.getEnabledFlag("cleanupUnverifiedJob", "dryRun")).thenReturn(false)
    when(mockRunModeBridge.getStringForMode("scheduling.cleanupUnverifiedJob.activePeriod.start")).thenReturn("00:00")
    when(mockRunModeBridge.getStringForMode("scheduling.cleanupUnverifiedJob.activePeriod.stop")).thenReturn("23:59")

    when(mockPreferencesRepository.findUnverifiedExpired(any[Instant]))
      .thenReturn(Future.successful(Seq.empty[Preferences]))
    when(mockPreferencesRepository.findUnverifiedTwoEmailsExpired(any[Instant]))
      .thenReturn(Future.successful(Seq.empty[Preferences]))
  }

  "CleanupUnverifiedMigrationJob" should {
    "opt out no preferences if none expired" in new Setup {
      cleanupUnverifiedMigrationService.execute().futureValue.message mustBe executeMsg(0, 0, 0, 0, 0)
    }

    "opt out no preferences if failed to parse the preference" in new Setup {
      when(mockPreferencesRepository.findUnverifiedExpired(any[Instant]))
        .thenReturn(Future.failed(new RuntimeException("Failed to parse preferences")))
      cleanupUnverifiedMigrationService.execute().futureValue.message mustBe executeMsg(0, 0, 0, 0, 0)
    }

    "not opt out if there is no generic terms and conditions" in new Setup {
      when(mockPreferencesRepository.findUnverifiedExpired(any[Instant])).thenReturn(Future.successful(Seq(prefNoTnC)))
      cleanupUnverifiedMigrationService.execute().futureValue.message mustBe executeMsg(1, 0, 0, 0, 0)
    }

    "opt out generic tnc when it is set" in new Setup {
      when(mockPreferencesRepository.findUnverifiedExpired(any[Instant]))
        .thenReturn(Future.successful(Seq(prefGenTnC)))
      when(
        mockOptOutService.optOutOfDigital(
          eqTo(entityId),
          eqTo(reason),
          eqTo(GENERIC),
          eqTo(None),
          eqTo(OptInBundle(None, Some(SystemOptOut))),
          eqTo(None),
          eqTo(None)
        )(any[HeaderCarrier])
      ).thenReturn(Future.successful(prefUpdatResult))
      cleanupUnverifiedMigrationService.execute().futureValue.message mustBe executeMsg(1, 1, 0, 0, 0)
    }

    "delete pendiing expired email" in new Setup {
      when(mockPreferencesRepository.findUnverifiedTwoEmailsExpired(any[Instant]))
        .thenReturn(Future.successful(Seq(prefGenTnC)))
      when(mockPreferencesRepository.unsetPendingEmail(any[EntityId], any[EmailEvent]))
        .thenReturn(Future.successful(PreferenceUpdated))
      cleanupUnverifiedMigrationService.execute().futureValue.message mustBe executeMsg(0, 0, 1, 1, 0)
    }

    "show number of pending expired emails not deleted" in new Setup {
      when(mockPreferencesRepository.findUnverifiedTwoEmailsExpired(any[Instant]))
        .thenReturn(Future.successful(Seq(prefGenTnC)))
      when(mockPreferencesRepository.unsetPendingEmail(any[EntityId], any[EmailEvent]))
        .thenReturn(Future.successful(NoEmailForPreference))
      cleanupUnverifiedMigrationService.execute().futureValue.message mustBe executeMsg(0, 0, 1, 0, 1)
    }
  }

  private def executeMsg(prefsFound: Int, optedOut: Int, pendingFnd: Int, deleted: Int, notDeleted: Int) =
    s"""Completed cleanupUnverifiedJob batch migration. Found $prefsFound preferences. Actually opted out $optedOut.
       |Found: $pendingFnd expired pending emails, deleted: $deleted, not deleted $notDeleted.
       |""".stripMargin
}
