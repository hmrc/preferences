/*
 * Copyright 2020 HM Revenue & Customs
 *
 */

package client

import conf.{ CleanMongoCollection, ISpec }
import play.api.http.Status._
import uk.gov.hmrc.preferences.test.EntityResolverSupport
import utils.GenerateRandom

class OptOutNotificationISpec extends ISpec with EntityResolverSupport {

  "User opting out" should {
    "send an email to the user with opt-out notification" in new ISpecTestCase {

      private val email = GenerateRandom.email()
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))

      preferencesBuilder
        .withEntityId(entityId)
        .thenAcceptGenericTermsAndConditions(email, CREATED, Some(authHelper.authHeader(nino, ggAuthPort)))
        .thenVerifyEmail()
        .thenStopEmailRemindersFromManageAccount(Some(authHelper.authHeader(nino, ggAuthPort)))

      eventually {
        testEmailService.findEmailsFor(email).optOutNotifications() must have(size(1))
      }
    }
  }

  override def cleanMongoCollection: CleanMongoCollection = app.injector.instanceOf[CleanMongoCollection]
}
