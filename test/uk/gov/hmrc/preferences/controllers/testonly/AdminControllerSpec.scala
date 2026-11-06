/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.preferences.controllers.testonly

import cats.data.EitherT
import org.bson.types.ObjectId
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.{ Application, inject }
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{ AnyContentAsEmpty, Result }
import play.api.test.FakeRequest
import uk.gov.hmrc.http.{ HeaderCarrier, NotFoundException }
import uk.gov.hmrc.preferences.repository.{ BrokenVerificationLinkException, PreferenceUpdated, PreferencesRepository }
import uk.gov.hmrc.preferences.service.{ EmailBounceQueueMonitorService, ProcessingResult, VerificationChaser }
import utils.TestData.*
import play.api.test.Helpers.*
import utils.{ FakeApplicationCrypto, SpecBase }
import org.mockito.ArgumentMatchers.any
import play.api.libs.json.{ JsValue, Json }
import uk.gov.hmrc.crypto.{ Decrypter, Encrypter }
import uk.gov.hmrc.preferences.connector.{ Bounce, EntityResolverConnector }
import uk.gov.hmrc.preferences.controllers.EmailRequest
import uk.gov.hmrc.preferences.model.{ EmailVerificationLink, EntityId, PendingEmailAddress }

import java.time.{ Duration, Instant }
import scala.concurrent.{ ExecutionContext, Future }

class AdminControllerSpec extends SpecBase {

  "deletePreferences" should {
    "remove the preferences for the provided entityId" in new Setup {
      when(mockPreferencesRepository.findBy(any)(any)).thenReturn(Future.successful(Some(TEST_PREFERENCES)))
      when(mockPreferencesRepository.removeById(any)(any)).thenReturn(Future.successful(true))

      val request: FakeRequest[AnyContentAsEmpty.type] =
        fakeRequest(DELETE, routes.AdminController.deletePreferences(TEST_ENTITY_ID).url)

      val result: Future[Result] = route(app, request).value
      status(result) mustBe OK
    }

    "return OK if no preferences is found for the provided entityId" in new Setup {
      when(mockPreferencesRepository.findBy(any)(any)).thenReturn(Future.successful(None))

      val request: FakeRequest[AnyContentAsEmpty.type] =
        fakeRequest(DELETE, routes.AdminController.deletePreferences(TEST_ENTITY_ID).url)

      val result: Future[Result] = route(app, request).value
      status(result) mustBe OK
    }

    "return NotFound if NotFoundException occurs while fetching the preferences" in new Setup {
      when(mockPreferencesRepository.findBy(any)(any))
        .thenReturn(Future.failed(TEST_HTTP_NOT_FOUND_EXCEPTION))

      val request: FakeRequest[AnyContentAsEmpty.type] =
        fakeRequest(DELETE, routes.AdminController.deletePreferences(TEST_ENTITY_ID).url)

      val result: Future[Result] = route(app, request).value
      status(result) mustBe NOT_FOUND
    }
  }

  "deleteAllPreferences" should {
    "return OK if all preferences are successfully removed" in new Setup {
      when(mockPreferencesRepository.removeAll()(any)).thenReturn(Future.successful(true))

      val request: FakeRequest[AnyContentAsEmpty.type] =
        fakeRequest(DELETE, routes.AdminController.deleteAllPreferences().url)

      val result: Future[Result] = route(app, request).value
      status(result) mustBe OK
    }

    "return BadRequest if all preferences are not removed" in new Setup {
      when(mockPreferencesRepository.removeAll()(any)).thenReturn(Future.successful(false))

      val request: FakeRequest[AnyContentAsEmpty.type] =
        fakeRequest(DELETE, routes.AdminController.deleteAllPreferences().url)

      val result: Future[Result] = route(app, request).value
      status(result) mustBe BAD_REQUEST
    }
  }

  "expireEmailVerificationLink" should {
    "return OK when expireEmailVerificationLink is successfully expired" in new Setup {
      when(mockPreferencesRepository.findBy(any)(any)).thenReturn(Future.successful(Some(TEST_PREFERENCES)))
      when(mockPreferencesRepository.expireEmailVerificationLink(any, any)(any)).thenReturn(Future.successful(true))

      val request: FakeRequest[AnyContentAsEmpty.type] =
        fakeRequest(POST, routes.AdminController.expireEmailVerificationLink(TEST_ENTITY_ID).url)

      val result: Future[Result] = route(app, request).value
      status(result) mustBe OK
    }

    "return NotFound when no preferences is found for the Entity Id" in new Setup {
      when(mockPreferencesRepository.findBy(any)(any)).thenReturn(Future.successful(None))

      val request: FakeRequest[AnyContentAsEmpty.type] =
        fakeRequest(POST, routes.AdminController.expireEmailVerificationLink(TEST_ENTITY_ID).url)

      val result: Future[Result] = route(app, request).value
      status(result) mustBe NOT_FOUND
    }

    "return NotFound when there is no Pending Email Address in the found Preferences" in new Setup {
      when(mockPreferencesRepository.findBy(any)(any))
        .thenReturn(Future.successful(Some(TEST_PREFERENCES.copy(pendingEmail = None))))

      val request: FakeRequest[AnyContentAsEmpty.type] =
        fakeRequest(POST, routes.AdminController.expireEmailVerificationLink(TEST_ENTITY_ID).url)

      val result: Future[Result] = route(app, request).value
      status(result) mustBe NOT_FOUND
    }

    "return NotFound if NotFoundException occurs while fetching the preferences" in new Setup {
      when(mockPreferencesRepository.findBy(any)(any))
        .thenReturn(Future.failed(TEST_HTTP_NOT_FOUND_EXCEPTION))

      val request: FakeRequest[AnyContentAsEmpty.type] =
        fakeRequest(POST, routes.AdminController.expireEmailVerificationLink(TEST_ENTITY_ID).url)

      val result: Future[Result] = route(app, request).value
      status(result) mustBe NOT_FOUND
    }
  }

  "verificationToken" should {
    "return OK when pending email address has verification link in the found preferences" in new Setup {
      when(mockPreferencesRepository.findBy(any)(any)).thenReturn(Future.successful(Some(TEST_PREFERENCES)))

      val request: FakeRequest[AnyContentAsEmpty.type] =
        fakeRequest(GET, routes.AdminController.verificationToken(TEST_ENTITY_ID).url)

      val result: Future[Result] = route(app, request).value
      status(result) mustBe OK
    }

    "return NotFound when no preferences is found" in new Setup {
      when(mockPreferencesRepository.findBy(any)(any)).thenReturn(Future.successful(None))

      val request: FakeRequest[AnyContentAsEmpty.type] =
        fakeRequest(GET, routes.AdminController.verificationToken(TEST_ENTITY_ID).url)

      val result: Future[Result] = route(app, request).value
      status(result) mustBe NOT_FOUND
    }

    "return NotFound if NotFoundException occurs while fetching the preferences" in new Setup {
      when(mockPreferencesRepository.findBy(any)(any))
        .thenReturn(Future.failed(TEST_HTTP_NOT_FOUND_EXCEPTION))

      val request: FakeRequest[AnyContentAsEmpty.type] =
        fakeRequest(GET, routes.AdminController.verificationToken(TEST_ENTITY_ID).url)

      val result: Future[Result] = route(app, request).value
      status(result) mustBe NOT_FOUND
    }
  }

  "bounceEmail" should {
    import Bounce.formats

    "return NoContent when email is successfully bounced" in new Setup {
      when(mockEmailBounceQueueMonitorService.markAsBounced(any)(any))
        .thenReturn(Future.successful(()))

      val request: FakeRequest[AnyContentAsEmpty.type] =
        fakeRequest(POST, routes.AdminController.bounceEmail().url)

      val result: Future[Result] = route(app, request.withBody(Json.toJson(bounce))).value
      status(result) mustBe NO_CONTENT
    }

    "return NotFound if NotFoundException occurs while calling queue monitor service" in new Setup {
      when(mockEmailBounceQueueMonitorService.markAsBounced(any)(any))
        .thenReturn(Future.failed(TEST_HTTP_NOT_FOUND_EXCEPTION))

      val request: FakeRequest[AnyContentAsEmpty.type] =
        fakeRequest(POST, routes.AdminController.bounceEmail().url)

      val result: Future[Result] = route(app, request.withBody(Json.toJson(bounce))).value
      status(result) mustBe NOT_FOUND
    }
  }

  "verifyEmail" should {
    "return NoContent when link is successfully verified" in new Setup {
      val emailVerificationLink: EmailVerificationLink =
        EmailVerificationLink(linkSentTime = Instant.now.minus(Duration.ofDays(1)))

      val updatedPendingEmail: Option[PendingEmailAddress] =
        TEST_PREFERENCES.pendingEmail.map(emailAddress =>
          emailAddress.copy(verificationLink = Some(emailVerificationLink))
        )

      when(mockPreferencesRepository.findBy(any)(any))
        .thenReturn(Future.successful(Some(TEST_PREFERENCES.copy(pendingEmail = updatedPendingEmail))))

      when(mockPreferencesRepository.markEmailVerified(any, any, any, any)(any)).thenReturn(Future.successful(()))

      val request: FakeRequest[AnyContentAsEmpty.type] =
        fakeRequest(POST, routes.AdminController.verifyEmail(TEST_ENTITY_ID).url)

      val result: Future[Result] = route(app, request).value
      status(result) mustBe NO_CONTENT
    }

    "return NotFound if NotFoundException occurs while calling preferences api to get the preferences" in new Setup {
      when(mockPreferencesRepository.findBy(any)(any))
        .thenReturn(Future.failed(TEST_HTTP_NOT_FOUND_EXCEPTION))

      val request: FakeRequest[AnyContentAsEmpty.type] =
        fakeRequest(POST, routes.AdminController.verifyEmail(TEST_ENTITY_ID).url)

      val result: Future[Result] = route(app, request).value
      status(result) mustBe NOT_FOUND
    }

    "return NotFound when preferences do not have the pending email" in new Setup {
      when(mockPreferencesRepository.findBy(any)(any))
        .thenReturn(Future.successful(Some(TEST_PREFERENCES.copy(pendingEmail = None))))

      val request: FakeRequest[AnyContentAsEmpty.type] =
        fakeRequest(POST, routes.AdminController.verifyEmail(TEST_ENTITY_ID).url)

      val result: Future[Result] = route(app, request).value
      status(result) mustBe NOT_FOUND
    }

    "return BadRequest if BrokenVerificationLinkException occurs while marking the email verified" in new Setup {
      val emailVerificationLink: EmailVerificationLink =
        EmailVerificationLink(linkSentTime = Instant.now.minus(Duration.ofDays(1)))

      val updatedPendingEmail: Option[PendingEmailAddress] =
        TEST_PREFERENCES.pendingEmail.map(emailAddress =>
          emailAddress.copy(verificationLink = Some(emailVerificationLink))
        )

      when(mockPreferencesRepository.findBy(any)(any))
        .thenReturn(Future.successful(Some(TEST_PREFERENCES.copy(pendingEmail = updatedPendingEmail))))

      when(mockPreferencesRepository.markEmailVerified(any, any, any, any)(any))
        .thenReturn(Future.failed(BrokenVerificationLinkException(preferencesId = ObjectId.get())))

      val request: FakeRequest[AnyContentAsEmpty.type] =
        fakeRequest(POST, routes.AdminController.verifyEmail(TEST_ENTITY_ID).url)

      val result: Future[Result] = route(app, request).value
      status(result) mustBe BAD_REQUEST
    }
  }

  "processVerificationReminders" should {
    "return OK" in new Setup {
      when(mockVerificationChaser.chaseVerifications(any))
        .thenReturn(Future.successful(ProcessingResult.Empty))

      val request: FakeRequest[AnyContentAsEmpty.type] =
        fakeRequest(POST, routes.AdminController.processVerificationReminders().url)

      val result: Future[Result] = route(app, request.withBody(Json.toJson(bounce))).value
      status(result) mustBe OK
    }
  }

  "findPreferencesByEmail" should {
    import EmailRequest.fmt

    "return OK with the preferences for the given email address" in new Setup {
      when(mockPreferencesRepository.findByEmail(any)(any)).thenReturn(Future.successful(Seq(TEST_PREFERENCES)))

      val request: FakeRequest[JsValue] =
        fakeRequest(POST, routes.AdminController.findPreferencesByEmail().url).withBody(Json.toJson(testEmail))

      val result: Future[Result] = route(app, request).value
      status(result) mustBe OK
    }

    "return NOT_FOUND if no preferences is found for the given email address" in new Setup {
      when(mockPreferencesRepository.findByEmail(any)(any)).thenReturn(Future.successful(Nil))

      val request: FakeRequest[JsValue] =
        fakeRequest(POST, routes.AdminController.findPreferencesByEmail().url).withBody(Json.toJson(testEmail))

      val result: Future[Result] = route(app, request).value
      status(result) mustBe NOT_FOUND
    }

    "return NotFound if NotFoundException occurs while fetching the preferences" in new Setup {
      when(mockPreferencesRepository.findByEmail(any)(any))
        .thenReturn(Future.failed(TEST_HTTP_NOT_FOUND_EXCEPTION))

      val request: FakeRequest[JsValue] =
        fakeRequest(POST, routes.AdminController.findPreferencesByEmail().url).withBody(Json.toJson(testEmail))

      val result: Future[Result] = route(app, request).value
      status(result) mustBe NOT_FOUND
    }
  }

  "findPreferencesByEntityId" should {

    "return OK with the preferences for the given entityId" in new Setup {
      when(mockPreferencesRepository.findBy(any)(any)).thenReturn(Future.successful(Some(TEST_PREFERENCES)))

      val request: FakeRequest[AnyContentAsEmpty.type] =
        fakeRequest(GET, routes.AdminController.findPreferencesByEntityId(TEST_ENTITY_ID).url)

      val result: Future[Result] = route(app, request).value
      status(result) mustBe OK
    }

    "return NOT_FOUND if no preferences is found for the given entityId" in new Setup {
      when(mockPreferencesRepository.findBy(any)(any)).thenReturn(Future.successful(None))

      val request: FakeRequest[AnyContentAsEmpty.type] =
        fakeRequest(GET, routes.AdminController.findPreferencesByEntityId(TEST_ENTITY_ID).url)

      val result: Future[Result] = route(app, request).value
      status(result) mustBe NOT_FOUND
    }

    "return NotFound if NotFoundException occurs while fetching the preferences" in new Setup {
      when(mockPreferencesRepository.findBy(any)(any))
        .thenReturn(Future.failed(TEST_HTTP_NOT_FOUND_EXCEPTION))

      val request: FakeRequest[AnyContentAsEmpty.type] =
        fakeRequest(GET, routes.AdminController.findPreferencesByEntityId(TEST_ENTITY_ID).url)

      val result: Future[Result] = route(app, request).value
      status(result) mustBe NOT_FOUND
    }
  }

  "findPreferencesByNino" should {

    "return OK with the preferences for the given nino" in new Setup {
      when(mockEntityResolverConnector.getEntityIdByTaxId(any, any)(any))
        .thenReturn(EitherT.rightT[Future, EntityId](TEST_ENTITY_ID))
      when(mockPreferencesRepository.findBy(any)(any)).thenReturn(Future.successful(Some(TEST_PREFERENCES)))

      val request: FakeRequest[AnyContentAsEmpty.type] =
        fakeRequest(GET, routes.AdminController.findPreferencesByNino(TEST_NINO).url)

      val result: Future[Result] = route(app, request).value
      status(result) mustBe OK
    }

    "return NOT_FOUND if no preferences is found for the given nino" in new Setup {
      when(mockEntityResolverConnector.getEntityIdByTaxId(any, any)(any))
        .thenReturn(EitherT.rightT[Future, EntityId](TEST_ENTITY_ID))
      when(mockPreferencesRepository.findBy(any)(any)).thenReturn(Future.successful(None))

      val request: FakeRequest[AnyContentAsEmpty.type] =
        fakeRequest(GET, routes.AdminController.findPreferencesByNino(TEST_NINO).url)

      val result: Future[Result] = route(app, request).value
      status(result) mustBe NOT_FOUND
    }

    "return NotFound if NotFoundException occurs while fetching the preferences" in new Setup {
      when(mockEntityResolverConnector.getEntityIdByTaxId(any, any)(any))
        .thenReturn(EitherT.rightT[Future, EntityId](TEST_ENTITY_ID))
      when(mockPreferencesRepository.findBy(any)(any))
        .thenReturn(Future.failed(TEST_HTTP_NOT_FOUND_EXCEPTION))

      val request: FakeRequest[AnyContentAsEmpty.type] =
        fakeRequest(GET, routes.AdminController.findPreferencesByNino(TEST_NINO).url)

      val result: Future[Result] = route(app, request).value
      status(result) mustBe NOT_FOUND
    }
  }

  trait Setup {
    val mockPreferencesRepository: PreferencesRepository = mock[PreferencesRepository]
    val mockEmailBounceQueueMonitorService: EmailBounceQueueMonitorService = mock[EmailBounceQueueMonitorService]
    val mockVerificationChaser: VerificationChaser = mock[VerificationChaser]
    val mockEntityResolverConnector = mock[EntityResolverConnector]

    val bounce: Bounce = Bounce(
      emailAddress = TEST_EMAIL,
      detected = TEST_TIME_INSTANT,
      code = Some(TEST_CODE),
      emailSource = Some(TEST_SOURCE),
      formType = Some(TEST_FORM_TYPE),
      nino = Some(TEST_NINO)
    )

    val testEmail = EmailRequest("test@test.com")

    val app: Application = applicationBuilder
      .overrides(
        inject.bind[PreferencesRepository].toInstance(mockPreferencesRepository),
        inject.bind[EntityResolverConnector].toInstance(mockEntityResolverConnector),
        inject.bind[EmailBounceQueueMonitorService].toInstance(mockEmailBounceQueueMonitorService),
        inject.bind[VerificationChaser].toInstance(mockVerificationChaser),
        inject.bind[Decrypter].toInstance(FakeApplicationCrypto)
      )
      .build()
  }
}
