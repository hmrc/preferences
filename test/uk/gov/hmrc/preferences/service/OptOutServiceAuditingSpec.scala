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

import org.bson.types.ObjectId
import org.mockito.ArgumentMatchers.{ any, eq => eqTo }
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.retrieve.Name
import uk.gov.hmrc.auth.core.{ AffinityGroup, ConfidenceLevel }
import uk.gov.hmrc.http.{ HeaderCarrier, HeaderNames }
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.Audit
import uk.gov.hmrc.preferences.Auditable
import uk.gov.hmrc.preferences.connector.EmailConnector
import uk.gov.hmrc.preferences.controllers.TestAudit
import uk.gov.hmrc.preferences.controllers.model.Credentials
import uk.gov.hmrc.preferences.model.MessageDeliveryFormat.Paper
import uk.gov.hmrc.preferences.model.TermsAndConditions._
import uk.gov.hmrc.preferences.model._
import uk.gov.hmrc.preferences.repository.{ PreferenceUpdated, PreferencesMetricsRepository, PreferencesRepository }
import uk.gov.hmrc.preferences.util.DateTimeExtensions.InstantExtensions
import uk.gov.hmrc.preferences.util.Dc

import java.time.Instant
import scala.concurrent.{ ExecutionContext, Future }

class OptOutServiceAuditingSpec extends PlaySpec with MockitoSugar {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  lazy val now: Instant = Dc.instantNow()

  private lazy val credentials =
    Credentials(Some(AffinityGroup.Individual), ConfidenceLevel.L200)

  "Calling optOutOfDigital" should {
    "generate a success audit event when preferences are successfully stored for opt out when user has non-paperless preference pre opt-in migration" in new TestCase {
      private val originalPrefs = Some(
        Preferences(
          entityId = entityId,
          termsAndConditions = termsAndConditionsRefusedForGenericOnly,
          email = None,
          pendingEmail = None
        )
      )
      when(mockPreferencesRepository.findBy(any[EntityId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(originalPrefs))
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      await(resource.optOutOfDigital(entityId, None, "generic", Some(credentials), OptInBundle()))

      testAudit.capturedTxName mustBe "Set Print Preference"
      testAudit.capturedInputs must contain.allOf(
        "entityId"           -> "1111",
        "preference-digital" -> "false",
        "preference-reason"  -> "User Selected to Opt Out",
        "termsAndConditions" -> "generic"
      )
      private val dataEvent =
        testAudit.capturedDataEvents.filter(_.tags("transactionName") == "Opt Out Email Reminders").head
      dataEvent.auditSource must be("test")
      dataEvent.auditType must be("TxSucceeded")
      dataEvent.eventId must not be empty
      dataEvent.generatedAt must not be null
      dataEvent.tags must (
        contain("transactionName" -> "Opt Out Email Reminders") and
          contain("reason"        -> "User Selected to Opt Out")
      )
      dataEvent.detail must contain.allOf(
        "entityId"           -> "1111",
        "wasDigital"         -> "false",
        "wasVerified"        -> "false",
        "wasBounced"         -> "false",
        "termsAndConditions" -> "generic"
      )
      dataEvent.tags.get(HeaderNames.xRequestId) must not be empty
    }

    "generate a success audit event when preferences are successfully stored for opt out when user has non-paperless preference post opt-in migration" in new TestCase {
      private val originalPrefs = Some(
        Preferences(
          entityId = entityId,
          termsAndConditions = termsAndConditionsRefusedForGenericOnly,
          email = None,
          pendingEmail = None
        )
      )
      when(mockPreferencesRepository.findBy(any[EntityId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(originalPrefs))
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      await(
        resource
          .optOutOfDigital(entityId, None, "generic", Some(credentials), OptInBundle(Some(optInPage)))
      )

      testAudit.capturedTxName mustBe "Set Print Preference"
      testAudit.capturedInputs must contain.allOf(
        "entityId"           -> "1111",
        "preference-digital" -> "false",
        "preference-reason"  -> "User Selected to Opt Out",
        "termsAndConditions" -> "generic"
      )
      private val dataEvent =
        testAudit.capturedDataEvents.filter(_.tags("transactionName") == "Opt Out Email Reminders").head
      dataEvent.auditSource must be("test")
      dataEvent.auditType must be("TxSucceeded")
      dataEvent.eventId must not be empty
      dataEvent.generatedAt must not be null
      dataEvent.tags must (
        contain("transactionName" -> "Opt Out Email Reminders") and
          contain("reason"        -> "User Selected to Opt Out")
      )
      dataEvent.detail must contain.allOf(
        "entityId"           -> "1111",
        "wasDigital"         -> "false",
        "wasVerified"        -> "false",
        "wasBounced"         -> "false",
        "termsAndConditions" -> "generic"
      )
      dataEvent.tags.get(HeaderNames.xRequestId) must not be empty
    }

    "generate a success audit event when preferences are successfully stored for opt out when user has paperless preference pre opt-in migraion" in new TestCase {
      private val exampleEmail = "test@test.com"
      private val originalPrefs = Some(
        Preferences(
          entityId = entityId,
          termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
          email = Some(
            EmailAddress(
              email = exampleEmail,
              lastBounce = Some(EmailBounce(errorCode = None, timestamp = Instant.now)),
              verifiedOn = Some(Instant.now)
            )
          ),
          pendingEmail = None
        )
      )
      when(mockPreferencesRepository.findBy(any[EntityId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(originalPrefs))
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      await(resource.optOutOfDigital(entityId, None, "generic", Some(credentials), OptInBundle()))

      private val dataEvent =
        testAudit.capturedDataEvents.filter(_.tags("transactionName") == "Opt Out Email Reminders").head

      dataEvent.detail must contain.allOf(
        "wasDigital"         -> "true",
        "wasVerified"        -> "true",
        "wasBounced"         -> "true",
        "termsAndConditions" -> "generic"
      )
    }

    "generate a success audit event when preferences are successfully stored for opt out when user has paperless preference post opt-in migration" in new TestCase {
      private val exampleEmail = "test@test.com"
      private val originalPrefs = Some(
        Preferences(
          entityId = entityId,
          termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
          email = Some(
            EmailAddress(
              email = exampleEmail,
              lastBounce = Some(EmailBounce(errorCode = None, timestamp = Instant.now)),
              verifiedOn = Some(Instant.now)
            )
          ),
          pendingEmail = None
        )
      )
      when(mockPreferencesRepository.findBy(any[EntityId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(originalPrefs))
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      await(
        resource
          .optOutOfDigital(entityId, None, "generic", Some(credentials), OptInBundle(Some(optInPage)))
      )

      private val dataEvent =
        testAudit.capturedDataEvents.filter(_.tags("transactionName") == "Opt Out Email Reminders").head

      dataEvent.detail must contain.allOf(
        "wasDigital"         -> "true",
        "wasVerified"        -> "true",
        "wasBounced"         -> "true",
        "termsAndConditions" -> "generic"
      )
    }

    "generate a success audit event when preferences are successfully stored for opt out when user has generic paperless preference pre opt-in migration" in new TestCase {
      private val exampleEmail = "test@test.com"
      private val originalPrefs = Some(
        Preferences(
          entityId = entityId,
          termsAndConditions = TermsAndConditions(Accepted(Dc.instantNow(), None)),
          email = Some(EmailAddress(email = exampleEmail, lastBounce = None, verifiedOn = Some(Instant.now))),
          pendingEmail = None
        )
      )
      when(mockPreferencesRepository.findBy(any[EntityId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(originalPrefs))
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      await(resource.optOutOfDigital(entityId, None, "generic", Some(credentials), OptInBundle()))

      private val dataEvent =
        testAudit.capturedDataEvents.filter(_.tags("transactionName") == "Opt Out Email Reminders").head

      dataEvent.detail must contain.allOf(
        "wasDigital"         -> "true",
        "wasVerified"        -> "true",
        "wasBounced"         -> "false",
        "termsAndConditions" -> "generic"
      )
    }

    "generate a success audit event when preferences are successfully stored for opt out when user has generic paperless preference post opt-in migration" in new TestCase {
      private val exampleEmail = "test@test.com"
      private val originalPrefs = Some(
        Preferences(
          entityId = entityId,
          termsAndConditions = TermsAndConditions(Accepted(Dc.instantNow(), None)),
          email = Some(EmailAddress(email = exampleEmail, lastBounce = None, verifiedOn = Some(Instant.now))),
          pendingEmail = None
        )
      )
      when(mockPreferencesRepository.findBy(any[EntityId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(originalPrefs))
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      await(
        resource
          .optOutOfDigital(entityId, None, "generic", Some(credentials), OptInBundle(Some(optInPage)))
      )

      private val dataEvent =
        testAudit.capturedDataEvents.filter(_.tags("transactionName") == "Opt Out Email Reminders").head

      dataEvent.detail must contain.allOf(
        "wasDigital"         -> "true",
        "wasVerified"        -> "true",
        "wasBounced"         -> "false",
        "termsAndConditions" -> "generic"
      )
    }

    "generate a success audit event when preferences are successfully stored with a reason when user has non-digital preference pre opt-in migration" in new TestCase {
      private val originalPrefs = Some(
        Preferences(
          entityId = entityId,
          termsAndConditions = termsAndConditionsRefusedForGenericOnly,
          email = None,
          pendingEmail = None
        )
      )
      when(mockPreferencesRepository.findBy(any[EntityId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(originalPrefs))
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      await(
        resource
          .optOutOfDigital(entityId, Some("Automaticness"), "generic", Some(credentials), OptInBundle())
      )

      testAudit.capturedTxName mustBe "Set Print Preference"
      testAudit.capturedInputs must contain("preference-reason" -> "Automaticness")
      private val dataEvent =
        testAudit.capturedDataEvents.filter(_.tags("transactionName") == "Opt Out Email Reminders").head
      dataEvent.tags must contain("reason" -> "Automaticness")
    }

    "generate a success audit event when preferences are successfully stored with a reason when user has non-digital preference post opt-in migration" in new TestCase {
      private val originalPrefs = Some(
        Preferences(
          entityId = entityId,
          termsAndConditions = termsAndConditionsRefusedForGenericOnly,
          email = None,
          pendingEmail = None
        )
      )
      when(mockPreferencesRepository.findBy(any[EntityId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(originalPrefs))
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      await(
        resource
          .optOutOfDigital(entityId, Some("Automaticness"), "generic", Some(credentials), OptInBundle(Some(optInPage)))
      )

      testAudit.capturedTxName mustBe "Set Print Preference"
      testAudit.capturedInputs must contain("preference-reason" -> "Automaticness")
      private val dataEvent =
        testAudit.capturedDataEvents.filter(_.tags("transactionName") == "Opt Out Email Reminders").head
      dataEvent.tags must contain("reason" -> "Automaticness")
    }

    "only send an email to the users verified email address when opting out pre opt-in migration" in new TestCase {
      private val reason = "Something else happened"
      private val originalPrefs = Some(
        Preferences(
          entityId = entityId,
          termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
          email = Some(EmailAddress("verified@test.com", verifiedOn = Some(Dc.instantNow()))),
          pendingEmail = Some(PendingEmailAddress(email = "pending@foo.com")),
          createdAt = Dc.instantNow().minusDays(1),
          updatedAt = Dc.instantNow().minusDays(1)
        )
      )
      when(mockPreferencesRepository.findBy(any[EntityId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(originalPrefs))
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      await(resource.optOutOfDigital(entityId, Some(reason), "generic", Some(credentials), OptInBundle()))

      private val dataEvent =
        testAudit.capturedDataEvents.filter(_.tags("transactionName") == "Opt Out Email Reminders").head
      dataEvent.auditSource must be("test")
      dataEvent.auditType must be("TxSucceeded")
      dataEvent.eventId must not be empty
      dataEvent.generatedAt must not be {}
      dataEvent.tags must {
        contain("transactionName" -> "Opt Out Email Reminders") and
          contain("reason"        -> reason)
      }
      dataEvent.detail must {
        contain("entityId"             -> "1111") and
          contain("termsAndConditions" -> "generic")
      }

      dataEvent.tags.get(HeaderNames.xRequestId) must not be empty
    }

    "only send an email to the users verified email address when opting out post opt-in migration" in new TestCase {
      private val reason = "Something else happened"
      private val originalPrefs = Some(
        Preferences(
          entityId = entityId,
          termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
          email = Some(EmailAddress("verified@test.com", verifiedOn = Some(Dc.instantNow()))),
          pendingEmail = Some(PendingEmailAddress(email = "pending@foo.com")),
          createdAt = Dc.instantNow().minusDays(1),
          updatedAt = Dc.instantNow().minusDays(1)
        )
      )
      when(mockPreferencesRepository.findBy(any[EntityId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(originalPrefs))
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      await(
        resource.optOutOfDigital(entityId, Some(reason), "generic", Some(credentials), OptInBundle(Some(optInPage)))
      )

      private val dataEvent =
        testAudit.capturedDataEvents.filter(_.tags("transactionName") == "Opt Out Email Reminders").head
      dataEvent.auditSource must be("test")
      dataEvent.auditType must be("TxSucceeded")
      dataEvent.eventId must not be empty
      dataEvent.generatedAt must not be null
      dataEvent.tags must {
        contain("transactionName" -> "Opt Out Email Reminders") and
          contain("reason"        -> reason)
      }
      dataEvent.detail must {
        contain("entityId"             -> "1111") and
          contain("termsAndConditions" -> "generic")
      }

      dataEvent.tags.get(HeaderNames.xRequestId) must not be empty
    }
  }

  trait TestCase {

    import scala.concurrent.ExecutionContext.Implicits.global

    val etmpUpdateFlag: Boolean = false
    val entityId: EntityId = EntityId("1111")
    val optInPage: OptInPage = OptInPage(Version(1, 2), 1, PageType.IPage)
    val termsAndConditionsAcceptedForGenericOnly: TermsAndConditions =
      TermsAndConditions(Accepted(Dc.instantNow(), None))
    val termsAndConditionsRefusedForGenericOnly: TermsAndConditions =
      TermsAndConditions(Refused(Dc.instantNow()))

    val mockPreferencesRepository: PreferencesRepository = mock[PreferencesRepository]
    val mockPreferencesMetricsRepository: PreferencesMetricsRepository = mock[PreferencesMetricsRepository]
    val mockEmailConnector: EmailConnector = mock[EmailConnector]
    val mockAuditConnector: AuditConnector = mock[AuditConnector]
    val mockPCNService: PreferencesChangedNotifierService = mock[PreferencesChangedNotifierService]
    val mockEtmpService: ETMPService = mock[ETMPService]
    val testAudit = new TestAudit(mockAuditConnector)
    val testAuditable: Auditable = new Auditable {
      override def appName: String = "test"
      override def audit: Audit = testAudit
    }
    lazy val resource: OptOutService = new OptOutService(
      mockEtmpService,
      mockPreferencesRepository,
      mockPreferencesMetricsRepository,
      mockEmailConnector,
      mockPCNService,
      testAuditable: Auditable,
      etmpUpdateFlag
    )

    when(mockPreferencesMetricsRepository.increment(any[String], any[Int])).thenReturn(Future.successful((): Unit))

    when(
      mockPreferencesRepository
        .createOrUpdateTermsAndConditions(any[Preferences], any[Option[Credentials]])(any[HeaderCarrier])
    )
      .thenReturn(Future.successful(PreferenceUpdated))

    when(mockEmailConnector.sendDigitalOptOutEmail(any[String])(any[HeaderCarrier]))
      .thenReturn(Future.successful((): Unit))

  }

}
