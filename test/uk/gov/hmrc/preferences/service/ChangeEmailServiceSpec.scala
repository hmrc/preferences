/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.service

import cats.data.EitherT
import org.mockito.ArgumentMatchers.{ any, same }
import org.mockito.Mockito.{ never, verify, when }
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatestplus.play.PlaySpec
import play.api.test.Helpers.*
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.preferences.Auditable
import uk.gov.hmrc.preferences.connector.{ EmailConnector, EntityResolverConnector }
import uk.gov.hmrc.preferences.exceptions.EntityResolverResponse
import uk.gov.hmrc.preferences.model.TermsAndConditions.{ Accepted, Refused }
import uk.gov.hmrc.preferences.model.{ EmailVerificationLink, * }
import uk.gov.hmrc.preferences.repository.PreferencesRepository
import uk.gov.hmrc.preferences.util.DateTimeExtensions.InstantExtensions
import uk.gov.hmrc.preferences.util.Dc
import utils.GenerateRandom

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ChangeEmailServiceSpec extends PlaySpec with MockitoSugar with ScalaFutures with IntegrationPatience {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val appName = "ChangeEmailServiceSpec"

  "ChangeEmailService" should {
    "fail if no preference for the given entityId" in new TestCase {
      when(mockRepository.findBy(any[EntityId])(any[HeaderCarrier])).thenReturn(Future.successful(None))
      intercept[NoPreferenceExists] {
        await(changeEmailService.setPending(GenerateRandom.entityId(), GenerateRandom.email()))
      }
    }

    "fail if no current email addresses on the preference" in new TestCase {
      when(mockRepository.findBy(any[EntityId])(any[HeaderCarrier])).thenReturn(
        Future.successful(
          Some(
            Preferences(
              entityId = GenerateRandom.entityId(),
              termsAndConditions = termsAndConditionsRefusedForGenericOnly,
              pendingEmail = None,
              email = None
            )
          )
        )
      )

      intercept[NoEmailExists] {
        await(changeEmailService.setPending(GenerateRandom.entityId(), GenerateRandom.email()))
      }
    }

    "force send a verification link email if the address matches an existing pending address" in new TestCase {
      private val emailAddress = "new@email.com"

      private val pendingEmailReturnedByStubbedReset = PendingEmailAddress(
        emailAddress,
        verificationLink = Some(EmailVerificationLink(linkSentTime = Dc.instantNow()))
      )

      private val myPreference = new Preferences(
        entityId = eid,
        termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
        pendingEmail = Some(PendingEmailAddress(emailAddress))
      ) {
        override def resetPending(
          emailAddress: String,
          timeSource: () => Instant,
          returnText: Option[String],
          returnUrl: Option[String],
          language: Option[Language]
        ): PendingEmailAddress = pendingEmailReturnedByStubbedReset
      }

      when(mockRepository.findBy(same(eid))(any[HeaderCarrier])).thenReturn(Future.successful(Some(myPreference)))
      when(
        mockRepository.setUnverifiedEmailAddress(same(eid), same(pendingEmailReturnedByStubbedReset), any[Seq[Event]])(
          any[HeaderCarrier]
        )
      )
        .thenReturn(Future.successful(()))
      when(
        mockEmailConnector.sendDigitalOptInEmailVerification(any[String], any[String], any[Boolean])(any[HeaderCarrier])
      )
        .thenReturn(Future.successful {})

      changeEmailService.setPending(eid, emailAddress).futureValue must be(())

      verify(mockEmailConnector)
        .sendDigitalOptInEmailVerification(same(emailAddress), same("verification link"), same(true))(
          any[HeaderCarrier]
        )
    }

    "set the unverified email address in the repo and send emails to existing verified and new pending addresses" in new TestCase {
      private val existingVerifiedEmail =
        EmailAddress(GenerateRandom.email(), verifiedOn = Some(Dc.instantNow().minusDays(1)))
      private val changeTo = GenerateRandom.email()
      private val pendingEmailReturnedByStubbedReset =
        PendingEmailAddress(changeTo, verificationLink = Some(EmailVerificationLink(linkSentTime = Dc.instantNow())))

      private val myPreference = new Preferences(
        entityId = eid,
        termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
        email = Some(existingVerifiedEmail)
      ) {
        override def resetPending(
          emailAddress: String,
          timeSource: () => Instant,
          returnText: Option[String],
          returnUrl: Option[String],
          language: Option[Language]
        ): PendingEmailAddress = pendingEmailReturnedByStubbedReset
      }

      when(mockEmailConnector.sendChangedEmailAddressVerification(any[String], any[String])(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))
      when(mockEmailConnector.sendEmailChangedNotification(any[String])(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))
      when(mockRepository.findBy(eid)).thenReturn(Future.successful(Some(myPreference)))
      when(
        mockRepository.setUnverifiedEmailAddress(same(eid), same(pendingEmailReturnedByStubbedReset), any[Seq[Event]])(
          any[HeaderCarrier]
        )
      )
        .thenReturn(Future.successful(()))

      changeEmailService.setPending(eid, changeTo).futureValue mustBe unitVal

      verify(mockRepository).setUnverifiedEmailAddress(
        same(eid),
        same(pendingEmailReturnedByStubbedReset),
        any[Seq[Event]]
      )(any[HeaderCarrier])
      verify(mockEmailConnector).sendChangedEmailAddressVerification(same(changeTo), same("verification link"))(
        any[HeaderCarrier]
      )
      verify(mockEmailConnector).sendEmailChangedNotification(same(existingVerifiedEmail.email))(any[HeaderCarrier])
      verify(mockAuditable).sendDataEvent(
        transactionName = "Email Verification Link Sent",
        detail = Map(
          "entityId"         -> eid.value,
          "emailAddress"     -> changeTo,
          "verificationLink" -> "verification link",
          "verificationType" -> "emailAddressChanged"
        )
      )
    }

    "only send change of email to existing address if it's not a resend of an existing link" in new TestCase {
      private val pendingEmail = PendingEmailAddress(GenerateRandom.email())
      private val verifiedEmail = EmailAddress(GenerateRandom.email())
      private val changeTo = pendingEmail.email

      private val pendingEmailReturnedByStubbedReset =
        PendingEmailAddress(changeTo, verificationLink = Some(EmailVerificationLink(linkSentTime = Dc.instantNow())))

      private val myPreference = new Preferences(
        entityId = eid,
        termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
        pendingEmail = Some(pendingEmail),
        email = Some(verifiedEmail)
      ) {
        override def resetPending(
          emailAddress: String,
          timeSource: () => Instant,
          returnText: Option[String],
          returnUrl: Option[String],
          language: Option[Language]
        ): PendingEmailAddress = pendingEmailReturnedByStubbedReset
      }

      when(
        mockEmailConnector.sendDigitalOptInEmailVerification(any[String], any[String], any[Boolean])(any[HeaderCarrier])
      )
        .thenReturn(Future.successful(()))
      when(mockRepository.findBy(eid)).thenReturn(Future.successful(Some(myPreference)))
      when(
        mockRepository.setUnverifiedEmailAddress(same(eid), same(pendingEmailReturnedByStubbedReset), any[Seq[Event]])(
          any[HeaderCarrier]
        )
      )
        .thenReturn(Future.successful(()))

      changeEmailService.setPending(eid, changeTo).futureValue mustBe unitVal

      verify(mockRepository).setUnverifiedEmailAddress(
        same(eid),
        same(pendingEmailReturnedByStubbedReset),
        any[Seq[Event]]
      )(any[HeaderCarrier])
      verify(mockEmailConnector)
        .sendDigitalOptInEmailVerification(same(changeTo), same("verification link"), same(true))(any[HeaderCarrier])
      verify(mockEmailConnector, never()).sendEmailChangedNotification(any[String])(any[HeaderCarrier])
    }

    "change the unverified email address in the repo and only send opt-in email to new pending address" in new TestCase {
      private val existingPendingEmail = PendingEmailAddress(GenerateRandom.email())
      private val changeTo = GenerateRandom.email()

      private val pendingEmailReturnedByStubbedReset =
        PendingEmailAddress(changeTo, verificationLink = Some(EmailVerificationLink(linkSentTime = Dc.instantNow())))

      private val myPreference = new Preferences(
        entityId = eid,
        termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
        pendingEmail = Some(existingPendingEmail)
      ) {
        override def resetPending(
          emailAddress: String,
          timeSource: () => Instant,
          returnText: Option[String],
          returnUrl: Option[String],
          language: Option[Language]
        ): PendingEmailAddress = pendingEmailReturnedByStubbedReset
      }

      when(
        mockEmailConnector.sendDigitalOptInEmailVerification(any[String], any[String], any[Boolean])(any[HeaderCarrier])
      )
        .thenReturn(Future.successful(()))
      when(mockRepository.findBy(eid)).thenReturn(Future.successful(Some(myPreference)))
      when(
        mockRepository.setUnverifiedEmailAddress(same(eid), same(pendingEmailReturnedByStubbedReset), any[Seq[Event]])(
          any[HeaderCarrier]
        )
      )
        .thenReturn(Future.successful(()))

      changeEmailService.setPending(eid, changeTo).futureValue mustBe unitVal

      verify(mockRepository).setUnverifiedEmailAddress(
        same(eid),
        same(pendingEmailReturnedByStubbedReset),
        any[Seq[Event]]
      )(any[HeaderCarrier])
      verify(mockEmailConnector)
        .sendDigitalOptInEmailVerification(same(changeTo), same("verification link"), same(true))(any[HeaderCarrier])
    }

    "recover if the sending of the emails failed" in new TestCase {
      private val existingPendingEmail = PendingEmailAddress(GenerateRandom.email())
      private val changeTo = GenerateRandom.email()

      private val pendingEmailReturnedByStubbedReset =
        PendingEmailAddress(changeTo, verificationLink = Some(EmailVerificationLink(linkSentTime = Dc.instantNow())))

      private val myPreference = new Preferences(
        entityId = eid,
        termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
        pendingEmail = Some(existingPendingEmail)
      ) {
        override def resetPending(
          emailAddress: String,
          timeSource: () => Instant,
          returnText: Option[String],
          returnUrl: Option[String],
          language: Option[Language]
        ): PendingEmailAddress = pendingEmailReturnedByStubbedReset
      }

      when(
        mockEmailConnector.sendDigitalOptInEmailVerification(any[String], any[String], any[Boolean])(any[HeaderCarrier])
      )
        .thenReturn(Future.failed(new RuntimeException("aaarrrggghhh!")))
      when(mockRepository.findBy(eid)).thenReturn(Future.successful(Some(myPreference)))
      when(
        mockRepository.setUnverifiedEmailAddress(same(eid), same(pendingEmailReturnedByStubbedReset), any[Seq[Event]])(
          any[HeaderCarrier]
        )
      )
        .thenReturn(Future.successful(()))

      changeEmailService.setPending(eid, changeTo).futureValue mustBe unitVal

      verify(mockEmailConnector)
        .sendDigitalOptInEmailVerification(same(changeTo), same("verification link"), same(true))(any[HeaderCarrier])
    }
  }

  "setPendingEmail" should {
    "fail if no preference for the given entityId" in new TestCase {
      val entityId = GenerateRandom.entityId()
      when(mockEntityResolverConnector.getEntityIdByAuth())
        .thenReturn(EitherT.rightT[Future, EntityResolverResponse](entityId))
      when(mockRepository.findBy(any[EntityId])(any[HeaderCarrier])).thenReturn(Future.successful(None))
      changeEmailService.setPendingEmail(GenerateRandom.email()).value.futureValue mustBe Left(
        NoPreferenceExists(s"no preferences == no change of email for entity id $entityId")
      )
    }

    "NoEmailExists error when no current email addresses on the preference" in new TestCase {
      when(mockEntityResolverConnector.getEntityIdByAuth())
        .thenReturn(EitherT.rightT[Future, EntityResolverResponse](GenerateRandom.entityId()))
      when(mockRepository.findBy(any[EntityId])(any[HeaderCarrier])).thenReturn(
        Future.successful(
          Some(
            Preferences(
              entityId = GenerateRandom.entityId(),
              termsAndConditions = termsAndConditionsRefusedForGenericOnly,
              pendingEmail = None,
              email = None
            )
          )
        )
      )
      changeEmailService.setPendingEmail(GenerateRandom.email()).value.futureValue mustBe Left(
        NoEmailExists("changing email address when preference has no existing verified or pending email")
      )
    }

    "force send a verification link email if the address matches an existing pending address" in new TestCase {

      private val emailAddress = "new@email.com"

      private val pendingEmailReturnedByStubbedReset = PendingEmailAddress(
        emailAddress,
        verificationLink = Some(EmailVerificationLink(linkSentTime = Dc.instantNow()))
      )

      private val myPreference = new Preferences(
        entityId = eid,
        termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
        pendingEmail = Some(PendingEmailAddress(emailAddress))
      ) {
        override def resetPending(
          emailAddress: String,
          timeSource: () => Instant,
          returnText: Option[String],
          returnUrl: Option[String],
          language: Option[Language]
        ): PendingEmailAddress = pendingEmailReturnedByStubbedReset
      }

      when(mockEntityResolverConnector.getEntityIdByAuth())
        .thenReturn(EitherT.rightT[Future, EntityResolverResponse](eid))
      when(mockRepository.findBy(same(eid))(any[HeaderCarrier])).thenReturn(Future.successful(Some(myPreference)))
      when(
        mockRepository.setUnverifiedEmailAddress(same(eid), same(pendingEmailReturnedByStubbedReset), any[Seq[Event]])(
          any[HeaderCarrier]
        )
      )
        .thenReturn(Future.successful(()))
      when(
        mockEmailConnector.sendDigitalOptInEmailVerification(any[String], any[String], any[Boolean])(any[HeaderCarrier])
      )
        .thenReturn(Future.successful {})

      changeEmailService.setPendingEmail(emailAddress).value.futureValue mustBe (Right(()))

      verify(mockEmailConnector)
        .sendDigitalOptInEmailVerification(same(emailAddress), same("verification link"), same(true))(
          any[HeaderCarrier]
        )
    }

    "set the unverified email address in the repo and send emails to existing verified and new pending addresses" in new TestCase {
      private val existingVerifiedEmail =
        EmailAddress(GenerateRandom.email(), verifiedOn = Some(Dc.instantNow().minusDays(1)))
      private val changeTo = GenerateRandom.email()
      private val pendingEmailReturnedByStubbedReset =
        PendingEmailAddress(changeTo, verificationLink = Some(EmailVerificationLink(linkSentTime = Dc.instantNow())))

      private val myPreference = new Preferences(
        entityId = eid,
        termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
        email = Some(existingVerifiedEmail)
      ) {
        override def resetPending(
          emailAddress: String,
          timeSource: () => Instant,
          returnText: Option[String],
          returnUrl: Option[String],
          language: Option[Language]
        ): PendingEmailAddress = pendingEmailReturnedByStubbedReset
      }
      when(mockEntityResolverConnector.getEntityIdByAuth())
        .thenReturn(EitherT.rightT[Future, EntityResolverResponse](eid))
      when(mockEmailConnector.sendChangedEmailAddressVerification(any[String], any[String])(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))
      when(mockEmailConnector.sendEmailChangedNotification(any[String])(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))
      when(mockRepository.findBy(eid)).thenReturn(Future.successful(Some(myPreference)))
      when(
        mockRepository.setUnverifiedEmailAddress(same(eid), same(pendingEmailReturnedByStubbedReset), any[Seq[Event]])(
          any[HeaderCarrier]
        )
      )
        .thenReturn(Future.successful(()))

      changeEmailService.setPendingEmail(changeTo).value.futureValue mustBe Right(unitVal)

      verify(mockRepository).setUnverifiedEmailAddress(
        same(eid),
        same(pendingEmailReturnedByStubbedReset),
        any[Seq[Event]]
      )(any[HeaderCarrier])
      verify(mockEmailConnector).sendChangedEmailAddressVerification(same(changeTo), same("verification link"))(
        any[HeaderCarrier]
      )
      verify(mockEmailConnector).sendEmailChangedNotification(same(existingVerifiedEmail.email))(any[HeaderCarrier])
      verify(mockAuditable).sendDataEvent(
        transactionName = "Email Verification Link Sent",
        detail = Map(
          "entityId"         -> eid.value,
          "emailAddress"     -> changeTo,
          "verificationLink" -> "verification link",
          "verificationType" -> "emailAddressChanged"
        )
      )
    }

    "only send change of email to existing address if it's not a resend of an existing link" in new TestCase {
      private val pendingEmail = PendingEmailAddress(GenerateRandom.email())
      private val verifiedEmail = EmailAddress(GenerateRandom.email())
      private val changeTo = pendingEmail.email

      private val pendingEmailReturnedByStubbedReset =
        PendingEmailAddress(changeTo, verificationLink = Some(EmailVerificationLink(linkSentTime = Dc.instantNow())))

      private val myPreference = new Preferences(
        entityId = eid,
        termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
        pendingEmail = Some(pendingEmail),
        email = Some(verifiedEmail)
      ) {
        override def resetPending(
          emailAddress: String,
          timeSource: () => Instant,
          returnText: Option[String],
          returnUrl: Option[String],
          language: Option[Language]
        ): PendingEmailAddress = pendingEmailReturnedByStubbedReset
      }
      when(mockEntityResolverConnector.getEntityIdByAuth())
        .thenReturn(EitherT.rightT[Future, EntityResolverResponse](eid))
      when(
        mockEmailConnector.sendDigitalOptInEmailVerification(any[String], any[String], any[Boolean])(any[HeaderCarrier])
      )
        .thenReturn(Future.successful(()))
      when(mockRepository.findBy(eid)).thenReturn(Future.successful(Some(myPreference)))
      when(
        mockRepository.setUnverifiedEmailAddress(same(eid), same(pendingEmailReturnedByStubbedReset), any[Seq[Event]])(
          any[HeaderCarrier]
        )
      )
        .thenReturn(Future.successful(()))

      changeEmailService.setPendingEmail(changeTo).value.futureValue mustBe Right(unitVal)

      verify(mockRepository).setUnverifiedEmailAddress(
        same(eid),
        same(pendingEmailReturnedByStubbedReset),
        any[Seq[Event]]
      )(any[HeaderCarrier])
      verify(mockEmailConnector)
        .sendDigitalOptInEmailVerification(same(changeTo), same("verification link"), same(true))(any[HeaderCarrier])
      verify(mockEmailConnector, never()).sendEmailChangedNotification(any[String])(any[HeaderCarrier])
    }

    "change the unverified email address in the repo and only send opt-in email to new pending address" in new TestCase {
      private val existingPendingEmail = PendingEmailAddress(GenerateRandom.email())
      private val changeTo = GenerateRandom.email()

      private val pendingEmailReturnedByStubbedReset =
        PendingEmailAddress(changeTo, verificationLink = Some(EmailVerificationLink(linkSentTime = Dc.instantNow())))

      private val myPreference = new Preferences(
        entityId = eid,
        termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
        pendingEmail = Some(existingPendingEmail)
      ) {
        override def resetPending(
          emailAddress: String,
          timeSource: () => Instant,
          returnText: Option[String],
          returnUrl: Option[String],
          language: Option[Language]
        ): PendingEmailAddress = pendingEmailReturnedByStubbedReset
      }
      when(mockEntityResolverConnector.getEntityIdByAuth())
        .thenReturn(EitherT.rightT[Future, EntityResolverResponse](eid))
      when(
        mockEmailConnector.sendDigitalOptInEmailVerification(any[String], any[String], any[Boolean])(any[HeaderCarrier])
      )
        .thenReturn(Future.successful(()))
      when(mockRepository.findBy(eid)).thenReturn(Future.successful(Some(myPreference)))
      when(
        mockRepository.setUnverifiedEmailAddress(same(eid), same(pendingEmailReturnedByStubbedReset), any[Seq[Event]])(
          any[HeaderCarrier]
        )
      )
        .thenReturn(Future.successful(()))

      changeEmailService.setPendingEmail(changeTo).value.futureValue mustBe Right(unitVal)

      verify(mockRepository).setUnverifiedEmailAddress(
        same(eid),
        same(pendingEmailReturnedByStubbedReset),
        any[Seq[Event]]
      )(any[HeaderCarrier])
      verify(mockEmailConnector)
        .sendDigitalOptInEmailVerification(same(changeTo), same("verification link"), same(true))(any[HeaderCarrier])
    }

    "recover if the sending of the emails failed" in new TestCase {
      private val existingPendingEmail = PendingEmailAddress(GenerateRandom.email())
      private val changeTo = GenerateRandom.email()

      private val pendingEmailReturnedByStubbedReset =
        PendingEmailAddress(changeTo, verificationLink = Some(EmailVerificationLink(linkSentTime = Dc.instantNow())))

      private val myPreference = new Preferences(
        entityId = eid,
        termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
        pendingEmail = Some(existingPendingEmail)
      ) {
        override def resetPending(
          emailAddress: String,
          timeSource: () => Instant,
          returnText: Option[String],
          returnUrl: Option[String],
          language: Option[Language]
        ): PendingEmailAddress = pendingEmailReturnedByStubbedReset
      }
      when(mockEntityResolverConnector.getEntityIdByAuth())
        .thenReturn(EitherT.rightT[Future, EntityResolverResponse](eid))
      when(
        mockEmailConnector.sendDigitalOptInEmailVerification(any[String], any[String], any[Boolean])(any[HeaderCarrier])
      )
        .thenReturn(Future.failed(new RuntimeException("aaarrrggghhh!")))
      when(mockRepository.findBy(eid)).thenReturn(Future.successful(Some(myPreference)))
      when(
        mockRepository.setUnverifiedEmailAddress(same(eid), same(pendingEmailReturnedByStubbedReset), any[Seq[Event]])(
          any[HeaderCarrier]
        )
      )
        .thenReturn(Future.successful(()))

      changeEmailService.setPendingEmail(changeTo).value.futureValue mustBe Right(unitVal)

      verify(mockEmailConnector)
        .sendDigitalOptInEmailVerification(same(changeTo), same("verification link"), same(true))(any[HeaderCarrier])
    }

  }

  trait TestCase {

    import scala.concurrent.ExecutionContext.Implicits.global

    /** needed for test compilation */
    val unitVal: Unit = ()

    val eid: EntityId = GenerateRandom.entityId()

    val termsAndConditionsAcceptedForGenericOnly: TermsAndConditions = TermsAndConditions(Accepted(Dc.instantNow()))
    val termsAndConditionsRefusedForGenericOnly: TermsAndConditions = TermsAndConditions(Refused(Dc.instantNow()))

    val mockRepository: PreferencesRepository = mock[PreferencesRepository]
    val mockEmailConnector: EmailConnector = mock[EmailConnector]
    val mockExternaliseLink: EmailVerificationLink => String = _ => "verification link"
    val mockAuditConnector: AuditConnector = mock[AuditConnector]
    val mockAuditable: Auditable = mock[Auditable]
    val mockEntityResolverConnector: EntityResolverConnector = mock[EntityResolverConnector]
    val changeEmailService =
      new ChangeEmailService(
        mockRepository,
        mockEmailConnector,
        mockExternaliseLink,
        mockAuditable,
        () => Dc.instantNow(),
        mockEntityResolverConnector
      )
  }

}
