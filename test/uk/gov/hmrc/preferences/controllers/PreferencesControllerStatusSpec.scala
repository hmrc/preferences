/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.controllers

import cats.data.EitherT
import cats.instances.future.*
import org.apache.pekko.stream.Materializer
import org.mockito.ArgumentMatchers.{ any, eq as eqTo }
import org.mockito.Mockito.when
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.bind
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import play.api.Application
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.{ AffinityGroup, AuthConnector, ConfidenceLevel }
import uk.gov.hmrc.auth.core.retrieve.{ Name, Retrieval, ~ }
import uk.gov.hmrc.crypto.{ Decrypter, Encrypter }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.paperless.controllers.model.PreferenceResponse
import uk.gov.hmrc.play.audit.model.Audit
import uk.gov.hmrc.preferences.PreferencesParams
import uk.gov.hmrc.preferences.connector.EntityResolverConnector
import uk.gov.hmrc.preferences.model.TermsAndConditions.*
import uk.gov.hmrc.preferences.model.*
import uk.gov.hmrc.preferences.repository.PreferencesRepository
import uk.gov.hmrc.preferences.service.ChangeEmailService
import uk.gov.hmrc.preferences.util.Dc
import utils.{ FakeApplicationCrypto, GenerateRandom, Resources }

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.annotation.unused
import scala.concurrent.{ ExecutionContext, Future }

class PreferencesControllerStatusSpec
    extends PlaySpec with MockitoSugar with ScalaFutures with IntegrationPatience with GuiceOneAppPerTest {
  val REOPTINMAJOR = 2
  val GRACEPERIOD = 10L
  override def fakeApplication(): Application = {
    val repoMock = mock[PreferencesRepository]
    val mockEmailService = mock[ChangeEmailService]
    val mockEntityResolver = mock[EntityResolverConnector]
    val mockAuthConnector: AuthConnector = mock[AuthConnector]
    val audit = mock[Audit]

    new GuiceApplicationBuilder()
      .configure("metrics.jvm" -> false)
      .configure("appName" -> "test app")
      .configure("reoptin.major" -> REOPTINMAJOR)
      .configure("activation.gracePeriodInMin" -> GRACEPERIOD)
      .overrides(bind[PreferencesRepository].toInstance(repoMock))
      .overrides(bind[ChangeEmailService].toInstance(mockEmailService))
      .overrides(bind[EntityResolverConnector].toInstance(mockEntityResolver))
      .overrides(bind[Audit].toInstance(audit))
      .overrides(bind[AuthConnector].toInstance(mockAuthConnector))
      .overrides(bind[Decrypter].toInstance(FakeApplicationCrypto))
      .build()
  }
  // TODO: test should test all response not just the status, as status needs to be correlated to other fields,
  // for this timestamp must be injected into preference and stubbed to Time zero in tests
  "findPreferences" should {

    "return 200 and NoEmail status for preferences without verified email" in new TestCase {
      withAuth(authFailure)
      withPreferences(email = None, accepted = true)
      val response: PreferenceResponse = readPreference()
      val expectedResponse: PreferenceResponse = Resources.readJson("status/NoEmail.json").as[PreferenceResponse]
      response.status mustBe expectedResponse.status
    }

    "return 200 and Paper/OptInRequired status for preferences with Refused t&c and updatedAt outside of grace period" in new TestCase {
      withAuth(authFailure)
      withPreferences(
        email = None,
        accepted = false,
        updatedAt = Dc.instantNow().minus(GRACEPERIOD + 2L, ChronoUnit.MINUTES)
      )
      val response: PreferenceResponse = readPreference()
      val expectedResponse: PreferenceResponse =
        Resources.readJson("status/PaperOptinRequired.json").as[PreferenceResponse]
      response.status mustBe expectedResponse.status
    }

    "return 200 and Paper/Info status for preferences with Refused t&c and updatedAt within grace period" in new TestCase {
      withAuth(authFailure)
      withPreferences(
        email = None,
        accepted = false,
        updatedAt = Dc.instantNow().minus(GRACEPERIOD - 2L, ChronoUnit.MINUTES)
      )
      val response: PreferenceResponse = readPreference()
      val expectedResponse: PreferenceResponse = Resources.readJson("status/PaperInfo.json").as[PreferenceResponse]
      response.status mustBe expectedResponse.status
    }

    "return 200 and EmailNotVerified status for preferences with Accepted t&c and email not verified" in new TestCase {
      withAuth(authFailure)
      val pendingEmail: Option[PendingEmailAddress] = Some(PendingEmailAddress(email = email))
      withPreferences(email = None, accepted = true, pendingEmail = pendingEmail)
      val response: PreferenceResponse = readPreference()
      val expectedResponse: PreferenceResponse =
        Resources.readJson("status/EmailNotVerified.json").as[PreferenceResponse]
      response.status mustBe expectedResponse.status
    }

    "return 200 and BouncedEmail status for preferences with Accepted t&c and email verified but bounced" in new TestCase {
      withAuth(authFailure)
      val bouncedEmail: Option[EmailAddress] =
        Some(emailAddress(lastBounce = Some(EmailBounce(Some(1), Dc.instantNow()))))
      withPreferences(email = bouncedEmail, accepted = true)
      val response: PreferenceResponse = readPreference()
      val expectedResponse: PreferenceResponse = Resources.readJson("status/BouncedEmail.json").as[PreferenceResponse]
      response.status mustBe expectedResponse.status
    }

    "return 200 and Alright status for preferences with Accepted t&c and email verified" in new TestCase {
      withAuth(authFailure)
      withPreferences(email = Some(emailAddress()), accepted = true)
      val response: PreferenceResponse = readPreference()
      val expectedResponse: PreferenceResponse = Resources.readJson("status/AlrightNoAuth.json").as[PreferenceResponse]
      response.status mustBe expectedResponse.status
    }
    "retun 200 and OldVersion if Accepted/Individual/200  major < reoptinmajor" in new TestCase {
      withPreferences(email = Some(emailAddress()), accepted = true, major = REOPTINMAJOR - 1)
      withAuth(individual200)
      val response: PreferenceResponse = readPreference()
      val expectedResponse: PreferenceResponse =
        Resources.readJson("status/OldVersionIndividual200.json").as[PreferenceResponse]
      response.status mustBe expectedResponse.status

    }

    "retun 200 and Alright if Accepted/Individual/200  major = reoptinmajor" in new TestCase {
      withPreferences(email = Some(emailAddress()), accepted = true)
      withAuth(individual200)
      val response: PreferenceResponse = readPreference()
      val expectedResponse: PreferenceResponse =
        Resources.readJson("status/AlrightIndividual200.json").as[PreferenceResponse]
      response.status mustBe expectedResponse.status

    }

    "retun 200 and Alright if Accepted/Organization/200  major < reoptinmajor" in new TestCase {
      withPreferences(email = Some(emailAddress()), accepted = true, major = REOPTINMAJOR - 1)
      withAuth(organization200)
      val response: PreferenceResponse = readPreference()
      val expectedResponse: PreferenceResponse =
        Resources.readJson("status/AlrightOrganizationOnOldVersion.json").as[PreferenceResponse]
      response.status mustBe expectedResponse.status

    }

    "retun 200 and Alright if Accepted/Individual/300  major < reoptinmajor" in new TestCase {
      withPreferences(email = Some(emailAddress()), accepted = true, major = REOPTINMAJOR - 1)
      withAuth(organization200)
      val response: PreferenceResponse = readPreference()
      val expectedResponse: PreferenceResponse =
        Resources.readJson("status/AlrightIndividual300OnOldVersion.json").as[PreferenceResponse]
      response.status mustBe expectedResponse.status
    }

  }

  "findPreferences with auth" should {

    "return 200 and NoEmail status for preferences without verified email" in new TestCase {
      withAuth(authFailure)
      withPreferences(email = None, accepted = true)
      val response: PreferenceResponse = readPreferenceAuth()
      val expectedResponse: PreferenceResponse = Resources.readJson("status/NoEmail.json").as[PreferenceResponse]
      response.status mustBe expectedResponse.status
    }

    "return 200 and Paper/OptInRequired status for preferences with Refused t&c and updatedAt outside of grace period" in new TestCase {
      withAuth(authFailure)
      withPreferences(
        email = None,
        accepted = false,
        updatedAt = Dc.instantNow().minus(GRACEPERIOD + 2L, ChronoUnit.MINUTES)
      )
      val response: PreferenceResponse = readPreferenceAuth()
      val expectedResponse: PreferenceResponse =
        Resources.readJson("status/PaperOptinRequired.json").as[PreferenceResponse]
      response.status mustBe expectedResponse.status
    }

    "return 200 and Paper/Info status for preferences with Refused t&c and updatedAt within grace period" in new TestCase {
      withAuth(authFailure)
      withPreferences(
        email = None,
        accepted = false,
        updatedAt = Dc.instantNow().minus(GRACEPERIOD - 2L, ChronoUnit.MINUTES)
      )
      val response: PreferenceResponse = readPreferenceAuth()
      val expectedResponse: PreferenceResponse = Resources.readJson("status/PaperInfo.json").as[PreferenceResponse]
      response.status mustBe expectedResponse.status
    }

    "return 200 and EmailNotVerified status for preferences with Accepted t&c and email not verified" in new TestCase {
      withAuth(authFailure)
      val pendingEmail: Option[PendingEmailAddress] = Some(PendingEmailAddress(email = email))
      withPreferences(email = None, accepted = true, pendingEmail = pendingEmail)
      val response: PreferenceResponse = readPreferenceAuth()
      val expectedResponse: PreferenceResponse =
        Resources.readJson("status/EmailNotVerified.json").as[PreferenceResponse]
      response.status mustBe expectedResponse.status
    }

    "return 200 and BouncedEmail status for preferences with Accepted t&c and email verified but bounced" in new TestCase {
      withAuth(authFailure)
      val bouncedEmail: Option[EmailAddress] =
        Some(emailAddress(lastBounce = Some(EmailBounce(Some(1), Dc.instantNow()))))
      withPreferences(email = bouncedEmail, accepted = true)
      val response: PreferenceResponse = readPreferenceAuth()
      val expectedResponse: PreferenceResponse = Resources.readJson("status/BouncedEmail.json").as[PreferenceResponse]
      response.status mustBe expectedResponse.status
    }

    "return 200 and Alright status for preferences with Accepted t&c and email verified" in new TestCase {
      withAuth(authFailure)
      withPreferences(email = Some(emailAddress()), accepted = true)
      val response: PreferenceResponse = readPreferenceAuth()
      val expectedResponse: PreferenceResponse = Resources.readJson("status/AlrightNoAuth.json").as[PreferenceResponse]
      response.status mustBe expectedResponse.status
    }
    "retun 200 and OldVersion if Accepted/Individual/200  major < reoptinmajor" in new TestCase {
      withPreferences(email = Some(emailAddress()), accepted = true, major = REOPTINMAJOR - 1)
      withAuth(individual200)
      val response: PreferenceResponse = readPreferenceAuth()
      val expectedResponse: PreferenceResponse =
        Resources.readJson("status/OldVersionIndividual200.json").as[PreferenceResponse]
      response.status mustBe expectedResponse.status

    }

    "retun 200 and Alright if Accepted/Individual/200  major = reoptinmajor" in new TestCase {
      withPreferences(email = Some(emailAddress()), accepted = true)
      withAuth(individual200)
      val response: PreferenceResponse = readPreferenceAuth()
      val expectedResponse: PreferenceResponse =
        Resources.readJson("status/AlrightIndividual200.json").as[PreferenceResponse]
      response.status mustBe expectedResponse.status

    }

    "retun 200 and Alright if Accepted/Organization/200  major < reoptinmajor" in new TestCase {
      withPreferences(email = Some(emailAddress()), accepted = true, major = REOPTINMAJOR - 1)
      withAuth(organization200)
      val response: PreferenceResponse = readPreferenceAuth()
      val expectedResponse: PreferenceResponse =
        Resources.readJson("status/AlrightOrganizationOnOldVersion.json").as[PreferenceResponse]
      response.status mustBe expectedResponse.status

    }

    "retun 200 and Alright if Accepted/Individual/300  major < reoptinmajor" in new TestCase {
      withPreferences(email = Some(emailAddress()), accepted = true, major = REOPTINMAJOR - 1)
      withAuth(organization200)
      val response: PreferenceResponse = readPreferenceAuth()
      val expectedResponse: PreferenceResponse =
        Resources.readJson("status/AlrightIndividual300OnOldVersion.json").as[PreferenceResponse]
      response.status mustBe expectedResponse.status
    }

  }

  class TestCase {
    val entityId: EntityId = GenerateRandom.entityId()
    val email: String = GenerateRandom.email()

    val controller: PreferencesController = app.injector.instanceOf[PreferencesController]
    val repoMock: PreferencesRepository = app.injector.instanceOf[PreferencesRepository]
    val mockAuthConnector: AuthConnector = app.injector.instanceOf[AuthConnector]
    val mockEmailService: ChangeEmailService = app.injector.instanceOf[ChangeEmailService]
    val mockEntityResolveConnector: EntityResolverConnector = app.injector.instanceOf[EntityResolverConnector]

    implicit def mat: Materializer = app.injector.instanceOf[Materializer]
    implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

    def readPreference(): PreferenceResponse = {
      val result: Future[Result] = call(controller.findPreferences(entityId), FakeRequest())
      status(result) must be(OK)
      contentAsJson(result).as[PreferenceResponse]
    }

    def readPreferenceAuth(): PreferenceResponse = {
      when(mockEntityResolveConnector.getEntityIdByAuth(any, any)(any))
        .thenReturn(EitherT.rightT[Future, EntityId](entityId))
      val result: Future[Result] =
        call(controller.findPreferencesByTaxIdOrAuth(PreferencesParams(None, None)), FakeRequest())
      status(result) must be(OK)
      contentAsJson(result).as[PreferenceResponse]
    }

    def emailAddress(
      email: String = email,
      verifiedOn: Option[Instant] = Some(Dc.instantNow()),
      lastBounce: Option[EmailBounce] = None,
      bounceCount: Int = 0,
      verifiedWithLink: Option[EmailVerificationLink] = None,
      language: Option[Language] = None
    ): EmailAddress =
      EmailAddress(
        email = email,
        verifiedOn = verifiedOn,
        lastBounce = lastBounce,
        bounceCount = bounceCount,
        verifiedWithLink = verifiedWithLink,
        language = language
      )
    def withPreferences(
      email: Option[EmailAddress],
      accepted: Boolean,
      pendingEmail: Option[PendingEmailAddress] = None,
      major: Int = REOPTINMAJOR,
      @unused paperless: Option[Boolean] = Some(true),
      updatedAt: Instant = Dc.instantNow()
    ): OngoingStubbing[Future[Option[Preferences]]] = {
      val tc =
        if (accepted)
          Accepted(updatedAt, optInPage = Some(OptInPage(Version(major, 2), cohort = 1, PageType.AndroidOptInPage)))
        else
          Refused(updatedAt)

      when(repoMock.findBy(eqTo(entityId))(any[HeaderCarrier])).thenReturn(
        Future.successful(
          Some(
            Preferences(
              entityId = entityId,
              email = email,
              pendingEmail = pendingEmail,
              termsAndConditions = TermsAndConditions(tc)
            )
          )
        )
      )
    }

    def withAuth(
      r: Future[Option[AffinityGroup] ~ ConfidenceLevel]
    ): OngoingStubbing[Future[Option[AffinityGroup] ~ ConfidenceLevel]] =
      when(
        mockAuthConnector.authorise[Option[AffinityGroup] ~ ConfidenceLevel](
          any[Predicate],
          any[Retrieval[Option[AffinityGroup] ~ ConfidenceLevel]]
        )(any[HeaderCarrier], any[ExecutionContext])
      )
        .thenReturn(r)

    val individual200: Future[Option[AffinityGroup] ~ ConfidenceLevel] =
      Future.successful(
        new ~(Some(AffinityGroup.Individual), ConfidenceLevel.L200)
      )

    val organization200: Future[Option[AffinityGroup] ~ ConfidenceLevel] =
      Future.successful(
        new ~(Some(AffinityGroup.Organisation), ConfidenceLevel.L200)
      )

    val authFailure: Future[Option[AffinityGroup.Individual.type] ~ ConfidenceLevel] =
      Future.failed(new Exception())
  }
}
