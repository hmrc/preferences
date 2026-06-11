/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.service

import org.bson.types.ObjectId
import org.mockito.ArgumentMatchers.{ any, eq => eqTo }
import org.mockito.Mockito.{ verify, verifyNoInteractions, when }
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.retrieve.Name
import uk.gov.hmrc.auth.core.{ AffinityGroup, ConfidenceLevel }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.Audit
import uk.gov.hmrc.preferences.Auditable
import uk.gov.hmrc.preferences.connector.EmailConnector
import uk.gov.hmrc.preferences.controllers.model.{ Credentials, TermsAndConditionsRequest }
import uk.gov.hmrc.preferences.model.OptEventType.OptIn
import uk.gov.hmrc.preferences.model.MessageDeliveryFormat.{ Digital, Paper }
import uk.gov.hmrc.preferences.model.PageType.IPage
import uk.gov.hmrc.preferences.model.TermsAndConditions._
import uk.gov.hmrc.preferences.model._
import uk.gov.hmrc.preferences.repository.{ NewPreferenceCreated, PreferenceUpdated, PreferencesRepository }
import uk.gov.hmrc.preferences.util.DateTimeExtensions.InstantExtensions
import uk.gov.hmrc.preferences.util.Dc
import utils.GenerateRandom

import java.time.Instant
import scala.concurrent.{ ExecutionContext, Future }

class OptInServiceSpec extends PlaySpec with MockitoSugar with ScalaFutures {

  lazy val credentials: Credentials =
    Credentials(Some(AffinityGroup.Individual), ConfidenceLevel.L200)
  lazy val emailAddress = "new@email.com"
  lazy val termsAndConditionsRequest: TermsAndConditionsRequest =
    TermsAndConditionsRequest(
      None,
      Some(emailAddress),
      Some("returnTest"),
      Some("returnUrl"),
      language = Option(Language.English)
    )

  "Opting-in to digital" should {

    "send (non-forced) a verification link email for generic terms and conditions" in new TestCase {

      when(mockPreferencesRepository.findBy(entityId)).thenReturn(Future.successful(None))
      when(
        mockPreferencesRepository
          .createOrUpdateTermsAndConditions(any[Preferences], any[Option[Credentials]])(
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(NewPreferenceCreated))
      when(
        mockEmailConnector.sendDigitalOptInEmailVerification(any[String], any[String], any[Boolean])(any[HeaderCarrier])
      )
        .thenReturn(Future.successful {})
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      await(
        optInService
          .optInToDigital(
            entityId,
            emailAddress,
            "generic",
            TermsAndConditionsRequest(
              Some(TermsAndConditionsRequest.UserAcceptance(accepted = true)),
              Some(emailAddress),
              Some(""),
              Some(""),
              language = Option(Language.English)
            ),
            credentials = Some(credentials),
            OptInBundle(Some(optInPage), Some(OptIn))
          )
      ) must be(NewPreferenceCreated)

      verify(mockEmailConnector).sendDigitalOptInEmailVerification(eqTo(emailAddress), eqTo(dummyLink), eqTo(false))(
        any[HeaderCarrier]
      )
    }

    "send (non-forced) a verification link email for generic terms and conditions with returnText and returnUrl pre opt-in migration" in new TestCase {
      private val emailAddress = "new@email.com"

      when(mockPreferencesRepository.findBy(entityId)).thenReturn(Future.successful(None))
      when(
        mockPreferencesRepository
          .createOrUpdateTermsAndConditions(any[Preferences], any[Option[Credentials]])(
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(NewPreferenceCreated))
      when(
        mockEmailConnector.sendDigitalOptInEmailVerification(any[String], any[String], any[Boolean])(any[HeaderCarrier])
      )
        .thenReturn(Future.successful {})
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      await(
        optInService
          .optInToDigital(
            entityId,
            emailAddress,
            "generic",
            termsAndConditionsRequest,
            Some(credentials),
            OptInBundle(Some(optInPage), Some(OptIn))
          )
      ) must be(NewPreferenceCreated)
      verify(mockEmailConnector).sendDigitalOptInEmailVerification(eqTo(emailAddress), eqTo(dummyLink), eqTo(false))(
        any[HeaderCarrier]
      )
    }

    "send (non-forced) a verification link email for generic terms and conditions with returnText and returnUrl post opt-in migration" in new TestCase {
      private val emailAddress = "new@email.com"

      when(mockPreferencesRepository.findBy(entityId)).thenReturn(Future.successful(None))
      when(
        mockPreferencesRepository
          .createOrUpdateTermsAndConditions(any[Preferences], any[Option[Credentials]])(
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(NewPreferenceCreated))
      when(
        mockEmailConnector.sendDigitalOptInEmailVerification(any[String], any[String], any[Boolean])(any[HeaderCarrier])
      )
        .thenReturn(Future.successful {})
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      await(
        optInService
          .optInToDigital(
            entityId,
            emailAddress,
            "generic",
            termsAndConditionsRequest,
            Some(credentials),
            OptInBundle(Some(optInPage), Some(OptIn))
          )
      ) must be(NewPreferenceCreated)
      verify(mockEmailConnector).sendDigitalOptInEmailVerification(eqTo(emailAddress), eqTo(dummyLink), eqTo(false))(
        any[HeaderCarrier]
      )
    }

    "skip sending verification email when email service is down pre opt-in migration" in new TestCase {
      private val emailAddress = "new@email.com"

      when(mockPreferencesRepository.findBy(entityId)).thenReturn(Future.successful(None))
      when(
        mockPreferencesRepository
          .createOrUpdateTermsAndConditions(any[Preferences], any[Option[Credentials]])(
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(NewPreferenceCreated))
      when(
        mockEmailConnector.sendDigitalOptInEmailVerification(any[String], any[String], any[Boolean])(any[HeaderCarrier])
      ).thenReturn(Future.failed {
        new RuntimeException()
      })
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      await(
        optInService
          .optInToDigital(
            entityId,
            emailAddress,
            "generic",
            TermsAndConditionsRequest(
              None,
              Some(emailAddress),
              Some("returnTest"),
              Some("returnUrl"),
              language = Option(Language.English)
            ),
            Some(credentials),
            OptInBundle(Option(optInPage), Option(OptIn))
          )
      ) must be(NewPreferenceCreated)
    }

    "skip sending verification email when email service is down post opt-in migration" in new TestCase {
      private val emailAddress = "new@email.com"

      when(mockPreferencesRepository.findBy(entityId)).thenReturn(Future.successful(None))
      when(
        mockPreferencesRepository
          .createOrUpdateTermsAndConditions(any[Preferences], any[Option[Credentials]])(
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(NewPreferenceCreated))
      when(
        mockEmailConnector.sendDigitalOptInEmailVerification(any[String], any[String], any[Boolean])(any[HeaderCarrier])
      ).thenReturn(Future.failed {
        new RuntimeException()
      })
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Paper), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      await(
        optInService
          .optInToDigital(
            entityId,
            emailAddress,
            "generic",
            TermsAndConditionsRequest(
              None,
              Some(emailAddress),
              Some("returnTest"),
              Some("returnUrl"),
              language = Option(Language.English)
            ),
            Some(credentials),
            OptInBundle(Some(optInPage), Option(OptIn))
          )
      ) must be(NewPreferenceCreated)
    }

    "not send any message if email address supplied is empty string pre opt-in migration" in new TestCase {
      private val emailAddress = ""

      when(mockPreferencesRepository.findBy(entityId)).thenReturn(Future.successful(None))
      when(
        mockPreferencesRepository
          .createOrUpdateTermsAndConditions(any[Preferences], any[Option[Credentials]])(
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(NewPreferenceCreated))

      a[IllegalArgumentException] shouldBe thrownBy(
        await(
          optInService.optInToDigital(
            entityId,
            emailAddress,
            "generic",
            termsAndConditionsRequest,
            Some(credentials),
            OptInBundle()
          )
        )
      )

      verifyNoInteractions(mockEmailConnector)
    }

    "not send any message if email address supplied is empty string post opt-in migration" in new TestCase {
      private val emailAddress = ""

      when(mockPreferencesRepository.findBy(entityId)).thenReturn(Future.successful(None))
      when(
        mockPreferencesRepository
          .createOrUpdateTermsAndConditions(any[Preferences], any[Option[Credentials]])(
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(NewPreferenceCreated))

      a[IllegalArgumentException] shouldBe thrownBy(
        await(
          optInService.optInToDigital(
            entityId,
            emailAddress,
            "generic",
            termsAndConditionsRequest,
            Some(credentials),
            OptInBundle(Some(optInPage), None)
          )
        )
      )

      verifyNoInteractions(mockEmailConnector)
    }

    "Throw an exception when email address is changed while opting in pre opt-in migration" in new TestCase {
      private val exampleEmail = "test@test.com"
      private val originalPrefs = Some(
        Preferences(
          termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
          entityId = entityId,
          email = Some(EmailAddress("original@email.com", verifiedOn = Some(Dc.instantNow().minusDays(1))))
        )
      )

      when(mockPreferencesRepository.findBy(entityId)).thenReturn(Future.successful(originalPrefs))

      a[IllegalArgumentException] shouldBe thrownBy(
        await(
          optInService.optInToDigital(
            entityId,
            exampleEmail,
            "generic",
            termsAndConditionsRequest,
            Some(credentials),
            OptInBundle()
          )
        )
      )
    }

    "Throw an exception when email address is changed while opting in post opt-in migration" in new TestCase {
      private val exampleEmail = "test@test.com"
      private val originalPrefs = Some(
        Preferences(
          termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
          entityId = entityId,
          email = Some(EmailAddress("original@email.com", verifiedOn = Some(Dc.instantNow().minusDays(1))))
        )
      )

      when(mockPreferencesRepository.findBy(entityId)).thenReturn(Future.successful(originalPrefs))

      a[IllegalArgumentException] shouldBe thrownBy(
        await(
          optInService.optInToDigital(
            entityId,
            exampleEmail,
            "generic",
            termsAndConditionsRequest,
            Some(credentials),
            OptInBundle(Some(optInPage), None)
          )
        )
      )
    }

    "Updates event information to preference on opt-in" in new TestCase {
      private val exampleEmail = "test@test.com"
      private val acceptedTime = Dc.instantNow().minusYears(1)
      private val termsAndConditionsAcceptedForGenericAndOptInPage =
        TermsAndConditions(Accepted(acceptedTime, Some(OptIn), Some(OptInPage(Version(1, 2), 1, IPage))))

      private val originalPrefs = Some(
        Preferences(
          termsAndConditions = termsAndConditionsAcceptedForGenericAndOptInPage,
          entityId = entityId,
          email = Some(EmailAddress(exampleEmail, verifiedOn = Some(Dc.instantNow().minusDays(1))))
        )
      )

      when(mockPreferencesRepository.findBy(entityId)).thenReturn(Future.successful(originalPrefs))
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
        .thenReturn(Future.successful {})
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Digital), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      await(
        optInService
          .optInToDigital(
            entityId,
            exampleEmail,
            "generic",
            TermsAndConditionsRequest(
              Some(TermsAndConditionsRequest.UserAcceptance(accepted = true)),
              Some(emailAddress),
              Some(""),
              Some(""),
              Some(Language.English)
            ),
            credentials = Some(credentials),
            OptInBundle(Some(optInPage), Some(OptEventType.OptIn))
          )
      ) must be(PreferenceUpdated)
    }

    "Updates event information to preference on re-opt-in" in new TestCase {
      private val exampleEmail = "test@test.com"
      private val acceptedTime = Dc.instantNow().minusYears(1)
      private val termsAndConditionsAcceptedForGenericAndOptInPage =
        TermsAndConditions(Accepted(acceptedTime, Some(OptIn), Some(OptInPage(Version(1, 2), 1, IPage))))

      private val originalPrefs = Some(
        Preferences(
          termsAndConditions = termsAndConditionsAcceptedForGenericAndOptInPage,
          entityId = entityId,
          email = Some(EmailAddress(exampleEmail, verifiedOn = Some(Dc.instantNow().minusDays(1))))
        )
      )

      when(mockPreferencesRepository.findBy(entityId)).thenReturn(Future.successful(originalPrefs))
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
        .thenReturn(Future.successful {})
      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Digital), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      await(
        optInService
          .optInToDigital(
            entityId,
            exampleEmail,
            "generic",
            TermsAndConditionsRequest(
              Some(TermsAndConditionsRequest.UserAcceptance(accepted = true)),
              Some(emailAddress),
              Some(""),
              Some(""),
              Some(Language.English)
            ),
            credentials = Some(credentials),
            OptInBundle(Some(reOptInPage), Some(OptEventType.ReOptIn))
          )
      ) must be(PreferenceUpdated)

    }

  }

  trait TestCase {

    import scala.concurrent.ExecutionContext.Implicits.global

    implicit val hc: HeaderCarrier = HeaderCarrier()

    val entityId: EntityId = GenerateRandom.entityId()
    val optInPage: OptInPage = OptInPage(Version(1, 2), 1, PageType.IPage)
    val reOptInPage: OptInPage = OptInPage(Version(1, 2), 1, PageType.ReOptInPage)
    val termsAndConditionsRefusedForGenericOnly: TermsAndConditions =
      TermsAndConditions(Refused(Dc.instantNow()))
    val termsAndConditionsAcceptedForGenericOnly: TermsAndConditions =
      TermsAndConditions(Accepted(Dc.instantNow(), None))
    val mockPreferencesRepository: PreferencesRepository = mock[PreferencesRepository]
    val dummyLink = "this/is/a/fake/link"
    val mockVerificationLink: EmailVerificationLink => String = _ => dummyLink
    val mockEmailConnector: EmailConnector = mock[EmailConnector]
    val mockAuditConnector: AuditConnector = mock[AuditConnector]
    val mockEtmpService: ETMPService = mock[ETMPService]
    val mockPCNService: PreferencesChangedNotifierService = mock[PreferencesChangedNotifierService]
    val mockChangeEmailService: ChangeEmailService = mock[ChangeEmailService]
    val mockAuditable: Auditable = new Auditable {
      override def appName: String = "test"
      override def audit: Audit = new Audit("test", mockAuditConnector)
    }
    lazy val optInService: OptInService = new OptInService(
      mockPreferencesRepository,
      () => Instant.now,
      mockVerificationLink,
      mockEmailConnector,
      mockAuditable,
      mockChangeEmailService,
      mockPCNService
    )
  }

}
