/*
 * Copyright 2020 HM Revenue & Customs
 *
 */

package emailverification

import conf.{ CleanMongoCollection, ISpec, Tardis }
import play.api.http.Status._
import uk.gov.hmrc.preferences.test.EntityResolverSupport
import utils.GenerateRandom

class EmailVerificationTimeBasedISpec extends ISpec with Tardis with EntityResolverSupport {

  "verifying an email address" should {
    "fail when the verification link has expired (at least 30 days old)" in new ISpecTestCase {
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))
      atTime(daysAgo(31)) {
        preferencesBuilder
          .withEntityId(entityId)
          .thenAcceptGenericTermsAndConditions(
            GenerateRandom.email(),
            CREATED,
            Some(authHelper.authHeader(nino, ggAuthPort))
          )
      }

      preferencesBuilder.withEntityId(entityId).thenVerifyEmail(shouldReturnStatus = GONE)
    }

    "succeed when the verification link is within the valid period (less than 30 days old)" in new ISpecTestCase {
      val email = GenerateRandom.email()
      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))
      atTime(daysAgo(29)) {
        preferencesBuilder
          .withEntityId(entityId)
          .thenAcceptGenericTermsAndConditions(
            email,
            CREATED,
            Some(authHelper.authHeader(nino, ggAuthPort))
          )
      }

      preferencesBuilder.withEntityId(entityId).thenVerifyEmail(shouldReturnStatus = OK)
    }
  }

  override def cleanMongoCollection: CleanMongoCollection = app.injector.instanceOf[CleanMongoCollection]
}
