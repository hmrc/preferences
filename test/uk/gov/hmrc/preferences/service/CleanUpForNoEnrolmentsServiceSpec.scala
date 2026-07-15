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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import org.mongodb.scala.bson.ObjectId
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.lock.{ Lock, MongoLockRepository }
import uk.gov.hmrc.preferences.util.Dc
import uk.gov.hmrc.preferences.connector.*
import uk.gov.hmrc.preferences.exceptions.{ DeletePreferences, EntityProcessError, InvalidEntity, UnsetMarkDeEnrolment }
import uk.gov.hmrc.preferences.jobs.RunModeBridge
import uk.gov.hmrc.preferences.model.TermsAndConditions.Unknown
import uk.gov.hmrc.preferences.model.*
import uk.gov.hmrc.preferences.repository.PreferencesRepository
import uk.gov.hmrc.preferences.util.DateTimeExtensions.InstantExtensions
import utils.GenerateRandom

import java.time.Instant
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.Duration.Zero
import scala.concurrent.duration.Duration

class CleanUpForNoEnrolmentsServiceSpec
    extends AnyWordSpecLike with Matchers with MockitoSugar with ScalaFutures with IntegrationPatience {

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  private val cleanUpJobName = "cleanUpForNoEnrolments"

  trait Setup {
    val headerCarrier = HeaderCarrier()

    val mockLockRespository: MongoLockRepository = mock[MongoLockRepository]
    val mockPreferencesRepository: PreferencesRepository = mock[PreferencesRepository]
    val mockEntityResolverConnector: EntityResolverConnector = mock[EntityResolverConnector]
    val mockCleanUpService: CleanUpForNoEnrolmentsService = mock[CleanUpForNoEnrolmentsService]
    val mockRunModeBridge: RunModeBridge = mock[RunModeBridge]

    lazy val now: Instant = Dc.instantNow()
    lazy val expired: Instant = now.minusDays(28)

    val cleanupService = new CleanUpForNoEnrolmentsService(
      mockLockRespository,
      mockPreferencesRepository,
      mockEntityResolverConnector,
      mockRunModeBridge
    )

    val lock = Some(Lock("id", "owner", Dc.instantNow(), Dc.instantNow().plusSeconds(1)))
    when(mockLockRespository.takeLock(any[String], any[String], any[Duration])).thenReturn(Future.successful(lock))
    when(mockLockRespository.releaseLock(any[String], any[String])).thenReturn(Future.successful(()))

    when(mockRunModeBridge.getEnabledFlag(cleanUpJobName, "taskEnabled")).thenReturn(true)
    when(mockRunModeBridge.getBatchSize(cleanUpJobName, "batchSize")).thenReturn(100)
    when(mockRunModeBridge.getMillisForScheduling(any[String], any[String])).thenReturn(Zero)

    when(mockRunModeBridge.getStringForMode(s"scheduling.$cleanUpJobName.activePeriod.start")).thenReturn("00:00")
    when(mockRunModeBridge.getStringForMode(s"scheduling.$cleanUpJobName.activePeriod.stop")).thenReturn("23:59")

    def resultMessage(result: Int) = s"Completed the process '$cleanUpJobName' for $result record(s)"
  }

  "CleanUpForNoEnrolments service" should {

    "return 0 records when there are no records marked with 'markForDeEnrolment'" in new Setup {
      when(mockPreferencesRepository.findExpiredRecordsForDeEnrolment(any[Int], any[Instant]))
        .thenReturn(Future.successful(Seq()))
      cleanupService.execute.futureValue.message mustBe resultMessage(0)
    }

    "return 0 records when there is parsing error for preference record" in new Setup {
      when(mockPreferencesRepository.findExpiredRecordsForDeEnrolment(any[Int], any[Instant]))
        .thenReturn(Future.failed(new RuntimeException("Failed to parse preferences")))
      cleanupService.execute.futureValue.message mustBe resultMessage(0)
    }

    "return 2 records processed, when there are 2 records marked with 'markForDeEnrolment' " +
      "and the response from entity-resolver is 'UnsetMarkDeEnrolment' " in new Setup {
        val p1 = new Preferences(
          entityId = GenerateRandom.entityId(),
          pendingEmail = Some(PendingEmailAddress("pendingemail@test.com", None, language = Some(Language.English))),
          termsAndConditions = TermsAndConditions(Unknown),
          markForDeEnrolment = Some(MarkForDeEnrolment(expired, "sa"))
        )
        val p2 = new Preferences(
          entityId = GenerateRandom.entityId(),
          pendingEmail = Some(PendingEmailAddress("pendingemail@test.com", None, language = Some(Language.English))),
          termsAndConditions = TermsAndConditions(Unknown),
          markForDeEnrolment = Some(MarkForDeEnrolment(expired, "sa"))
        )
        when(mockEntityResolverConnector.updateEntity(any[EntityId], any[String])(any[HeaderCarrier]))
          .thenReturn(Future.successful(UnsetMarkDeEnrolment))
        when(mockPreferencesRepository.findExpiredRecordsForDeEnrolment(any[Int], any[Instant]))
          .thenReturn(Future.successful(Seq(p1, p2)))
        when(mockPreferencesRepository.unsetDeEnrolment(any[EntityId])(any[HeaderCarrier]))
          .thenReturn(Future.successful(true))
        cleanupService.execute.futureValue.message mustBe resultMessage(2)
      }

    "return 2 records processed, when there are 2 records marked with 'markForDeEnrolment' " +
      "and the response from entity-resolver is 'DeletePreferences' " in new Setup {
        val p1 = new Preferences(
          entityId = GenerateRandom.entityId(),
          pendingEmail = Some(PendingEmailAddress("pendingemail@test.com", None, language = Some(Language.English))),
          termsAndConditions = TermsAndConditions(Unknown),
          markForDeEnrolment = Some(MarkForDeEnrolment(expired, "sa"))
        )
        val p2 = new Preferences(
          entityId = GenerateRandom.entityId(),
          pendingEmail = Some(PendingEmailAddress("pendingemail@test.com", None, language = Some(Language.English))),
          termsAndConditions = TermsAndConditions(Unknown),
          markForDeEnrolment = Some(MarkForDeEnrolment(expired, "sa"))
        )
        when(mockEntityResolverConnector.updateEntity(any[EntityId], any[String])(any[HeaderCarrier]))
          .thenReturn(Future.successful(DeletePreferences))
        when(mockPreferencesRepository.findExpiredRecordsForDeEnrolment(any[Int], any[Instant]))
          .thenReturn(Future.successful(Seq(p1, p2)))
        when(mockPreferencesRepository.removeById(any[ObjectId])(any[ExecutionContext]))
          .thenReturn(Future.successful(true))
        cleanupService.execute.futureValue.message mustBe resultMessage(2)
      }

    "return 0 records processed, when there are 2 records marked with 'markForDeEnrolment' " +
      "and the response from entity-resolver is 'EntityProcessError' " in new Setup {
        val p1 = new Preferences(
          entityId = GenerateRandom.entityId(),
          pendingEmail = Some(PendingEmailAddress("pendingemail@test.com", None, language = Some(Language.English))),
          termsAndConditions = TermsAndConditions(Unknown),
          markForDeEnrolment = Some(MarkForDeEnrolment(expired, "sa"))
        )
        val p2 = new Preferences(
          entityId = GenerateRandom.entityId(),
          pendingEmail = Some(PendingEmailAddress("pendingemail@test.com", None, language = Some(Language.English))),
          termsAndConditions = TermsAndConditions(Unknown),
          markForDeEnrolment = Some(MarkForDeEnrolment(expired, "sa"))
        )
        when(mockEntityResolverConnector.updateEntity(any[EntityId], any[String])(any[HeaderCarrier]))
          .thenReturn(Future.successful(EntityProcessError))
        when(mockPreferencesRepository.findExpiredRecordsForDeEnrolment(any[Int], any[Instant]))
          .thenReturn(Future.successful(Seq(p1, p2)))

        cleanupService.execute.futureValue.message mustBe resultMessage(0)
      }

    "return 0 records processed, when there are 2 records marked with 'markForDeEnrolment' " +
      "and the response from entity-resolver is 'InvalidEntity' " in new Setup {
        val p1 = new Preferences(
          entityId = GenerateRandom.entityId(),
          pendingEmail = Some(PendingEmailAddress("pendingemail@test.com", None, language = Some(Language.English))),
          termsAndConditions = TermsAndConditions(Unknown),
          markForDeEnrolment = Some(MarkForDeEnrolment(expired, "sa"))
        )
        val p2 = new Preferences(
          entityId = GenerateRandom.entityId(),
          pendingEmail = Some(PendingEmailAddress("pendingemail@test.com", None, language = Some(Language.English))),
          termsAndConditions = TermsAndConditions(Unknown),
          markForDeEnrolment = Some(MarkForDeEnrolment(expired, "sa"))
        )
        when(mockEntityResolverConnector.updateEntity(any[EntityId], any[String])(any[HeaderCarrier]))
          .thenReturn(Future.successful(InvalidEntity))
        when(mockPreferencesRepository.findExpiredRecordsForDeEnrolment(any[Int], any[Instant]))
          .thenReturn(Future.successful(Seq(p1, p2)))

        cleanupService.execute.futureValue.message mustBe resultMessage(0)
      }
  }
}
