/*
 * Copyright 2020 HM Revenue & Customs
 *
 */

package emailverification

import conf.{ ISpec, _ }
import org.scalatest.time._
import org.scalatest.LoneElement
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.preferences.service.{ ProcessingResult, VerificationChaser }
import utils.GenerateRandom
import play.api.test.Helpers._
import uk.gov.hmrc.preferences.test.EntityResolverSupport

class EmailVerificationReminderISpec extends ISpec with Tardis with EntityResolverSupport with LoneElement {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(
    timeout = scaled(Span(600, Seconds)),
    interval = scaled(Span(200, Millis))
  )

  "verification reminder" should {
    val taxPlatformSaPrefsRootUri = "/sa/print-preferences/verification"
    "be sent to the email after the configured interval and have verification link" in new ISpecTestCase {
      val email: String = GenerateRandom.email()

      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))

      val verificationLink: String = atTime(twoDaysAgo) {
        preferencesBuilder
          .withEntityId(entityId)
          .thenAcceptGenericTermsAndConditions(email, CREATED, Some(authHelper.authHeader(nino, ggAuthPort)))
          .maybeVerificationLink
          .get
      }

      triggerVerificationRemindersProcessing().successfulCount must be > 0

      testEmailService.findEmailsFor(email).verificationReminders().loneElement.content.text must (
        include("Two days ago you told us you want to get online tax letters") and
          include(verificationLink) and include(taxPlatformSaPrefsRootUri)
      )
    }

    "be sent to the new email address when the user changes their pending email address" in new ISpecTestCase {
      val emailAfterVerifiedThenRemindedThenChangedEmail: String = GenerateRandom.email()

      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))

      val optedInAndVerified: preferencesBuilder.Builder = atTime(daysAgo(3)) {
        preferencesBuilder
          .withEntityId(entityId)
          .thenAcceptGenericTermsAndConditions(
            GenerateRandom.email(),
            CREATED,
            Some(authHelper.authHeader(nino, ggAuthPort))
          )
      }

      val optedInAndVerifiedThenChangedEmail: preferencesBuilder.Builder = atTime(twoDaysAgo) {
        optedInAndVerified.thenChangeEmailAddress(emailAfterVerifiedThenRemindedThenChangedEmail)
      }

      triggerVerificationRemindersProcessing().successfulCount must be > 0

      eventually {
        testEmailService
          .findEmailsFor(emailAfterVerifiedThenRemindedThenChangedEmail)
          .verificationReminders()
          .loneElement
          .content
          .text must {
          include(optedInAndVerifiedThenChangedEmail.maybeVerificationLink.get) and
            include(taxPlatformSaPrefsRootUri)
        }
      }
    }

    "not be sent when email address bounces" in new ISpecTestCase {

      private val bouncedEmail = GenerateRandom.email()
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))
      atTime(twoDaysAgo) {
        preferencesBuilder
          .withEntityId(entityId)
          .thenAcceptGenericTermsAndConditions(bouncedEmail, CREATED, Some(authHelper.authHeader(nino, ggAuthPort)))
          .thenBounceEmail()
      }

      val emails = testEmailService.findEmailsForMock(bouncedEmail)
      emails.verificationLinks().size must be(1)
      emails.verificationReminders().size must be(0)
    }

    "be sent again to the same email if resend is requested after the original reminder" in new ISpecTestCase {

      private val email = GenerateRandom.email()
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))
      private val optedInSomeTimeAgo = atTime(daysAgo(4)) {
        preferencesBuilder
          .withEntityId(entityId)
          .thenAcceptGenericTermsAndConditions(email, CREATED, Some(authHelper.authHeader(nino, ggAuthPort)))
      }

      triggerVerificationRemindersProcessing()
      testEmailService.findEmailsFor(email).verificationReminders().size must be(1)

      atTime(twoDaysAgo) {
        optedInSomeTimeAgo.thenRequestNewVerificationLink()
      }

      triggerVerificationRemindersProcessing()
      testEmailService.findEmailsFor(email).verificationReminders().size must be(2)
    }

    "be sent to the new email address when the user changes their unverified email address" in new ISpecTestCase {
      private val emailAfterOptIn = GenerateRandom.email()
      private val emailAfterOptedInThenRemindedThenChangedEmail = GenerateRandom.email()
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))
      private val optedInThenChangedEmail = atTime(daysAgo(3)) {
        preferencesBuilder
          .withEntityId(entityId)
          .thenAcceptGenericTermsAndConditions(emailAfterOptIn, CREATED, Some(authHelper.authHeader(nino, ggAuthPort)))
      }

      triggerVerificationRemindersProcessing()

      atTime(twoDaysAgo) {
        optedInThenChangedEmail.thenChangeEmailAddress(emailAfterOptedInThenRemindedThenChangedEmail)
      }

      eventually {
        testEmailService.findEmailsFor(emailAfterOptIn).verificationReminders() must have size 1
      }

      triggerVerificationRemindersProcessing()

      testEmailService
        .findEmailsFor(emailAfterOptedInThenRemindedThenChangedEmail)
        .verificationReminders() must have size 1
    }

  }

  def triggerVerificationRemindersProcessing(): ProcessingResult = {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    app.injector.instanceOf[VerificationChaser].chaseVerifications.futureValue
  }

  override def cleanMongoCollection: CleanMongoCollection = app.injector.instanceOf[CleanMongoCollection]

  override def additionalConfig: Map[String, _] = Map("play.http.router" -> "testOnlyDoNotUseInAppConf.Routes")
}
