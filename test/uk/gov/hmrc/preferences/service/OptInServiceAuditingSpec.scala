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
import uk.gov.hmrc.preferences.controllers.model.{ Credentials, TermsAndConditionsRequest }
import uk.gov.hmrc.preferences.model.MessageDeliveryFormat.Paper
import uk.gov.hmrc.preferences.model.TermsAndConditions._
import uk.gov.hmrc.preferences.model._
import uk.gov.hmrc.preferences.repository.{ PreferenceUpdated, PreferencesRepository }
import uk.gov.hmrc.preferences.util.Dc

import java.time.Instant
import scala.concurrent.{ ExecutionContext, Future }

class OptInServiceAuditingSpec extends PlaySpec with MockitoSugar {

  "Calling optInToDigital" should {

    "Generate a success audit event for generic when preferences are successfully stored when user has no preference pre opt-in migration" in new TestCase {
      private val exampleEmail = "test@test.com"

      private val originalPrefs = None
      when(mockPreferencesRepository.findBy(any[EntityId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(originalPrefs))
      when(
        mockPreferencesRepository
          .createOrUpdateTermsAndConditions(any[Preferences], eqTo(Some(credentials)))(any[HeaderCarrier])
      )
        .thenReturn(Future.successful(PreferenceUpdated))
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      await(
        resource.optInToDigital(
          entityId = entityId,
          email = exampleEmail,
          "generic",
          termsAndConditionsRequest,
          Some(credentials),
          OptInBundle(Some(OptInPage(Version(1, 0), cohort = 8, pageType = PageType.IPage)), Some(OptEventType.OptIn))
        )
      )

      testAudit.capturedTxName mustBe "Set Print Preference"
      testAudit.capturedInputs must contain.allOf(
        "entityId"           -> "1111",
        "preference-digital" -> "true",
        "email"              -> exampleEmail,
        "termsAndConditions" -> "generic",
        "affinityGroup"      -> "Individual",
        "confidenceLevel"    -> "200",
        "optInPageMajor"     -> "1",
        "optInPageMinor"     -> "0",
        "optInPageCohort"    -> "8",
        "optInPagePageType"  -> "IPage",
        "eventType"          -> "OptIn"
      )
      testAudit.capturedInputs must have size 11
      private val dataEvent =
        testAudit.capturedDataEvents.filter(_.tags("transactionName") == "Opt In Email Reminders").head
      dataEvent.auditSource must be("test")
      dataEvent.auditType must be("TxSucceeded")
      dataEvent.eventId must not be empty
      dataEvent.generatedAt must not be {}
      dataEvent.tags must {
        contain("transactionName" -> "Opt In Email Reminders") and
          contain("reason"        -> "User Selected to Opt In")
      }
      dataEvent.detail must {
        contain("entityId"       -> "1111") and
          contain("emailAddress" -> "test@test.com")
        contain("termsAndConditions" -> "generic")
      }
      dataEvent.tags.get(HeaderNames.xRequestId) must not be empty
    }

    "Generate a success audit event for generic when preferences are successfully stored when user has no preference post opt-in migration" in new TestCase {
      private val exampleEmail = "test@test.com"

      private val originalPrefs = None
      when(mockPreferencesRepository.findBy(any[EntityId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(originalPrefs))
      when(
        mockPreferencesRepository
          .createOrUpdateTermsAndConditions(any[Preferences], eqTo(Some(credentials)))(any[HeaderCarrier])
      )
        .thenReturn(Future.successful(PreferenceUpdated))
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      await(
        resource.optInToDigital(
          entityId = entityId,
          email = exampleEmail,
          "generic",
          termsAndConditionsRequest,
          Some(credentials),
          OptInBundle(Some(optInPage), Some(OptEventType.OptIn))
        )
      )

      testAudit.capturedTxName mustBe "Set Print Preference"
      testAudit.capturedInputs must contain.allOf(
        "entityId"           -> "1111",
        "preference-digital" -> "true",
        "email"              -> exampleEmail,
        "termsAndConditions" -> "generic",
        "affinityGroup"      -> "Individual",
        "confidenceLevel"    -> "200",
        "optInPageMajor"     -> "1",
        "optInPageMinor"     -> "2",
        "optInPageCohort"    -> "1",
        "optInPagePageType"  -> "IPage",
        "eventType"          -> "OptIn"
      )
      testAudit.capturedInputs must have size 11
      private val dataEvent =
        testAudit.capturedDataEvents.filter(_.tags("transactionName") == "Opt In Email Reminders").head
      dataEvent.auditSource must be("test")
      dataEvent.auditType must be("TxSucceeded")
      dataEvent.eventId must not be empty
      dataEvent.generatedAt must not be {}
      dataEvent.tags must {
        contain("transactionName" -> "Opt In Email Reminders") and
          contain("reason"        -> "User Selected to Opt In")
      }
      dataEvent.detail must {
        contain("entityId"       -> "1111") and
          contain("emailAddress" -> "test@test.com")
        contain("termsAndConditions" -> "generic")
      }
      dataEvent.tags.get(HeaderNames.xRequestId) must not be empty
    }

    "Generate a success audit event when email address verification link is sent to user on opt in pre opt-in migration" in new TestCase {

      // given
      private val exampleEmail = "test@test.com"
      private val originalPrefs =
        Some(Preferences(entityId = entityId, termsAndConditions = termsAndConditionsRefusedForGenericOnly))

      when(mockPreferencesRepository.findBy(any[EntityId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(originalPrefs))
      when(
        mockPreferencesRepository.createOrUpdateTermsAndConditions(any[Preferences], eqTo(Some(credentials)))(
          any[HeaderCarrier]
        )
      )
        .thenReturn(Future.successful(PreferenceUpdated))
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      // when
      await(
        resource
          .optInToDigital(
            entityId,
            exampleEmail,
            "generic",
            termsAndConditionsRequest,
            Some(credentials),
            OptInBundle(Some(optInPage), Some(OptEventType.OptIn))
          )
      )

      // then
      private val dataEvent =
        testAudit.capturedDataEvents.filter(_.tags("transactionName") == "Email Verification Link Sent").head
      dataEvent.auditSource must be("test")
      dataEvent.auditType must be("TxSucceeded")
      dataEvent.eventId must not be empty
      dataEvent.generatedAt must not be {}

      dataEvent.detail must contain.allOf(
        "entityId"         -> "1111",
        "emailAddress"     -> "test@test.com",
        "verificationType" -> "optedIn"
      )
      dataEvent.tags.get(HeaderNames.xRequestId) must not be empty

      dataEvent.detail("verificationLink") must include(verificationLinkPrefix)
    }

    "Generate a success audit event when email address verification link is sent to user on opt in post opt-in migration" in new TestCase {

      // given
      private val exampleEmail = "test@test.com"
      private val originalPrefs =
        Some(Preferences(entityId = entityId, termsAndConditions = termsAndConditionsRefusedForGenericOnly))

      when(mockPreferencesRepository.findBy(any[EntityId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(originalPrefs))
      when(
        mockPreferencesRepository.createOrUpdateTermsAndConditions(any[Preferences], eqTo(Some(credentials)))(
          any[HeaderCarrier]
        )
      )
        .thenReturn(Future.successful(PreferenceUpdated))
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      // when
      await(
        resource
          .optInToDigital(
            entityId,
            exampleEmail,
            "generic",
            termsAndConditionsRequest,
            Some(credentials),
            OptInBundle(Some(optInPage), Some(OptEventType.OptIn))
          )
      )

      // then
      private val dataEvent =
        testAudit.capturedDataEvents.filter(_.tags("transactionName") == "Email Verification Link Sent").head
      dataEvent.auditSource must be("test")
      dataEvent.auditType must be("TxSucceeded")
      dataEvent.eventId must not be empty
      dataEvent.generatedAt must not be {}

      dataEvent.detail must contain
        .allOf("entityId" -> "1111", "emailAddress" -> "test@test.com", "verificationType" -> "optedIn")

      dataEvent.tags.get(HeaderNames.xRequestId) must not be empty

      dataEvent.detail("verificationLink") must include(verificationLinkPrefix)
    }

  }

  trait TestCase {

    import scala.concurrent.ExecutionContext.Implicits.global

    implicit val hc: HeaderCarrier = HeaderCarrier()

    val termsAndConditionsRefusedForGenericOnly: TermsAndConditions = TermsAndConditions(Refused(Dc.instantNow()))
    val entityId: EntityId = EntityId("1111")
    val termsAndConditionsAcceptedForGenericOnly: TermsAndConditions = TermsAndConditions(
      Accepted(Dc.instantNow(), None)
    )
    val optInPage: OptInPage = OptInPage(Version(1, 2), 1, PageType.IPage)
    val mockPreferencesRepository: PreferencesRepository = mock[PreferencesRepository]
    val verificationLinkPrefix = "/verification-link/"
    val mockExternalVerificationLink: EmailVerificationLink => String = _ => verificationLinkPrefix
    val mockEmailConnector: EmailConnector = mock[EmailConnector]
    val mockAuditConnector: AuditConnector = mock[AuditConnector]
    val mockEtmpService: ETMPService = mock[ETMPService]
    val mockPCNService: PreferencesChangedNotifierService = mock[PreferencesChangedNotifierService]
    val mockChangeEmailService: ChangeEmailService = mock[ChangeEmailService]
    val testAudit = new TestAudit(mockAuditConnector)
    val testAuditable: Auditable = new Auditable {
      override def appName: String = "test"
      override def audit: Audit = testAudit
    }
    lazy val resource: OptInService = new OptInService(
      mockPreferencesRepository,
      () => Instant.now,
      mockExternalVerificationLink,
      mockEmailConnector,
      testAuditable,
      mockChangeEmailService,
      mockPCNService
    )

    when(
      mockEmailConnector.sendDigitalOptInEmailVerification(any[String], any[String], any[Boolean])(any[HeaderCarrier])
    )
      .thenReturn(Future.successful(()))
    when(mockEmailConnector.sendEmailChangedNotification(any[String])(any[HeaderCarrier]))
      .thenReturn(Future.successful(()))
    when(mockEmailConnector.sendChangedEmailAddressVerification(any[String], any[String])(any[HeaderCarrier]))
      .thenReturn(Future.successful(()))
    when(mockEmailConnector.sendDigitalOptOutEmail(any[String])(any[HeaderCarrier])).thenReturn(Future.successful(()))

    val termsAndConditionsRequest: TermsAndConditionsRequest =
      TermsAndConditionsRequest(None, None, None, None, language = Option(Language.English))
    val credentials: Credentials =
      Credentials(affinityGroup = Some(AffinityGroup.Individual), ConfidenceLevel.L200)

  }

}
