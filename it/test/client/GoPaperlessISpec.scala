/*
 * Copyright 2020 HM Revenue & Customs
 *
 */

package client

import conf.*
import conf.PreferencesTestRoutes.*
import play.api.Application
import play.api.http.Status.*
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.domain.{ Nino, SaUtr }
import uk.gov.hmrc.preferences.model.EntityId
import uk.gov.hmrc.preferences.test.EntityResolverSupport
import utils.{ FakeApplicationCrypto, GenerateRandom }

class GoPaperlessISpec extends ISpec with EntityResolverSupport {

  "calling optin when new user" should {
    "create preferences for new user with T&Cs accepted when user accepts" in new TestCase {
      withEntity(entityId.get.toString, Option(nino.toString()), Option(utr.value))
      preferencesBuilder
        .withEntityId(entityId.get)
        .thenAcceptGenericTermsAndConditions(
          email,
          shouldReturnStatus = CREATED,
          Some(authHelper.authHeader(nino, ggAuthPort))
        )
    }

    "create preferences for new user with T&Cs not accepted as user declined" in new TestCase {
      withEntity(entityId.get.toString, Option(nino.toString()), Option(utr.value))
      preferencesBuilder
        .withEntityId(entityId.get)
        .thenDeclineGenericTermsAndConditions(
          shouldReturnStatus = CREATED,
          Some(authHelper.authHeader(nino, ggAuthPort))
        )
        .thenAcceptGenericTermsAndConditions(
          email,
          shouldReturnStatus = OK,
          Some(authHelper.authHeader(nino, ggAuthPort))
        )
    }

    "return bad request with T&Cs accepted and no email address" in new TestCase {
      preferencesBuilder
        .withRandomEntityId()
        .thenInvalidAcceptGenericTermsAndConditions(
          shouldReturnStatus = BAD_REQUEST,
          Some(authHelper.authHeader(nino, ggAuthPort))
        )
    }
  }

  trait TestCase extends ISpecTestCase {
    val email: String = GenerateRandom.email()
    val utr: SaUtr = GenerateRandom.utr()
    val entityId: Option[EntityId] = Some(GenerateRandom.entityId())
    val nino: Nino = GenerateRandom.nino()
  }

  override def cleanMongoCollection: CleanMongoCollection = app.injector.instanceOf[CleanMongoCollection]
}

class GoPaperlessNoEmailISpec extends ISpec with EntityResolverSupport {

  "going paperless when email service is unavailable" should {

    "save the pending email regardless" in new ISpecTestCase {
      private val entityId = GenerateRandom.entityId()
      private val emailAddress = GenerateRandom.email()

      withEntity(entityId.toString, Option(nino.toString()), Option(utr.value))

      preferencesBuilder
        .withEntityId(entityId)
        .thenAcceptGenericTermsAndConditions(emailAddress, CREATED, Some(authHelper.authHeader(nino, ggAuthPort)))

      (preferencesTestRoutes.get(`/preferences/:entityId`(entityId)).json \ "email" \ "email")
        .as[String] mustBe emailAddress
    }

  }

  override def fakeApplication(): Application = new GuiceApplicationBuilder()
    .configure("mongodb.uri" -> mongoUri)
    .configure(additionalConfig)
    .overrides(bind[Decrypter].toInstance(FakeApplicationCrypto))
    .build()

  override val cleanMongoCollection: CleanMongoCollection = app.injector.instanceOf[CleanMongoCollection]

}
