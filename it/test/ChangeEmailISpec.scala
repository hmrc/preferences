/*
 * Copyright 2020 HM Revenue & Customs
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

import conf.PreferencesTestRoutes._
import conf.{ CleanMongoCollection, ISpec }
import play.api.http.Status
import play.api.http.Status._
import play.api.libs.json.Json._
import uk.gov.hmrc.paperless.controllers.model.PreferenceResponse
import uk.gov.hmrc.preferences.test.EntityResolverSupport
import utils.GenerateRandom

class ChangeEmailISpec extends ISpec with EntityResolverSupport {
  "changing email address" should {

    "return 400 with invalid payload" in new ISpecTestCase {
      private val invalidPreferences = toJson(Map[String, String]())
      preferencesTestRoutes
        .put(`/preferences/:entityId/pending-email`(GenerateRandom.entityId()), invalidPreferences)
        .status must be(Status.BAD_REQUEST)
    }

    "clear bounce status for pending email when email is changed" in new ISpecTestCase {
      private val entityId = GenerateRandom.entityId()
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))
      private val (oldEmail, newEmail) = (GenerateRandom.email(), GenerateRandom.email())

      private val pb = preferencesBuilder
        .acceptGenericTermsAndVerifyEmail(entityId, oldEmail, Some(authHelper.authHeader(nino, ggAuthPort)))
        .thenBounceEmail()

      private val printPreferences =
        preferencesTestRoutes.get(`/preferences/:entityId`(entityId)).json.as[PreferenceResponse]
      printPreferences.email.get.hasBounces mustBe true

      pb.thenChangeEmailAddress(newEmail)

      private val printPreferencesNew =
        preferencesTestRoutes.get(`/preferences/:entityId`(entityId)).json.as[PreferenceResponse]
      printPreferencesNew.email.get.isVerified mustBe false
    }

    "clear bounce status for pending email when new verification code is requested and verified" in new ISpecTestCase {
      private val entityId = GenerateRandom.entityId()
      private val email = GenerateRandom.email()
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))
      private val verification = preferencesBuilder
        .acceptGenericTermsPendingVerification(entityId, email, Some(authHelper.authHeader(nino, ggAuthPort)))

      verification.thenBounceEmail()

      private val emailPreference =
        preferencesTestRoutes.get(`/preferences/:entityId`(entityId)).json.as[PreferenceResponse].email.get
      emailPreference.hasBounces mustBe true

      private val prefsWithNewVerificationLink = preferencesBuilder
        .withEntityIdAndEmail(entityId, email)
        .thenRequestNewVerificationLink()

      private val emailPreference1 =
        preferencesTestRoutes.get(`/preferences/:entityId`(entityId)).json.as[PreferenceResponse].email.get
      emailPreference1.isVerified mustBe false

      prefsWithNewVerificationLink.thenVerifyEmail()

      preferencesTestRoutes
        .get(`/preferences/:entityId`(entityId))
        .json
        .as[PreferenceResponse]
        .email
        .get
        .isVerified mustBe true
    }

    "create and send additional notification email with a different verification link when the user changes their verified email address to the same address" in new ISpecTestCase {
      private val emailAddress = GenerateRandom.email()
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))
      preferencesBuilder
        .acceptGenericTermsAndVerifyEmail(
          entityId,
          emailAddress,
          Some(authHelper.authHeader(nino, ggAuthPort))
        )
        .thenChangeEmailAddress(emailAddress)

      eventually {
        val verificationLinks = testEmailService.findEmailsFor(emailAddress).verificationLinks()
        verificationLinks.size must be(2)
        verificationLinks.head must not be verificationLinks(1)
      }
    }

    "create and send additional notification emails when the user changes their verified email address to a new address" in new ISpecTestCase {
      private val originalEmailAddress = GenerateRandom.email()
      private val newEmail = GenerateRandom.email()
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))
      preferencesBuilder
        .acceptGenericTermsAndVerifyEmail(
          entityId,
          originalEmailAddress,
          Some(authHelper.authHeader(nino, ggAuthPort))
        )
        .thenChangeEmailAddress(newEmail)

      eventually {
        val emailsForVerifiedEmailAddress = testEmailService.findEmailsFor(originalEmailAddress)
        val verifiedLink = emailsForVerifiedEmailAddress.verificationLinks().head
        val nonVerifiedLink = testEmailService.findEmailsFor(newEmail).verificationLinks().head
        verifiedLink must not be nonVerifiedLink
        emailsForVerifiedEmailAddress.withSubjectContaining(Seq("change", "address")).size must be(1)
      }
    }

    "create and send one change address email to verified email and a new verification link to the new email when user changes their email address and then asks for new verification link" in new ISpecTestCase {
      private val entityId = GenerateRandom.entityId()
      private val originalEmailAddress = GenerateRandom.email()
      private val newEmail = GenerateRandom.email()
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))
      preferencesBuilder
        .acceptGenericTermsAndVerifyEmail(entityId, originalEmailAddress, Some(authHelper.authHeader(nino, ggAuthPort)))
        .thenChangeEmailAddress(newEmail)

      preferencesBuilder.withEntityIdAndEmail(entityId, newEmail).thenRequestNewVerificationLink()

      eventually {
        val verificationLinksForNew = testEmailService.findEmailsFor(newEmail).verificationLinks()
        verificationLinksForNew must have size 2
        verificationLinksForNew.head must be(verificationLinksForNew(1))

        testEmailService
          .findEmailsFor(originalEmailAddress)
          .withSubjectContaining(Seq("change", "address")) must have size 1
      }
    }

    "create and send two change address emails to verified email and a new verification link to the newest email when user changes their email address twice" in new ISpecTestCase {
      private val entityId = GenerateRandom.entityId()
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))
      private val originalEmailAddress = GenerateRandom.email()
      private val newestEmail = GenerateRandom.email()
      private val newEmail = GenerateRandom.email()

      preferencesBuilder
        .acceptGenericTermsAndVerifyEmail(entityId, originalEmailAddress, Some(authHelper.authHeader(nino, ggAuthPort)))
        .thenChangeEmailAddress(newEmail)
        .thenChangeEmailAddress(newestEmail)

      eventually {
        val emailsForNewEmailAddress = testEmailService.findEmailsFor(newEmail)
        val nonVerifiedLinkForNew = emailsForNewEmailAddress.verificationLinks().head
        val nonVerifiedLinkForNewest = testEmailService.findEmailsFor(newestEmail).verificationLinks().head

        nonVerifiedLinkForNew must not be nonVerifiedLinkForNewest

        testEmailService
          .findEmailsFor(originalEmailAddress)
          .withSubjectContaining(Seq("change", "address"))
          .size must be(2)
        emailsForNewEmailAddress.withSubjectContaining(Seq("change", "address")) must be(empty)
      }
    }

    "not affect current paperless activation status" in new ISpecTestCase {
      private val entityId = GenerateRandom.entityId()
      private val emailAddress = GenerateRandom.email()
      private val newEmail1 = GenerateRandom.email()
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))

      preferencesTestRoutes.delete(`/preferences-admin/:entityId`(entityId))

      private val pb = preferencesBuilder.withEntityId(entityId)

      pb.thenAcceptGenericTermsAndConditions(emailAddress, CREATED, Some(authHelper.authHeader(nino, ggAuthPort)))
        .thenVerifyEmail()

      preferencesBuilder.withEntityId(pb.entityId).thenChangeEmailAddress(newEmail1).thenVerifyEmail()

      preferencesBuilder.withEntityId(pb.entityId).thenChangeEmailAddress(GenerateRandom.email()).thenVerifyEmail()
    }
  }

  override val cleanMongoCollection: CleanMongoCollection = app.injector.instanceOf[CleanMongoCollection]
}
