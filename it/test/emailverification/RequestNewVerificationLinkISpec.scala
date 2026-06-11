/*
 * Copyright 2020 HM Revenue & Customs
 *
 */

package emailverification

import conf.{ ISpec, _ }
import org.scalatest.concurrent.IntegrationPatience
import play.api.http.Status._
import uk.gov.hmrc.preferences.test.EntityResolverSupport
import utils.GenerateRandom

class RequestNewVerificationLinkISpec extends ISpec with Tardis with IntegrationPatience with EntityResolverSupport {

  "requesting a new verification link" should {

    "return the same verification link if the email is the same (tardis is not used)" in new ISpecTestCase {
      private val entityId = GenerateRandom.entityId()
      private val emailAddress = GenerateRandom.email()
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))
      preferencesBuilder
        .acceptGenericTermsPendingVerification(entityId, emailAddress, Some(authHelper.authHeader(nino, ggAuthPort)))
        .thenRequestNewVerificationLink()

      eventually {
        val verificationLinks = testEmailService.findEmailsFor(emailAddress).verificationLinks()
        verificationLinks.size must be(2)
        verificationLinks.head must be(verificationLinks(1))
      }
    }

    "send a verification link email after user opts back in" in new ISpecTestCase {
      private val email = GenerateRandom.email()
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))
      preferencesBuilder
        .withEntityId(entityId)
        .thenAcceptGenericTermsAndConditions(email, CREATED, Some(authHelper.authHeader(nino, ggAuthPort)))
        .thenStopEmailRemindersFromManageAccount(Some(authHelper.authHeader(nino, ggAuthPort)))
        .thenAcceptGenericTermsAndConditions(email, OK, Some(authHelper.authHeader(nino, ggAuthPort)))

      eventually {
        testEmailService.findEmailsFor(email).verificationLinks().size must be(2) // one for each opt in
      }
    }

    "fail with CONFLICT when the verification link is not for the email awaiting verification" in new ISpecTestCase {
      private val entityId = GenerateRandom.entityId()
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))
      private val prefBuilderWithOriginalVerificationLink = preferencesBuilder
        .acceptGenericTermsPendingVerification(
          entityId,
          GenerateRandom.email(),
          Some(authHelper.authHeader(nino, ggAuthPort))
        )

      preferencesBuilder
        .withEntityId(entityId)
        .thenChangeEmailAddress(GenerateRandom.email())

      prefBuilderWithOriginalVerificationLink.thenVerifyEmail(shouldReturnStatus = CONFLICT)
    }
  }

  override def cleanMongoCollection: CleanMongoCollection = app.injector.instanceOf[CleanMongoCollection]
}
