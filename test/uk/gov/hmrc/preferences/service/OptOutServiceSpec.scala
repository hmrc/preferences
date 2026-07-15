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

import com.codahale.metrics.SharedMetricRegistries
import org.bson.types.ObjectId
import org.mockito.ArgumentMatchers.{ any, eq => eqTo, same }
import org.mockito.Mockito.{ never, times, verify, verifyNoInteractions, when }
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.preferences.model.OptEventType.{ AdminOptOut, CustomerOptOut, CustomerReOptOut, OptIn, SystemOptOut }
import uk.gov.hmrc.preferences.repository.PreferenceUpdated
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.{ AffinityGroup, ConfidenceLevel }
import uk.gov.hmrc.auth.core.retrieve.Name
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.{ Audit, DataEvent }
import uk.gov.hmrc.preferences.Auditable
import uk.gov.hmrc.preferences.connector.EmailConnector
import uk.gov.hmrc.preferences.controllers.model.Credentials
import uk.gov.hmrc.preferences.controllers.model.TermsAndConditionsRequest.ManualOptOut
import uk.gov.hmrc.preferences.model._
import uk.gov.hmrc.preferences.model.Language.English
import uk.gov.hmrc.preferences.model.MessageDeliveryFormat.Paper
import uk.gov.hmrc.preferences.model.PageType.IPage
import uk.gov.hmrc.preferences.model.SurveyType.StandardInterruptOptOut
import uk.gov.hmrc.preferences.model.TermsAndConditions.{ Accepted, Refused }
import uk.gov.hmrc.preferences.repository.{ LanguageNotUpdated, NewPreferenceCreated, PreferencesMetricsRepository, PreferencesRepository }
import uk.gov.hmrc.preferences.util.DateTimeExtensions.InstantExtensions
import uk.gov.hmrc.preferences.util.Dc
import utils.GenerateRandom

import java.time.Instant
import scala.concurrent.{ ExecutionContext, Future }

class OptOutServiceSpec
    extends PlaySpec with MockitoSugar with ScalaFutures with BeforeAndAfterEach with IntegrationPatience {

  override def beforeEach(): Unit = {
    super.beforeEach()
    SharedMetricRegistries.clear()
  }

  val now: Instant = Dc.instantNow()

  implicit def futureConverter[T](t: T): Future[T] = Future.successful(t)

  implicit val hc: HeaderCarrier = HeaderCarrier()

  lazy val credentials: Credentials =
    Credentials(Some(AffinityGroup.Individual), ConfidenceLevel.L200)

  "opt out" should {
    val GenericTerms: String = "generic"

    "create a new preference when the entityId doesn't exist for customer opt out" in new TestCase {
      when(mockPreferencesRepository.findBy(same(entityId))(any[HeaderCarrier])).thenReturn(Future.successful(None))
      when(
        mockPreferencesRepository
          .createOrUpdateTermsAndConditions(any[Preferences], any[Option[Credentials]])(
            any[HeaderCarrier]
          )
      ).thenReturn(Future.successful(NewPreferenceCreated))
      when(mockPreferencesMetricsRepository.increment(any[String], any[Int])).thenReturn(Future.successful(()))
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      optOutService
        .optOutOfDigital(
          entityId,
          None,
          GenericTerms,
          Some(credentials),
          OptInBundle(Some(optInPage), Some(CustomerOptOut)),
          Some(Language.English),
          Some(StandardInterruptOptOut)
        )
        .futureValue must be(NewPreferenceCreated)
    }

    "create a new preference when the entityId doesn't exist for customer reopt out" in new TestCase {
      when(mockPreferencesRepository.findBy(same(entityId))(any[HeaderCarrier])).thenReturn(Future.successful(None))
      when(
        mockPreferencesRepository
          .createOrUpdateTermsAndConditions(any[Preferences], any[Option[Credentials]])(
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(NewPreferenceCreated))
      when(mockPreferencesMetricsRepository.increment(any[String], any[Int])).thenReturn(Future.successful(()))
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      optOutService
        .optOutOfDigital(
          entityId,
          None,
          GenericTerms,
          Some(credentials),
          OptInBundle(Some(optInPage), Some(CustomerReOptOut)),
          Some(Language.English),
          Some(StandardInterruptOptOut)
        )
        .futureValue must be(NewPreferenceCreated)
    }

    "send an email to the users verified email address when user opting out pre opt-in migration" in new TestCase {
      private val pref = Preferences(
        entityId = entityId,
        termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
        email = Some(EmailAddress("verified@test.com", verifiedOn = Some(Dc.instantNow()))),
        pendingEmail = Some(PendingEmailAddress(email = "pending@foo.com")),
        createdAt = Dc.instantNow().minusDays(1),
        updatedAt = Dc.instantNow().minusDays(1)
      )

      when(mockPreferencesRepository.findBy(same(entityId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(pref)))
      when(
        mockPreferencesRepository
          .createOrUpdateTermsAndConditions(any[Preferences], any[Option[Credentials]])(
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(PreferenceUpdated))
      when(mockEmailConnector.sendDigitalOptOutEmail(same("verified@test.com"))(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))
      when(mockPreferencesMetricsRepository.increment(any[String], any[Int])).thenReturn(Future.successful(()))
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      optOutService
        .optOutOfDigital(
          entityId,
          None,
          "generic",
          Some(credentials),
          OptInBundle(Some(optInPage), Some(CustomerOptOut)),
          Some(Language.English)
        )
        .futureValue must be(PreferenceUpdated)

      verify(mockEmailConnector).sendDigitalOptOutEmail(eqTo("verified@test.com"))(any[HeaderCarrier])
      verify(mockEmailConnector, never).sendDigitalOptOutEmail(eqTo("pending@foo.com"))(any[HeaderCarrier])
      verify(mockPreferencesRepository)
        .createOrUpdateTermsAndConditions(any[Preferences], eqTo(Some(credentials)))(any[HeaderCarrier])
    }

    "send an email to the users verified email address when user opting out post opt-in migration" in new TestCase {
      private val pref = Preferences(
        entityId = entityId,
        termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
        email = Some(EmailAddress("verified@test.com", verifiedOn = Some(Dc.instantNow()))),
        pendingEmail = Some(PendingEmailAddress(email = "pending@foo.com")),
        createdAt = Dc.instantNow().minusDays(1),
        updatedAt = Dc.instantNow().minusDays(1)
      )

      when(mockPreferencesRepository.findBy(same(entityId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(pref)))
      when(
        mockPreferencesRepository
          .createOrUpdateTermsAndConditions(any[Preferences], any[Option[Credentials]])(
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(PreferenceUpdated))
      when(mockEmailConnector.sendDigitalOptOutEmail(same("verified@test.com"))(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))
      when(mockPreferencesMetricsRepository.increment(any[String], any[Int])).thenReturn(Future.successful(()))
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      optOutService
        .optOutOfDigital(
          entityId,
          None,
          "generic",
          Some(credentials),
          OptInBundle(Some(optInPage), Some(CustomerOptOut)),
          Some(Language.English)
        )
        .futureValue must be(PreferenceUpdated)

      verify(mockEmailConnector).sendDigitalOptOutEmail(eqTo("verified@test.com"))(any[HeaderCarrier])
      verify(mockEmailConnector, never).sendDigitalOptOutEmail(eqTo("pending@foo.com"))(any[HeaderCarrier])
      verify(mockPreferencesRepository)
        .createOrUpdateTermsAndConditions(any[Preferences], eqTo(Some(credentials)))(any[HeaderCarrier])
    }

    "not send an email to the user's verified email address when user opt out is false pre opt-in migration" in new TestCase {
      private val pref = Preferences(
        entityId = entityId,
        termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
        email = Some(EmailAddress("verified@test.com", verifiedOn = Some(Dc.instantNow()))),
        pendingEmail = Some(PendingEmailAddress(email = "pending@foo.com")),
        createdAt = Dc.instantNow().minusDays(1),
        updatedAt = Dc.instantNow().minusDays(1)
      )

      when(mockPreferencesRepository.findBy(same(entityId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(pref)))
      when(
        mockPreferencesRepository
          .createOrUpdateTermsAndConditions(any[Preferences], any[Option[Credentials]])(
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(PreferenceUpdated))
      when(mockEmailConnector.sendDigitalOptOutEmail(same("verified@test.com"))(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))
      when(mockPreferencesMetricsRepository.increment(any[String], any[Int])).thenReturn(Future.successful(()))
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      optOutService
        .optOutOfDigital(
          entityId,
          Some(ManualOptOut.reason),
          "generic",
          Some(credentials),
          OptInBundle(Some(optInPage), Some(CustomerOptOut)),
          Some(Language.English)
        )
        .futureValue must be(PreferenceUpdated)

      verifyNoInteractions(mockEmailConnector)
      verify(mockPreferencesRepository)
        .createOrUpdateTermsAndConditions(any[Preferences], eqTo(Some(credentials)))(any[HeaderCarrier])
    }

    "not send an email to the user's verified email address when user opt out is false post opt-in migration" in new TestCase {
      private val pref = Preferences(
        entityId = entityId,
        termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
        email = Some(EmailAddress("verified@test.com", verifiedOn = Some(Dc.instantNow()))),
        pendingEmail = Some(PendingEmailAddress(email = "pending@foo.com")),
        createdAt = Dc.instantNow().minusDays(1),
        updatedAt = Dc.instantNow().minusDays(1)
      )

      when(mockPreferencesRepository.findBy(same(entityId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(pref)))
      when(
        mockPreferencesRepository
          .createOrUpdateTermsAndConditions(any[Preferences], any[Option[Credentials]])(
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(PreferenceUpdated))
      when(mockEmailConnector.sendDigitalOptOutEmail(same("verified@test.com"))(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))
      when(mockPreferencesMetricsRepository.increment(any[String], any[Int])).thenReturn(Future.successful(()))
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      optOutService
        .optOutOfDigital(
          entityId,
          Some(ManualOptOut.reason),
          "generic",
          Some(credentials),
          OptInBundle(Some(optInPage), Some(CustomerOptOut)),
          Some(Language.English)
        )
        .futureValue must be(PreferenceUpdated)

      verifyNoInteractions(mockEmailConnector)
      verify(mockPreferencesRepository)
        .createOrUpdateTermsAndConditions(any[Preferences], eqTo(Some(credentials)))(any[HeaderCarrier])
    }

    "not send an email when the verified email is bounced" in new TestCase {
      private val pref = Preferences(
        entityId = entityId,
        termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
        email = Some(
          EmailAddress(
            "verified@test.com",
            verifiedOn = Some(Dc.instantNow()),
            lastBounce = Some(EmailBounce(Some(bounceErrorCode), Dc.instantNow()))
          )
        ),
        createdAt = Dc.instantNow().minusDays(1),
        updatedAt = Dc.instantNow().minusDays(1)
      )

      when(mockPreferencesRepository.findBy(entityId)).thenReturn(Future.successful(Some(pref)))
      when(
        mockPreferencesRepository
          .createOrUpdateTermsAndConditions(any[Preferences], eqTo(Some(credentials)))(any[HeaderCarrier])
      )
        .thenReturn(Future.successful(PreferenceUpdated))
      when(mockPreferencesMetricsRepository.increment(any[String], any[Int])).thenReturn(Future.successful(()))
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      await(
        optOutService
          .optOutOfDigital(
            entityId,
            None,
            "generic",
            Some(credentials),
            OptInBundle(Some(optInPage), Some(CustomerOptOut)),
            Some(Language.English)
          )
      )

      verify(mockEmailConnector, never()).sendDigitalOptOutEmail("verified@test.com")
      verify(mockEmailConnector, never()).sendDigitalOptOutEmail("pending@foo.com")
      verify(mockPreferencesRepository)
        .createOrUpdateTermsAndConditions(any[Preferences], eqTo(Some(credentials)))(any[HeaderCarrier])
    }

    "not send an email when the verified email is unusable and there is a pending address" in new TestCase {
      private val pref = Preferences(
        entityId = entityId,
        termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
        email = Some(
          EmailAddress(
            "verified@test.com",
            verifiedOn = Some(Dc.instantNow()),
            lastBounce = Some(EmailBounce(Some(bounceErrorCode), Dc.instantNow()))
          )
        ),
        pendingEmail = Some(PendingEmailAddress(email = "pending@foo.com")),
        createdAt = Dc.instantNow().minusDays(1),
        updatedAt = Dc.instantNow().minusDays(1)
      )

      when(mockPreferencesRepository.findBy(entityId)).thenReturn(Future.successful(Some(pref)))
      when(
        mockPreferencesRepository
          .createOrUpdateTermsAndConditions(any[Preferences], eqTo(Some(credentials)))(any[HeaderCarrier])
      )
        .thenReturn(Future.successful(PreferenceUpdated))
      when(mockPreferencesMetricsRepository.increment(any[String], any[Int])).thenReturn(Future.successful(()))
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      await(
        optOutService
          .optOutOfDigital(
            entityId,
            None,
            "generic",
            Some(credentials),
            OptInBundle(Some(optInPage), Some(CustomerOptOut)),
            Some(Language.English)
          )
      )

      verify(mockEmailConnector, never()).sendDigitalOptOutEmail("verified@test.com")
      verify(mockEmailConnector, never()).sendDigitalOptOutEmail("pending@foo.com")
      verify(mockPreferencesRepository)
        .createOrUpdateTermsAndConditions(any[Preferences], eqTo(Some(credentials)))(any[HeaderCarrier])
    }

    "not send an email when there is no verified email but there is a pending address" in new TestCase {
      private val pref = Preferences(
        entityId = entityId,
        termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
        pendingEmail = Some(PendingEmailAddress(email = "pending@foo.com")),
        createdAt = Dc.instantNow().minusDays(1),
        updatedAt = Dc.instantNow().minusDays(1)
      )

      when(mockPreferencesRepository.findBy(entityId)).thenReturn(Future.successful(Some(pref)))
      when(
        mockPreferencesRepository
          .createOrUpdateTermsAndConditions(any[Preferences], eqTo(Some(credentials)))(any[HeaderCarrier])
      )
        .thenReturn(Future.successful(PreferenceUpdated))
      when(mockPreferencesMetricsRepository.increment(any[String], any[Int])).thenReturn(Future.successful(()))
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      await(
        optOutService
          .optOutOfDigital(
            entityId,
            None,
            "generic",
            Some(credentials),
            OptInBundle(Some(optInPage), Some(CustomerOptOut)),
            Some(Language.English)
          )
      )

      verify(mockEmailConnector, never).sendDigitalOptOutEmail("verified@test.com")
      verify(mockEmailConnector, never).sendDigitalOptOutEmail("pending@foo.com")
      verify(mockPreferencesRepository)
        .createOrUpdateTermsAndConditions(any[Preferences], eqTo(Some(credentials)))(any[HeaderCarrier])
    }

    "increment userOptOut metric when paperless user opts out pre opt-in migration" in new TestCase {
      private val pref = Preferences(
        entityId = entityId,
        termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
        email = Some(EmailAddress("verified@test.com", verifiedOn = Some(Dc.instantNow()))),
        pendingEmail = Some(PendingEmailAddress(email = "pending@foo.com")),
        createdAt = Dc.instantNow().minusDays(1),
        updatedAt = Dc.instantNow().minusDays(1)
      )

      when(mockPreferencesRepository.findBy(same(entityId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(pref)))
      when(
        mockPreferencesRepository
          .createOrUpdateTermsAndConditions(any[Preferences], any[Option[Credentials]])(
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(PreferenceUpdated))
      when(mockEmailConnector.sendDigitalOptOutEmail(same("verified@test.com"))(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))
      when(mockPreferencesMetricsRepository.increment(any[String], any[Int])).thenReturn(Future.successful(()))
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      optOutService
        .optOutOfDigital(
          entityId,
          None,
          "generic",
          Some(credentials),
          OptInBundle(Some(optInPage), Some(CustomerOptOut)),
          Some(Language.English)
        )
        .futureValue must be(PreferenceUpdated)

      verify(mockPreferencesMetricsRepository, times(1)).increment(eqTo("userOptOut"), eqTo(1))
    }

    "increment userOptOut metric when paperless user opts out post opt-in migration" in new TestCase {
      private val pref = Preferences(
        entityId = entityId,
        termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
        email = Some(EmailAddress("verified@test.com", verifiedOn = Some(Dc.instantNow()))),
        pendingEmail = Some(PendingEmailAddress(email = "pending@foo.com")),
        createdAt = Dc.instantNow().minusDays(1),
        updatedAt = Dc.instantNow().minusDays(1)
      )

      when(mockPreferencesRepository.findBy(same(entityId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(pref)))
      when(
        mockPreferencesRepository
          .createOrUpdateTermsAndConditions(any[Preferences], any[Option[Credentials]])(
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(PreferenceUpdated))
      when(mockEmailConnector.sendDigitalOptOutEmail(same("verified@test.com"))(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))
      when(mockPreferencesMetricsRepository.increment(any[String], any[Int])).thenReturn(Future.successful(()))
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      optOutService
        .optOutOfDigital(
          entityId,
          None,
          "generic",
          Some(credentials),
          OptInBundle(Some(optInPage), Some(CustomerOptOut)),
          Some(Language.English)
        )
        .futureValue must be(PreferenceUpdated)

      verify(mockPreferencesMetricsRepository, times(1)).increment(eqTo("userOptOut"), eqTo(1))
    }

    "increment manualOptOut metric when user is manually opted out pre opt-in migration" in new TestCase {
      when(mockPreferencesRepository.findBy(same(entityId))(any[HeaderCarrier])).thenReturn(Future.successful(None))
      when(
        mockPreferencesRepository
          .createOrUpdateTermsAndConditions(any[Preferences], any[Option[Credentials]])(
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(NewPreferenceCreated))
      when(mockEmailConnector.sendDigitalOptOutEmail(same("verified@test.com"))(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))
      when(mockPreferencesMetricsRepository.increment(any[String], any[Int])).thenReturn(Future.successful(()))
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      optOutService
        .optOutOfDigital(
          entityId,
          Some(ManualOptOut.reason),
          "generic",
          Some(credentials),
          OptInBundle(Some(optInPage), Some(CustomerOptOut)),
          Some(Language.English)
        )
        .futureValue must be(NewPreferenceCreated)

      verify(mockPreferencesMetricsRepository, times(1)).increment(eqTo("manualOptOut"), eqTo(1))
    }

    "increment manualOptOut metric when user is manually opted out post opt-in migration" in new TestCase {
      when(mockPreferencesRepository.findBy(same(entityId))(any[HeaderCarrier])).thenReturn(Future.successful(None))
      when(
        mockPreferencesRepository
          .createOrUpdateTermsAndConditions(any[Preferences], any[Option[Credentials]])(
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(NewPreferenceCreated))
      when(mockEmailConnector.sendDigitalOptOutEmail(same("verified@test.com"))(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))
      when(mockPreferencesMetricsRepository.increment(any[String], any[Int])).thenReturn(Future.successful(()))
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      optOutService
        .optOutOfDigital(
          entityId,
          Some(ManualOptOut.reason),
          "generic",
          Some(credentials),
          OptInBundle(Some(optInPage), Some(CustomerOptOut)),
          Some(Language.English)
        )
        .futureValue must be(NewPreferenceCreated)

      verify(mockPreferencesMetricsRepository, times(1)).increment(eqTo("manualOptOut"), eqTo(1))
    }

    "not increment any preferencesMetrics for new users opting out pre opt-in migration" in new TestCase {
      when(mockPreferencesRepository.findBy(same(entityId))(any[HeaderCarrier])).thenReturn(Future.successful(None))
      when(
        mockPreferencesRepository
          .createOrUpdateTermsAndConditions(any[Preferences], any[Option[Credentials]])(
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(NewPreferenceCreated))
      when(mockPreferencesMetricsRepository.increment(any[String], any[Int])).thenReturn(Future.successful(()))
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      optOutService
        .optOutOfDigital(
          entityId,
          None,
          "generic",
          Some(credentials),
          OptInBundle(Some(optInPage), Some(CustomerOptOut)),
          Some(Language.English)
        )
        .futureValue must be(NewPreferenceCreated)

      verifyNoInteractions(mockPreferencesMetricsRepository)
    }

    "not increment any preferencesMetrics for new users opting out post opt-in migration" in new TestCase {
      when(mockPreferencesRepository.findBy(same(entityId))(any[HeaderCarrier])).thenReturn(Future.successful(None))
      when(
        mockPreferencesRepository
          .createOrUpdateTermsAndConditions(any[Preferences], any[Option[Credentials]])(
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(NewPreferenceCreated))
      when(mockPreferencesMetricsRepository.increment(any[String], any[Int])).thenReturn(Future.successful(()))
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      optOutService
        .optOutOfDigital(
          entityId,
          None,
          "generic",
          Some(credentials),
          OptInBundle(Some(optInPage), Some(CustomerOptOut)),
          Some(Language.English)
        )
        .futureValue must be(NewPreferenceCreated)

      verifyNoInteractions(mockPreferencesMetricsRepository)
    }

    "Updates event information for user initiated opt-out" in new TestCase {
      override val etmpUpdateFlag: Boolean = true
      private val actionTimeStamp = Dc.instantNow().minusYears(1)
      private val termsAndConditionsAcceptedForGenericAndOptInPage =
        TermsAndConditions(Refused(actionTimeStamp, Some(OptIn), Some(OptInPage(Version(1, 2), 1, IPage))))

      private val preference = Some(
        Preferences(
          termsAndConditions = termsAndConditionsAcceptedForGenericAndOptInPage,
          entityId = entityId
        )
      )

      when(mockPreferencesRepository.findBy(entityId)).thenReturn(Future.successful(preference))
      when(
        mockPreferencesRepository
          .createOrUpdateTermsAndConditions(any[Preferences], any[Option[Credentials]])(
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(PreferenceUpdated))
      when(
        mockEmailConnector.sendDigitalOptInEmailVerification(any[String], any[String], any[Boolean])(any[HeaderCarrier])
      )
        .thenReturn(Future.successful(()))
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      await(
        optOutService.optOutOfDigital(
          entityId,
          None,
          "generic",
          Some(credentials),
          OptInBundle(Some(optInPage), Some(CustomerOptOut)),
          lang = Some(English)
        )
      ) must be(PreferenceUpdated)
    }

    "Updates event information for admin initiated opt-out" in new TestCase {
      override val etmpUpdateFlag: Boolean = true
      private val actionTimeStamp = Dc.instantNow().minusYears(1)
      private val termsAndConditionsAcceptedForGenericAndOptInPage =
        TermsAndConditions(Refused(actionTimeStamp, Some(OptIn), Some(OptInPage(Version(1, 2), 1, IPage))))

      private val preference = Some(
        Preferences(
          termsAndConditions = termsAndConditionsAcceptedForGenericAndOptInPage,
          entityId = entityId
        )
      )

      when(mockPreferencesRepository.findBy(entityId)).thenReturn(Future.successful(preference))
      when(
        mockPreferencesRepository
          .createOrUpdateTermsAndConditions(any[Preferences], any[Option[Credentials]])(
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(PreferenceUpdated))
      when(
        mockEmailConnector.sendDigitalOptInEmailVerification(any[String], any[String], any[Boolean])(any[HeaderCarrier])
      )
        .thenReturn(Future.successful(()))
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      await(
        optOutService.optOutOfDigital(
          entityId,
          None,
          "generic",
          Some(credentials),
          OptInBundle(Some(optInPage), Some(AdminOptOut)),
          lang = Some(English)
        )
      ) must be(PreferenceUpdated)
    }

    "Updates event information for user initiated re-opt-out" in new TestCase {
      override val etmpUpdateFlag: Boolean = true
      private val actionTimeStamp = Dc.instantNow().minusYears(1)
      private val termsAndConditionsAcceptedForGenericAndOptInPage =
        TermsAndConditions(Refused(actionTimeStamp, Some(OptIn), Some(OptInPage(Version(1, 2), 1, IPage))))

      private val preference = Some(
        Preferences(
          termsAndConditions = termsAndConditionsAcceptedForGenericAndOptInPage,
          entityId = entityId
        )
      )

      when(mockPreferencesRepository.findBy(entityId)).thenReturn(Future.successful(preference))
      when(
        mockPreferencesRepository
          .createOrUpdateTermsAndConditions(any[Preferences], any[Option[Credentials]])(
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(PreferenceUpdated))
      when(
        mockEmailConnector.sendDigitalOptInEmailVerification(any[String], any[String], any[Boolean])(any[HeaderCarrier])
      )
        .thenReturn(Future.successful(()))
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      await(
        optOutService.optOutOfDigital(
          entityId,
          None,
          "generic",
          Some(credentials),
          OptInBundle(Some(optInPage), Some(CustomerReOptOut)),
          lang = Some(English)
        )
      ) must be(PreferenceUpdated)
    }

    "Do not update preference when language is missing" in new TestCase {
      override val etmpUpdateFlag: Boolean = true
      private val actionTimeStamp = Dc.instantNow().minusYears(1)
      private val termsAndConditionsAcceptedForGenericAndOptInPage =
        TermsAndConditions(Refused(actionTimeStamp, Some(OptIn), Some(OptInPage(Version(1, 2), 1, IPage))))

      private val preference = Some(
        Preferences(
          termsAndConditions = termsAndConditionsAcceptedForGenericAndOptInPage,
          entityId = entityId
        )
      )

      when(mockPreferencesRepository.findBy(entityId)).thenReturn(Future.successful(preference))
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      await(
        optOutService.optOutOfDigital(
          entityId,
          None,
          "generic",
          Some(credentials),
          OptInBundle(Some(optInPage), Some(CustomerReOptOut)),
          lang = None
        )
      ) must be(LanguageNotUpdated)
    }

    "Do not update preference when opt-in page is missing" in new TestCase {
      override val etmpUpdateFlag: Boolean = true
      private val actionTimeStamp = Dc.instantNow().minusYears(1)
      private val termsAndConditionsAcceptedForGenericAndOptInPage =
        TermsAndConditions(Refused(actionTimeStamp, Some(OptIn), Some(OptInPage(Version(1, 2), 1, IPage))))

      private val preference = Some(
        Preferences(
          termsAndConditions = termsAndConditionsAcceptedForGenericAndOptInPage,
          entityId = entityId
        )
      )

      when(mockPreferencesRepository.findBy(entityId)).thenReturn(Future.successful(preference))
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      await(
        optOutService.optOutOfDigital(
          entityId,
          None,
          "generic",
          Some(credentials),
          OptInBundle(None, Some(CustomerReOptOut)),
          lang = Some(English)
        )
      ) must be(LanguageNotUpdated)
    }

    "Do not update preference when eventType is missing" in new TestCase {
      override val etmpUpdateFlag: Boolean = true
      private val actionTimeStamp = Dc.instantNow().minusYears(1)
      private val termsAndConditionsAcceptedForGenericAndOptInPage =
        TermsAndConditions(Refused(actionTimeStamp, Some(OptIn), Some(OptInPage(Version(1, 2), 1, IPage))))

      private val preference = Some(
        Preferences(
          termsAndConditions = termsAndConditionsAcceptedForGenericAndOptInPage,
          entityId = entityId
        )
      )

      when(mockPreferencesRepository.findBy(entityId)).thenReturn(Future.successful(preference))
      when(
        mockPreferencesRepository
          .createOrUpdateTermsAndConditions(any[Preferences], any[Option[Credentials]])(
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(PreferenceUpdated))
      when(
        mockEmailConnector.sendDigitalOptInEmailVerification(any[String], any[String], any[Boolean])(any[HeaderCarrier])
      )
        .thenReturn(Future.successful(()))
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      await(
        optOutService.optOutOfDigital(
          entityId,
          None,
          "generic",
          Some(credentials),
          OptInBundle(Some(optInPage), None),
          lang = Some(English)
        )
      ) must be(LanguageNotUpdated)
    }

    "verify notifyPreferencesChanged is called on condition" in new TestCase {
      override val etmpUpdateFlag: Boolean = true
      private val actionTimeStamp = Dc.instantNow().minusYears(1)
      private val termsAndConditionsAcceptedForGenericAndOptInPage =
        TermsAndConditions(Refused(actionTimeStamp, Some(OptIn), Some(OptInPage(Version(1, 2), 1, IPage))))

      private val preference = Some(
        Preferences(
          termsAndConditions = termsAndConditionsAcceptedForGenericAndOptInPage,
          entityId = entityId
        )
      )

      when(mockPreferencesRepository.findBy(entityId)).thenReturn(Future.successful(preference))
      when(
        mockPreferencesRepository
          .createOrUpdateTermsAndConditions(any[Preferences], any[Option[Credentials]])(
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(PreferenceUpdated))
      when(
        mockEmailConnector.sendDigitalOptInEmailVerification(any[String], any[String], any[Boolean])(any[HeaderCarrier])
      )
        .thenReturn(Future.successful(()))
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      await(
        optOutService.optOutOfDigital(
          entityId,
          None,
          "generic",
          Some(credentials),
          OptInBundle(Some(optInPage), None),
          lang = Some(English)
        )
      ) must be(LanguageNotUpdated)
      verify(mockPCNService)
        .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
          any[ExecutionContext],
          any[HeaderCarrier]
        )
    }

    "verify notifyPreferencesChanged is NOT called on condition" in new TestCase {
      override val etmpUpdateFlag: Boolean = true
      private val actionTimeStamp = Dc.instantNow().minusYears(1)
      private val termsAndConditionsAcceptedForGenericAndOptInPage =
        TermsAndConditions(Refused(actionTimeStamp, Some(OptIn), Some(OptInPage(Version(1, 2), 1, IPage))))

      private val preference = Some(
        Preferences(
          termsAndConditions = termsAndConditionsAcceptedForGenericAndOptInPage,
          entityId = entityId
        )
      )

      when(mockPreferencesRepository.findBy(entityId)).thenReturn(Future.successful(preference))
      when(
        mockPreferencesRepository
          .createOrUpdateTermsAndConditions(any[Preferences], any[Option[Credentials]])(
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(PreferenceUpdated))
      when(
        mockEmailConnector.sendDigitalOptInEmailVerification(any[String], any[String], any[Boolean])(any[HeaderCarrier])
      )
        .thenReturn(Future.successful(()))
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

//      await {
//        optOutService.optOutOfDigital(
//          entityId,
//          None,
//          "generic",
//          Some(credentials),
//          OptInBundle(Some(optInPage), Some(SystemOptOut)),
//          lang = None
//        )
//        verifyNoInteractions(mockPCNService)
//      }
    }
  }

  trait TestCase {

    val etmpUpdateFlag: Boolean = false

    val termsAndConditionsAcceptedForGenericOnly: TermsAndConditions = TermsAndConditions(Accepted(Dc.instantNow()))
    val refusedTermsAndConditionsForGeneric: TermsAndConditions = TermsAndConditions(generic = Refused(now))

    val userOptOut = true
    val manualOptOut = false
    val bounceErrorCode = 123
    val optInPage: OptInPage = OptInPage(Version(1, 2), 1, PageType.IPage)

    val entityId: EntityId = GenerateRandom.entityId()
    val mockDataEventConsumer: DataEvent => Unit = mock[DataEvent => Unit]

    val mockPreferencesMetricsRepository: PreferencesMetricsRepository = mock[PreferencesMetricsRepository]
    val mockPreferencesRepository: PreferencesRepository = mock[PreferencesRepository]
    val mockEmailConnector: EmailConnector = mock[EmailConnector]
    val mockPCNService: PreferencesChangedNotifierService = mock[PreferencesChangedNotifierService]
    val mockAuditConnector: AuditConnector = mock[AuditConnector]
    val mockEtmpService: ETMPService = mock[ETMPService]
    val mockAuditable: Auditable = new Auditable {
      override def appName: String = "test"
      override def audit: Audit = new Audit("test", mockAuditConnector)
    }

    implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

    lazy val optOutService: OptOutService =
      new OptOutService(
        mockEtmpService,
        mockPreferencesRepository,
        mockPreferencesMetricsRepository,
        mockEmailConnector,
        mockPCNService,
        mockAuditable,
        etmpUpdateFlag
      )

    when(mockEtmpService.checkAndUpdateETMP(any[EntityId], any[Boolean], any[Option[String]])(any[HeaderCarrier]))
      .thenReturn(Future.successful(ETMPUpdateSuccess))
  }
}
