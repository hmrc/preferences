/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.controllers

import cats.data.EitherT
import org.apache.pekko.stream.Materializer
import org.bson.types.ObjectId
import org.mockito.ArgumentMatchers.{ any, eq as eqTo }
import org.mockito.Mockito.{ doNothing, never, verify, when }
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers.shouldBe
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.http.ContentTypes
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.bind
import play.api.libs.json.Json.toJson
import play.api.libs.json.{ JsDefined, JsNumber, JsResultException, JsString, JsValue, Json }
import play.api.test.{ FakeHeaders, FakeRequest }
import play.api.test.Helpers.*
import play.api.{ Application, inject }
import play.api.mvc.{ AnyContentAsEmpty, Result }
import play.mvc.Http
import play.api.test.Helpers.*
import play.test.Helpers.fakeRequest
import uk.gov.hmrc.crypto.{ Crypted, Decrypter, Encrypter, PlainBytes, PlainContent, PlainText }
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.paperless.controllers.model.PreferenceResponse
import uk.gov.hmrc.paperless.controllers.model.StatusName.{ EmailNotVerified, ReOptInModified }
import uk.gov.hmrc.preferences.connector.EntityResolverConnector
import uk.gov.hmrc.preferences.exceptions.{ DoNotProcess, EntityBadRequest, EntityNotFound, EntityRequestServerError, EntityResolverResponse, EntityUnauthorised, PreferenceNotFound }
import uk.gov.hmrc.preferences.{ Auditable, PreferencesParams, ResolveParams, TaxIdParams }
import uk.gov.hmrc.preferences.model.TermsAndConditions.*
import uk.gov.hmrc.preferences.model.*
import uk.gov.hmrc.preferences.repository.PreferencesRepository
import uk.gov.hmrc.preferences.service.{ ChangeEmailService, EmailBounceQueueMonitorService, NoEmailExists, PreferenceService, PreferencesChangedNotifierService, VerificationChaser }
import uk.gov.hmrc.preferences.util.Dc
import utils.GenerateRandom
import utils.TestData.{ TEST_EMAIL, TEST_ENTITY_ID, TEST_ERROR_MESSAGE, TEST_PREFERENCES, TEST_TAX_ID, TEST_URI }
import uk.gov.hmrc.preferences.controllers.EmailRequest
import utils.FakeApplicationCrypto

import scala.concurrent.{ ExecutionContext, Future }

class PreferencesControllerSpec
    extends PlaySpec with MockitoSugar with ScalaFutures with IntegrationPatience with GuiceOneAppPerTest
    with OptionValues {

  override def fakeApplication(): Application = {
    val repoMock = mock[PreferencesRepository]
    val mockEmailService = mock[ChangeEmailService]
    val auditableMock: Auditable = mock[Auditable]
    val mockPreferenceService: PreferenceService = mock[PreferenceService]
    val mockEntityResolverConnector: EntityResolverConnector = mock[EntityResolverConnector]

    val testCrypto = new Encrypter with Decrypter {
      override def decrypt(crypted: Crypted): PlainText = FakeApplicationCrypto.decrypt(crypted)
      override def decryptAsBytes(reversiblyEncrypted: Crypted): PlainBytes =
        FakeApplicationCrypto.decryptAsBytes(reversiblyEncrypted)
      override def encrypt(plain: PlainContent): Crypted = plain match {
        case PlainText(v) => Crypted(v)
        case _            => Crypted("fake_encryption")
      }
    }

    new GuiceApplicationBuilder()
      .configure("metrics.jvm" -> false)
      .configure("appName" -> "test app")
      .overrides(bind[PreferencesRepository].toInstance(repoMock))
      .overrides(bind[ChangeEmailService].toInstance(mockEmailService))
      .overrides(bind[Auditable].toInstance(auditableMock))
      .overrides(bind[PreferenceService].toInstance(mockPreferenceService))
      .overrides(bind[EntityResolverConnector].toInstance(mockEntityResolverConnector))
      .overrides(bind[Decrypter].toInstance(testCrypto))
      .overrides(bind[Encrypter].toInstance(testCrypto))
      .build()
  }

  "find preference" should {
    "return 200 with an existing preference for a given entity ID" in new TestCase {
      val surveyCompletedDateTime = Dc.instantNow()

      when(repoMock.findBy(eqTo(entityId))(any[HeaderCarrier])).thenReturn(
        Future.successful(
          Some(
            Preferences(
              entityId = entityId,
              email = None,
              termsAndConditions = TermsAndConditions(Accepted(Dc.instantNow())),
              surveys = Option(List(Survey(SurveyType.StandardInterruptOptOut, surveyCompletedDateTime)))
            )
          )
        )
      )
      private val result = call(controller.findPreferences(entityId), FakeRequest())
      status(result) must be(OK)
      val response: JsValue = contentAsJson(result)

      val em = response \ "email"
      em match {
        case JsDefined(v) => fail(s"Was expecting email section to be missing, but found it $v")
        case _            =>
      }

      response \ "entityId" mustBe JsDefined(JsString(entityId.value))
      val surveys = response \ "surveys"
      (surveys(0) \ "completedAt" \ "$date") mustBe JsDefined(JsNumber(surveyCompletedDateTime.toEpochMilli))
    }

    "return 200 with an existing preference for a given email address - POST" in new TestCase {
      when(repoMock.findByEmail(eqTo(email))(any[HeaderCarrier])).thenReturn(
        Future.successful(
          List(
            Preferences(
              entityId = entityId,
              email = Some(EmailAddress(email)),
              termsAndConditions = TermsAndConditions(Accepted(Dc.instantNow()))
            )
          )
        )
      )

      private val result = call(controller.findPreferencesByEmail(), FakeRequest().withBody(Json.obj("email" -> email)))
      status(result) must be(OK)

      val response: Seq[PreferenceResponse] = contentAsJson(result).as[Seq[PreferenceResponse]]
      response.map(_.email.get.email) must be(List(email))
    }

    "return 404 if there is no preference for a given entity ID" in new TestCase {
      when(repoMock.findBy(eqTo(entityId))(any[HeaderCarrier])).thenReturn(Future.successful(None))

      private val result = call(controller.findPreferences(entityId), FakeRequest())
      status(result) must be(NOT_FOUND)
    }

    "return 200 and pendingEmail in the PreferenceResponse if pendingEmail exists in preferences" in new TestCase {
      when(repoMock.findBy(eqTo(entityId))(any[HeaderCarrier])).thenReturn(
        Future.successful(
          Some(
            Preferences(
              entityId = entityId,
              email = None,
              pendingEmail = Some(PendingEmailAddress("test@test.com", language = Some(Language.English))),
              termsAndConditions = TermsAndConditions(Accepted(Dc.instantNow(), None))
            )
          )
        )
      )

      private val result = call(controller.findPreferences(entityId), FakeRequest())
      status(result) must be(OK)

      private val response = contentAsJson(result).as[PreferenceResponse]

      response.email.get.pendingEmail.get mustBe "test@test.com"
      response.entityId mustBe Some(entityId)
    }

    "return 200 and paperless false in the PreferenceResponse if not paperless" in new TestCase {
      when(repoMock.findBy(eqTo(entityId))(any[HeaderCarrier])).thenReturn(
        Future.successful(
          Some(
            Preferences(
              entityId = entityId,
              email = None,
              pendingEmail = Some(PendingEmailAddress("test@test.com", language = Some(Language.English))),
              termsAndConditions = TermsAndConditions(Accepted(Dc.instantNow(), None))
            )
          )
        )
      )

      private val result = call(controller.findPreferences(entityId), FakeRequest())
      status(result) must be(OK)

      private val response = contentAsJson(result).as[PreferenceResponse]

      response.termsAndConditions("generic").paperless.get must be(false)
      response.entityId mustBe Some(entityId)
    }

    "return 200 with paperless true if paperless" in new TestCase {
      when(repoMock.findBy(eqTo(entityId))(any[HeaderCarrier])).thenReturn(
        Future.successful(
          Some(
            Preferences(
              entityId = entityId,
              email = Some(EmailAddress(email, verifiedOn = Some(Dc.instantNow()))),
              termsAndConditions = TermsAndConditions(Accepted(Dc.instantNow(), None))
            )
          )
        )
      )

      private val result = call(controller.findPreferences(entityId), FakeRequest())
      status(result) must be(OK)

      private val response = contentAsJson(result).as[PreferenceResponse]

      response.termsAndConditions("generic").paperless.get must be(true)
      response.entityId mustBe Some(entityId)
    }

    "return the status 'EmailNotVerified' when it has both  verified email and  pending email" in new TestCase {
      when(repoMock.findBy(eqTo(entityId))(any[HeaderCarrier])).thenReturn(
        Future.successful(
          Some(
            Preferences(
              entityId = entityId,
              email = Some(EmailAddress(email, verifiedOn = Some(Dc.instantNow()))),
              termsAndConditions = TermsAndConditions(
                Accepted(Dc.instantNow(), optInPage = Some(OptInPage(Version(0, 0), cohort = 1, PageType.IPage)))
              ),
              pendingEmail = Some(PendingEmailAddress("test@test.com", language = Some(Language.Welsh)))
            )
          )
        )
      )

      private val result = call(controller.findPreferences(entityId), FakeRequest())
      status(result) must be(OK)

      private val response = contentAsJson(result).as[PreferenceResponse]
      response.status.value.name must be(EmailNotVerified)
    }

    "return the status 'ReOptInModified' when there are email bounces and has no pending email" in new TestCase {
      when(repoMock.findBy(eqTo(entityId))(any[HeaderCarrier])).thenReturn(
        Future.successful(
          Some(
            Preferences(
              entityId = entityId,
              email = Some(
                EmailAddress(
                  email,
                  verifiedOn = Some(Dc.instantNow()),
                  lastBounce = Some(EmailBounce(Some(100), Dc.instantNow()))
                )
              ),
              termsAndConditions = TermsAndConditions(
                Accepted(Dc.instantNow(), optInPage = Some(OptInPage(Version(0, 0), cohort = 1, PageType.IPage)))
              )
            )
          )
        )
      )

      private val result = call(controller.findPreferences(entityId), FakeRequest())
      status(result) must be(OK)

      private val response = contentAsJson(result).as[PreferenceResponse]
      response.status.value.name must be(ReOptInModified)
    }

    "confirm termsAndConditions acceptance on mobile" in new TestCase {
      when(repoMock.findBy(eqTo(entityId))(any[HeaderCarrier])).thenReturn(
        Future.successful(
          Some(
            Preferences(
              entityId = entityId,
              email = Some(
                EmailAddress(
                  email,
                  verifiedOn = Some(Dc.instantNow()),
                  lastBounce = Some(EmailBounce(Some(100), Dc.instantNow()))
                )
              ),
              termsAndConditions = TermsAndConditions(
                Accepted(
                  Dc.instantNow(),
                  optInPage = Some(OptInPage(Version(0, 0), cohort = 43, PageType.AndroidOptInPage))
                )
              )
            )
          )
        )
      )

      private val result = call(controller.findPreferences(entityId), FakeRequest())
      status(result) must be(OK)

      private val response = contentAsJson(result).as[PreferenceResponse]
      response.termsAndConditions.get("generic").exists(_.isViaMobileApp.getOrElse(false)) mustBe true
    }
  }

  "getLanguageOfEmail" should {
    "return 200 with English if verified email is not absent" in new TestCase {
      when(repoMock.findByEmail(eqTo(email))(any[HeaderCarrier])).thenReturn(
        Future.successful(
          List(
            Preferences(
              entityId = entityId,
              email = None,
              termsAndConditions = TermsAndConditions(Accepted(Dc.instantNow()))
            )
          )
        )
      )

      private val result = call(controller.getLanguageOfEmail(encryptedEmail), FakeRequest())
      status(result) must be(OK)

      private val response = contentAsJson(result).as[Language]
      response mustBe Language.English
    }

    "return 200 with Welsh for a single matching preference" in new TestCase {
      when(repoMock.findByEmail(eqTo(email))(any[HeaderCarrier])).thenReturn(
        Future.successful(
          List(
            Preferences(
              entityId = entityId,
              email = Some(EmailAddress(email, language = Some(Language.Welsh))),
              termsAndConditions = TermsAndConditions(Accepted(Dc.instantNow()))
            )
          )
        )
      )

      private val result = call(controller.getLanguageOfEmail(encryptedEmail), FakeRequest())
      private val response = contentAsJson(result).as[Language]

      response mustBe Language.Welsh
    }
    "return 200 with English for a single matching preference" in new TestCase {
      when(repoMock.findByEmail(eqTo(email))(any[HeaderCarrier])).thenReturn(
        Future.successful(
          List(
            Preferences(
              entityId = entityId,
              email = Some(EmailAddress(email, language = Some(Language.English))),
              termsAndConditions = TermsAndConditions(Accepted(Dc.instantNow()))
            )
          )
        )
      )

      private val result = call(controller.getLanguageOfEmail(encryptedEmail), FakeRequest())
      private val response = contentAsJson(result).as[Language]

      response mustBe Language.English
    }

    "return 200 with English if there are more than 1 matching prefernce" in new TestCase {
      when(repoMock.findByEmail(eqTo(email))(any[HeaderCarrier])).thenReturn(
        Future.successful(
          List(
            Preferences(
              entityId = entityId,
              email = Some(EmailAddress(email, language = Some(Language.Welsh))),
              termsAndConditions = TermsAndConditions(Accepted(Dc.instantNow()))
            ),
            Preferences(
              entityId = entityId,
              email = Some(EmailAddress(email, language = Some(Language.Welsh))),
              termsAndConditions = TermsAndConditions(Accepted(Dc.instantNow()))
            )
          )
        )
      )

      private val result = call(controller.getLanguageOfEmail(encryptedEmail), FakeRequest())
      private val response = contentAsJson(result).as[Language]

      response mustBe Language.English
    }

    "return 200 with English if there are no matching preferences" in new TestCase {
      when(repoMock.findByEmail(eqTo(email))(any[HeaderCarrier])).thenReturn(Future.successful(List()))
      private val result = call(controller.getLanguageOfEmail(encryptedEmail), FakeRequest())
      status(result) must be(OK)
      private val response = contentAsJson(result).as[Language]
      response mustBe Language.English
    }

    "return 200 with Welsh from pendingEmail model when there is no language set inside email model" in new TestCase {
      when(repoMock.findByEmail(eqTo(email))(any[HeaderCarrier])).thenReturn(
        Future.successful(
          List(
            Preferences(
              entityId = entityId,
              termsAndConditions = TermsAndConditions(Accepted(Dc.instantNow())),
              pendingEmail = Some(PendingEmailAddress("test@test.com", language = Some(Language.Welsh)))
            )
          )
        )
      )

      private val result = call(controller.getLanguageOfEmail(encryptedEmail), FakeRequest())
      private val response = contentAsJson(result).as[Language]

      response mustBe Language.Welsh
    }

    "return 200 with English from pendingEmail model when there is no language set inside email model" in new TestCase {
      when(repoMock.findByEmail(eqTo(email))(any[HeaderCarrier])).thenReturn(
        Future.successful(
          List(
            Preferences(
              entityId = entityId,
              termsAndConditions = TermsAndConditions(Accepted(Dc.instantNow())),
              pendingEmail = Some(PendingEmailAddress("test@test.com", language = Some(Language.English)))
            )
          )
        )
      )

      private val result = call(controller.getLanguageOfEmail(encryptedEmail), FakeRequest())
      private val response = contentAsJson(result).as[Language]

      response mustBe Language.English
    }

    "return 200 with English when there is no language for an existing preference" in new TestCase {
      when(repoMock.findByEmail(eqTo(email))(any[HeaderCarrier])).thenReturn(
        Future.successful(
          List(Preferences(entityId = entityId, termsAndConditions = TermsAndConditions(Accepted(Dc.instantNow()))))
        )
      )

      private val result = call(controller.getLanguageOfEmail(encryptedEmail), FakeRequest())
      private val response = contentAsJson(result).as[Language]

      response mustBe Language.English
    }

    "return 200 and use the Welsh language preference set in the email model when there both the email and pendingEmail models exist" in new TestCase {
      when(repoMock.findByEmail(eqTo(email))(any[HeaderCarrier])).thenReturn(
        Future.successful(
          List(
            Preferences(
              entityId = entityId,
              termsAndConditions = TermsAndConditions(Accepted(Dc.instantNow())),
              email = Some(EmailAddress(email, language = Some(Language.Welsh))),
              pendingEmail = Some(PendingEmailAddress("test@test.com", language = Some(Language.English)))
            )
          )
        )
      )

      private val result = call(controller.getLanguageOfEmail(encryptedEmail), FakeRequest())
      private val response = contentAsJson(result).as[Language]

      response mustBe Language.Welsh
    }
  }

  "store preference" should {
    "return OK when updating preference with email" in new TestCase {
      when(mockEmailService.setPending(eqTo(entityId), eqTo(email), eqTo(None))(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))

      private val result =
        call(controller.setPendingEmail(entityId), FakeRequest().withBody(Json.obj("email" -> email)))

      status(result) mustBe OK
    }
  }

  "NEW markForDeEnrolment" should {

    "mark preferences with the flag 'markForDeEnrolment' and return OK" in new TestCase {
      when(mockEntityResolverConnector.getEntityIdByTaxId(any, any)(any))
        .thenReturn(EitherT.rightT[Future, EntityId](entityId))

      when(repoMock.findBy(eqTo(entityId))(any[HeaderCarrier])).thenReturn(
        Future.successful(
          Some(
            Preferences(
              entityId = entityId,
              termsAndConditions = TermsAndConditions(Accepted(Dc.instantNow())),
              email = None,
              pendingEmail = Some(PendingEmailAddress("test@test.com", language = Some(Language.English)))
            )
          )
        )
      )

      when(repoMock.markForDeEnrolment(any[EntityId], any[String])(any[HeaderCarrier]))
        .thenReturn(Future.successful(true))

      private val result = call(controller.markForDeEnrolmentNew(TaxIdParams("s", "1")), FakeRequest())
      status(result).mustBe(OK)
    }

    "return 'NotModified'(304) when preferences already has the flag 'markForDeEnrolment'" in new TestCase {
      when(mockEntityResolverConnector.getEntityIdByTaxId(any, any)(any))
        .thenReturn(EitherT.rightT[Future, EntityId](entityId))

      when(repoMock.findBy(eqTo(entityId))(any[HeaderCarrier])).thenReturn(
        Future.successful(
          Some(
            Preferences(
              entityId = entityId,
              termsAndConditions = TermsAndConditions(Accepted(Dc.instantNow())),
              email = None,
              markForDeEnrolment = Some(MarkForDeEnrolment(Dc.instantNow(), "sa")),
              pendingEmail = Some(PendingEmailAddress("test@test.com", language = Some(Language.English)))
            )
          )
        )
      )

      private val result = call(controller.markForDeEnrolmentNew(TaxIdParams("s", "1")), FakeRequest())
      status(result).mustBe(NO_CONTENT)
    }

    "return 'NotFound'(404) when there is no preference exists for the given entityid" in new TestCase {
      when(mockEntityResolverConnector.getEntityIdByTaxId(any, any)(any))
        .thenReturn(EitherT.rightT[Future, EntityId](entityId))

      when(repoMock.findBy(eqTo(entityId))(any[HeaderCarrier])).thenReturn(Future.successful(None))

      private val result = call(controller.markForDeEnrolmentNew(TaxIdParams("s", "1")), FakeRequest())
      status(result).mustBe(NOT_FOUND)
    }

    "return 'NotFound'(404) when there is no entityid for the given taxid" in new TestCase {
      when(mockEntityResolverConnector.getEntityIdByTaxId(any, any)(any))
        .thenReturn(EitherT.leftT[Future, EntityResolverResponse](EntityNotFound))

      private val result = call(controller.markForDeEnrolmentNew(TaxIdParams("s", "1")), FakeRequest())
      status(result).mustBe(NOT_FOUND)
      contentAsString(result) mustBe "Entity not found"

      verify(repoMock, never()).findBy(eqTo(entityId))(any[HeaderCarrier])
    }

    "return 'BadRequest'(400) if parameters are incorrect" in new TestCase {
      when(mockEntityResolverConnector.getEntityIdByTaxId(any, any)(any))
        .thenReturn(EitherT.leftT[Future, EntityResolverResponse](EntityBadRequest("bad request")))

      private val result = call(controller.markForDeEnrolmentNew(TaxIdParams("s", "1")), FakeRequest())
      status(result).mustBe(BAD_REQUEST)
      contentAsString(result) mustBe "bad request"

      verify(repoMock, never()).findBy(eqTo(entityId))(any[HeaderCarrier])
    }
  }

  "NEW unsetDeEnrolment" should {

    "remove the flag 'markForDeEnrolment' and return OK" in new TestCase {
      when(mockEntityResolverConnector.getEntityIdByTaxId(any, any)(any))
        .thenReturn(EitherT.rightT[Future, EntityId](entityId))

      when(repoMock.findBy(eqTo(entityId))(any[HeaderCarrier])).thenReturn(
        Future.successful(
          Some(
            Preferences(
              entityId = entityId,
              termsAndConditions = TermsAndConditions(Accepted(Dc.instantNow())),
              email = None,
              markForDeEnrolment = Some(MarkForDeEnrolment(Dc.instantNow(), "sa")),
              pendingEmail = Some(PendingEmailAddress("test@test.com", language = Some(Language.English)))
            )
          )
        )
      )

      when(repoMock.unsetDeEnrolment(any[EntityId])(any[HeaderCarrier])).thenReturn(Future.successful(true))

      private val result = call(controller.unsetDeEnrolmentNew(TaxIdParams("s", "1")), FakeRequest())
      status(result).mustBe(OK)
    }

    "return 'NotModified'(304) when preferences dont have the flag 'markForDeEnrolment'" in new TestCase {
      when(mockEntityResolverConnector.getEntityIdByTaxId(any, any)(any))
        .thenReturn(EitherT.rightT[Future, EntityId](entityId))

      when(repoMock.findBy(eqTo(entityId))(any[HeaderCarrier])).thenReturn(
        Future.successful(
          Some(
            Preferences(
              entityId = entityId,
              termsAndConditions = TermsAndConditions(Accepted(Dc.instantNow())),
              email = None,
              pendingEmail = Some(PendingEmailAddress("test@test.com", language = Some(Language.English)))
            )
          )
        )
      )

      private val result = call(controller.unsetDeEnrolmentNew(TaxIdParams("s", "1")), FakeRequest())
      status(result).mustBe(NO_CONTENT)
    }

    "return 'NotFound'(404) when there is no preference exists for the given entityid" in new TestCase {
      when(mockEntityResolverConnector.getEntityIdByTaxId(any, any)(any))
        .thenReturn(EitherT.rightT[Future, EntityId](entityId))

      when(repoMock.findBy(eqTo(entityId))(any[HeaderCarrier])).thenReturn(Future.successful(None))

      private val result = call(controller.unsetDeEnrolmentNew(TaxIdParams("s", "1")), FakeRequest())
      status(result).mustBe(NOT_FOUND)
    }

    "return 'NotFound'(404) when there is no entityid for the given taxid" in new TestCase {
      when(mockEntityResolverConnector.getEntityIdByTaxId(any, any)(any))
        .thenReturn(EitherT.leftT[Future, EntityResolverResponse](EntityNotFound))

      private val result = call(controller.unsetDeEnrolmentNew(TaxIdParams("s", "1")), FakeRequest())
      status(result).mustBe(NOT_FOUND)
      contentAsString(result) mustBe "Entity not found"

      verify(repoMock, never()).findBy(eqTo(entityId))(any[HeaderCarrier])
    }

    "return 'BadRequest'(400) if parameters are incorrect" in new TestCase {
      when(mockEntityResolverConnector.getEntityIdByTaxId(any, any)(any))
        .thenReturn(EitherT.leftT[Future, EntityResolverResponse](EntityBadRequest("bad request")))

      private val result = call(controller.unsetDeEnrolmentNew(TaxIdParams("s", "1")), FakeRequest())
      status(result).mustBe(BAD_REQUEST)
      contentAsString(result) mustBe "bad request"

      verify(repoMock, never()).findBy(eqTo(entityId))(any[HeaderCarrier])
    }
  }

  "findPreferencesByTaxIdOrAuth" should {

    "return Ok with preference when taxRegime and taxId are provided and preference is found" in new TestCase {
      when(mockEntityResolverConnector.getEntityIdByTaxId(any, any)(any))
        .thenReturn(EitherT.rightT[Future, EntityId](entityId))
      when(mockPreferenceService.getPreferencesByEntityId(any)(any[HeaderCarrier]))
        .thenReturn(EitherT.rightT[Future, Preferences](preference))

      val result: Future[Result] = controller
        .findPreferencesByTaxIdOrAuth(PreferencesParams(Some(TaxIdParams(taxRegime, taxId)), None))
        .apply(FakeRequest(GET, s"/preferences?taxRegime=$taxRegime&taxId=$taxId"))

      status(result) mustBe OK

      val response: PreferenceResponse = contentAsJson(result).as[PreferenceResponse]
      response.entityId mustBe Some(entityId)
    }

    "return NotFound when taxRegime and taxId are provided but preference is not found" in new TestCase {
      when(mockEntityResolverConnector.getEntityIdByTaxId(any, any)(any))
        .thenReturn(EitherT.rightT[Future, EntityId](entityId))
      when(mockPreferenceService.getPreferencesByEntityId(any)(any[HeaderCarrier]))
        .thenReturn(EitherT.leftT[Future, PreferenceNotFound](PreferenceNotFound()))

      val result: Future[Result] = controller
        .findPreferencesByTaxIdOrAuth(PreferencesParams(Some(TaxIdParams(taxRegime, taxId)), None))
        .apply(FakeRequest(GET, s"/preferences?taxIdType=$taxRegime&taxId=$taxId"))

      status(result) shouldBe NOT_FOUND
      contentAsString(result) shouldBe "Preference not found"
    }

    "return Ok with preference when no parameters are provided and entity is resolved by auth" in new TestCase {
      when(mockEntityResolverConnector.getEntityIdByAuth(any, any)(any))
        .thenReturn(EitherT.rightT[Future, EntityId](entityId))
      when(mockPreferenceService.getPreferencesByEntityId(any)(any[HeaderCarrier]))
        .thenReturn(EitherT.rightT[Future, Preferences](preference))

      val result: Future[Result] = controller
        .findPreferencesByTaxIdOrAuth(PreferencesParams(None, None))
        .apply(FakeRequest(GET, s"/preferences"))

      status(result) shouldBe OK

      val response: PreferenceResponse = contentAsJson(result).as[PreferenceResponse]

      response.entityId mustBe Some(entityId)
      verify(mockEntityResolverConnector).getEntityIdByAuth(eqTo(Some(true)), any[Option[Boolean]])(any)
    }

    "return Ok with preference when no parameters are provided and entity returned by auth, with resolution" in new TestCase {
      when(mockEntityResolverConnector.getEntityIdByAuth(any, any)(any))
        .thenReturn(EitherT.rightT[Future, EntityId](entityId))
      when(mockPreferenceService.getPreferencesByEntityId(any)(any[HeaderCarrier]))
        .thenReturn(EitherT.rightT[Future, Preferences](preference))

      val result: Future[Result] = controller
        .findPreferencesByTaxIdOrAuth(PreferencesParams(None, Some(ResolveParams(true))))
        .apply(FakeRequest(GET, s"/preferences?resolve=true"))

      status(result) shouldBe OK

      val response: PreferenceResponse = contentAsJson(result).as[PreferenceResponse]
      response.entityId mustBe Some(entityId)

      verify(mockEntityResolverConnector).getEntityIdByAuth(eqTo(Some(true)), any[Option[Boolean]])(any)
    }

    "return Ok with preference when no parameters are provided and entity returned by auth, without resolution" in new TestCase {
      when(mockEntityResolverConnector.getEntityIdByAuth(any, any)(any))
        .thenReturn(EitherT.rightT[Future, EntityId](entityId))
      when(mockPreferenceService.getPreferencesByEntityId(any)(any[HeaderCarrier]))
        .thenReturn(EitherT.rightT[Future, Preferences](preference))

      val result: Future[Result] = controller
        .findPreferencesByTaxIdOrAuth(PreferencesParams(None, Some(ResolveParams(false))))
        .apply(FakeRequest(GET, s"/preferences?resolve=false"))

      status(result) shouldBe OK

      val response: PreferenceResponse = contentAsJson(result).as[PreferenceResponse]
      response.entityId mustBe Some(entityId)
      verify(mockEntityResolverConnector).getEntityIdByAuth(eqTo(Some(false)), any[Option[Boolean]])(any)
    }

    "return BadRequest when no parameters are provided and EntityBadRequest is returned while getting the entity" in new TestCase {
      when(mockEntityResolverConnector.getEntityIdByAuth(any, any)(any))
        .thenReturn(EitherT.leftT[Future, EntityResolverResponse](EntityBadRequest(TEST_ERROR_MESSAGE)))

      val result: Future[Result] = controller
        .findPreferencesByTaxIdOrAuth(PreferencesParams(None, None))
        .apply(FakeRequest(GET, s"/preferences"))

      status(result) shouldBe BAD_REQUEST
    }

    "return NotFound when no parameters are provided and PreferenceNotFound is returned while getting the entity" in new TestCase {
      when(mockEntityResolverConnector.getEntityIdByAuth(any, any)(any))
        .thenReturn(EitherT.leftT[Future, EntityResolverResponse](PreferenceNotFound(TEST_ERROR_MESSAGE)))

      val result: Future[Result] = controller
        .findPreferencesByTaxIdOrAuth(PreferencesParams(None, None))
        .apply(FakeRequest(GET, s"/preferences"))

      status(result) shouldBe NOT_FOUND
    }

    "return NotFound when no parameters are provided and EntityNotFound is returned while getting the entity" in new TestCase {
      when(mockEntityResolverConnector.getEntityIdByAuth(any, any)(any))
        .thenReturn(EitherT.leftT[Future, EntityResolverResponse](EntityNotFound))

      val result: Future[Result] = controller
        .findPreferencesByTaxIdOrAuth(PreferencesParams(None, None))
        .apply(FakeRequest(GET, s"/preferences"))

      status(result) shouldBe NOT_FOUND
    }

    "return Unauthorized when no parameters are provided and EntityUnauthorised is returned while getting the entity" in new TestCase {
      when(mockEntityResolverConnector.getEntityIdByAuth(any, any)(any))
        .thenReturn(EitherT.leftT[Future, EntityResolverResponse](EntityUnauthorised("Error occcured")))

      val result: Future[Result] = controller
        .findPreferencesByTaxIdOrAuth(PreferencesParams(None, None))
        .apply(FakeRequest(GET, s"/preferences"))

      status(result) shouldBe UNAUTHORIZED
    }

    "return InternalServerError when no parameters are provided and EntityRequestServerError is returned while getting the entity" in new TestCase {
      when(mockEntityResolverConnector.getEntityIdByAuth(any, any)(any))
        .thenReturn(EitherT.leftT[Future, EntityResolverResponse](EntityRequestServerError("Error occcured")))

      val result: Future[Result] = controller
        .findPreferencesByTaxIdOrAuth(PreferencesParams(None, None))
        .apply(FakeRequest(GET, s"/preferences"))

      status(result) shouldBe INTERNAL_SERVER_ERROR
    }

    "return InternalServerError when no parameters are provided and DoNotProcess is returned while getting the entity" in new TestCase {
      when(mockEntityResolverConnector.getEntityIdByAuth(any, any)(any))
        .thenReturn(EitherT.leftT[Future, EntityResolverResponse](DoNotProcess))

      val result: Future[Result] = controller
        .findPreferencesByTaxIdOrAuth(PreferencesParams(None, None))
        .apply(FakeRequest(GET, s"/preferences"))

      status(result) shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "initiatePendingEmail" should {
    "return NotFound when EntityBadRequest is returned while getting the entity" in new TestCase {
      val emailRequest: EmailRequest = EmailRequest(email = TEST_EMAIL, journey = Some("test_journey"))

      when(mockEmailService.setPendingEmail(any, any)(any))
        .thenReturn(EitherT.leftT[Future, EntityResolverResponse](EntityBadRequest(TEST_ERROR_MESSAGE)))

      when(mockEntityResolverConnector.getEntityIdByAuth(any, any)(any))
        .thenReturn(EitherT.leftT[Future, EntityResolverResponse](EntityBadRequest(TEST_ERROR_MESSAGE)))

      val result: Future[Result] = controller
        .initiatePendingEmail()
        .apply(FakeRequest(PUT, "/preferences/pending-email").withBody(Json.toJson(emailRequest)))

      status(result) mustBe NOT_FOUND
    }

    "return NotFound when NoEmailExists is returned while setting the pending email" in new TestCase {
      val emailRequest: EmailRequest = EmailRequest(email = TEST_EMAIL, journey = Some("test_journey"))

      when(mockEmailService.setPendingEmail(any, any)(any))
        .thenReturn(EitherT.leftT[Future, EntityResolverResponse](NoEmailExists(TEST_ERROR_MESSAGE)))

      when(mockEntityResolverConnector.getEntityIdByAuth(any, any)(any))
        .thenReturn(EitherT.leftT[Future, EntityResolverResponse](EntityBadRequest(TEST_ERROR_MESSAGE)))

      val result: Future[Result] = controller
        .initiatePendingEmail()
        .apply(FakeRequest(PUT, "/preferences/pending-email").withBody(Json.toJson(emailRequest)))

      status(result) mustBe NOT_FOUND
    }
  }

  "updated" should {

    "return NoContent when preferences are updated successfully" in new TestCase {
      private val mockPreferencesRepository = mock[PreferencesRepository]
      private val mockPrefChangedNotifierService = mock[PreferencesChangedNotifierService]
      private val mockEntityResolverConnector = mock[EntityResolverConnector]

      val application: Application = new GuiceApplicationBuilder()
        .configure("metrics.enabled" -> false)
        .configure("auditing.enabled" -> false)
        .configure("metrics.graphite.enabled" -> false)
        .overrides(
          inject.bind[PreferencesRepository].toInstance(mockPreferencesRepository),
          inject.bind[PreferencesChangedNotifierService].toInstance(mockPrefChangedNotifierService),
          inject.bind[EntityResolverConnector].toInstance(mockEntityResolverConnector),
          inject.bind[Decrypter].toInstance(FakeApplicationCrypto)
        )
        .build()

      when(mockPreferencesRepository.updated(any)(any)).thenReturn(Future.successful(Some(TEST_PREFERENCES)))
      when(mockPrefChangedNotifierService.notifyPreferencesChanged(any, any, any, any, any)(any, any))
        .thenReturn(Future.successful(()))
      when(mockEntityResolverConnector.getTaxId(any)(any)).thenReturn(Future.successful(TEST_TAX_ID))

      val request: FakeRequest[JsValue] =
        FakeRequest(
          PUT,
          routes.PreferencesController.updated(TEST_ENTITY_ID).url,
          FakeHeaders(Seq(CONTENT_TYPE -> ContentTypes.JSON)),
          toJson(Map[String, String]())
        )

      val result: Future[Result] = route(application, request).value

      status(result) shouldBe NO_CONTENT
    }

    "return InternalServerError when a runtime error occurs while updating the preferences" in new TestCase {
      private val mockPreferencesRepository = mock[PreferencesRepository]

      val application: Application = new GuiceApplicationBuilder()
        .configure("metrics.enabled" -> false)
        .configure("auditing.enabled" -> false)
        .configure("metrics.graphite.enabled" -> false)
        .overrides(
          inject.bind[PreferencesRepository].toInstance(mockPreferencesRepository),
          inject.bind[Decrypter].toInstance(FakeApplicationCrypto)
        )
        .build()

      when(mockPreferencesRepository.updated(any)(any))
        .thenReturn(Future.failed(new RuntimeException(TEST_ERROR_MESSAGE)))

      val request: FakeRequest[JsValue] =
        FakeRequest(
          PUT,
          routes.PreferencesController.updated(TEST_ENTITY_ID).url,
          FakeHeaders(Seq(CONTENT_TYPE -> ContentTypes.JSON)),
          toJson(Map[String, String]())
        )

      val result: Future[Result] = route(application, request).value

      status(result) shouldBe INTERNAL_SERVER_ERROR
    }

    "return NotFound when PreferenceNotFound error occurs while updating the preferences" in new TestCase {
      private val mockPreferencesRepository = mock[PreferencesRepository]

      val application: Application = new GuiceApplicationBuilder()
        .configure("metrics.enabled" -> false)
        .configure("auditing.enabled" -> false)
        .configure("metrics.graphite.enabled" -> false)
        .overrides(
          inject.bind[PreferencesRepository].toInstance(mockPreferencesRepository),
          inject.bind[Decrypter].toInstance(FakeApplicationCrypto)
        )
        .build()

      when(mockPreferencesRepository.updated(any)(any))
        .thenReturn(Future.failed(PreferenceNotFound(TEST_ERROR_MESSAGE)))

      val request: FakeRequest[JsValue] =
        FakeRequest(
          PUT,
          routes.PreferencesController.updated(TEST_ENTITY_ID).url,
          FakeHeaders(Seq(CONTENT_TYPE -> ContentTypes.JSON)),
          toJson(Map[String, String]())
        )

      val result: Future[Result] = route(application, request).value

      status(result) shouldBe NOT_FOUND
    }
  }

  "FormattedUri.formats" should {
    import FormattedUri.formats

    "read the json correctly" in new Setup {
      Json.parse(formattedUriJsonString).as[FormattedUri] mustBe formattedUri
    }

    "throw exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(formattedUriInvalidJsonString).as[FormattedUri]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(formattedUri) mustBe Json.parse(formattedUriJsonString)
    }
  }

  trait TestCase {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

    val entityId: EntityId = GenerateRandom.entityId()
    val email: String = GenerateRandom.email()
    val encrypter: Encrypter = app.injector.instanceOf[Encrypter]
    val encryptedEmail: String = new String(encrypter.encrypt(PlainText(email)).toBase64)

    val controller: PreferencesController = app.injector.instanceOf[PreferencesController]
    val repoMock: PreferencesRepository = app.injector.instanceOf[PreferencesRepository]
    val auditableMock: Auditable = app.injector.instanceOf[Auditable]
    val mockEmailService: ChangeEmailService = app.injector.instanceOf[ChangeEmailService]
    val mockPreferenceService: PreferenceService = app.injector.instanceOf[PreferenceService]
    val mockEntityResolverConnector: EntityResolverConnector = app.injector.instanceOf[EntityResolverConnector]

    val sautr: SaUtr = SaUtr("1234567890")
    val taxRegime: String = sautr.name
    val taxId: String = sautr.value

    val preference: Preferences =
      Preferences(
        entityId,
        termsAndConditions = TermsAndConditions(Accepted(Dc.instantNow()))
      )

    implicit def mat: Materializer = app.injector.instanceOf[Materializer]
  }

  trait Setup {
    val formattedUri: FormattedUri = FormattedUri(uri = TEST_URI)

    val formattedUriJsonString: String = """{"uri":"test_uri"}""".stripMargin
    val formattedUriInvalidJsonString: String = """{"uri":5}""".stripMargin
  }

}
