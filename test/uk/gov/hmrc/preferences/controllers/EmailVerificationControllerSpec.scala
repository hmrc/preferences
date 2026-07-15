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

package uk.gov.hmrc.preferences.controllers

import org.mockito.Mockito.{ doNothing, when }
import play.api.libs.json.Json
import play.api.mvc.{ AnyContentAsEmpty, Result }
import play.api.test.FakeRequest
import play.api.test.Helpers.PUT
import play.api.{ Application, inject }
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.preferences.Auditable
import uk.gov.hmrc.preferences.connector.{ CitizenDetailsConnector, EntityResolverConnector, MessageConnector, TaxpayerConnector }
import uk.gov.hmrc.preferences.repository.PreferencesRepository
import uk.gov.hmrc.preferences.service.{ ETMPService, PreferencesChangedNotifierService }
import uk.gov.hmrc.preferences.templates.TemplateHelper
import utils.SpecBase
import play.api.test.Helpers.*
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.preferences.controllers.model.EmailToken
import org.mockito.ArgumentMatchers.any
import uk.gov.hmrc.preferences.model.Language.English
import uk.gov.hmrc.preferences.model.{ EmailVerificationLink, PendingEmailAddress, Preferences, TaxId }
import utils.TestData.{ TEST_EMAIL_ADDRESS, TEST_EMAIL_VERIFICATION_LINK, TEST_PREFERENCES, TEST_TAX_ID }
import uk.gov.hmrc.crypto.{ Decrypter, Encrypter }
import utils.FakeApplicationCrypto
import scala.concurrent.{ ExecutionContext, Future }

class EmailVerificationControllerSpec extends SpecBase {

  "verifyEmail" should {

    "return BadRequest if the email token is invalid" in new Setup {
      val request: FakeRequest[AnyContentAsEmpty.type] =
        fakeRequest(PUT, routes.EmailVerificationController.verifyEmail().url)

      val result: Future[Result] = route(app, request.withBody(Json.toJson(invalidEmailToken))).value

      status(result) mustBe BAD_REQUEST
    }

    "return CONFLICT" when {
      "email is valid and preferences is fetched for the token" in new Setup {

        when(mockPreferencesRepository.findByVerificationToken(any)(any))
          .thenReturn(
            Future.successful(
              Some(
                TEST_PREFERENCES
                  .copy(email =
                    Some(
                      TEST_EMAIL_ADDRESS.copy(verifiedWithLink =
                        Some(TEST_EMAIL_VERIFICATION_LINK.copy(_id = wellFormattedToken))
                      )
                    )
                  )
              )
            )
          )

        val request: FakeRequest[AnyContentAsEmpty.type] =
          fakeRequest(PUT, routes.EmailVerificationController.verifyEmail().url)

        val result: Future[Result] = route(app, request.withBody(Json.toJson(validEmailToken))).value

        status(result) mustBe CONFLICT
      }

      "email is valid and no preferences is fetched for the token" in new Setup {
        when(mockPreferencesRepository.findByVerificationToken(any)(any))
          .thenReturn(Future.successful(None))

        val request: FakeRequest[AnyContentAsEmpty.type] =
          fakeRequest(PUT, routes.EmailVerificationController.verifyEmail().url)

        val result: Future[Result] = route(app, request.withBody(Json.toJson(validEmailToken))).value

        status(result) mustBe CONFLICT
      }
    }

    "return OK" when {

      "email is valid and preferences is fetched for the token and there is no pending email" in new Setup {
        when(mockPreferencesRepository.findByVerificationToken(any)(any))
          .thenReturn(
            Future.successful(
              Some(
                TEST_PREFERENCES
                  .copy(
                    email = Some(
                      TEST_EMAIL_ADDRESS.copy(verifiedWithLink =
                        Some(TEST_EMAIL_VERIFICATION_LINK.copy(_id = wellFormattedToken))
                      )
                    ),
                    pendingEmail = None
                  )
              )
            )
          )

        val request: FakeRequest[AnyContentAsEmpty.type] =
          fakeRequest(PUT, routes.EmailVerificationController.verifyEmail().url)

        val result: Future[Result] = route(app, request.withBody(Json.toJson(validEmailToken))).value

        status(result) mustBe OK
      }
    }

    "return NoContent and save the verified mail" when {
      "email token is valid and matching preferences has pending email with language value " in new Setup {
        val updatedVerificationLink: EmailVerificationLink = TEST_EMAIL_VERIFICATION_LINK.copy(_id = wellFormattedToken)

        val pendingEmailValue: Option[PendingEmailAddress] = Some(
          TEST_PREFERENCES.pendingEmail.get.copy(
            verificationLink = Some(updatedVerificationLink),
            language = Some(English)
          )
        )

        val updatedPreferences: Preferences = TEST_PREFERENCES.copy(email = None, pendingEmail = pendingEmailValue)

        when(mockPreferencesRepository.findByVerificationToken(any)(any))
          .thenReturn(Future.successful(Some(updatedPreferences)))

        when(mockPreferencesRepository.markEmailVerified(any, any, any, any)(any)).thenReturn(Future.successful(()))
        when(mockPCNService.notifyPreferencesChanged(any, any, any, any, any)(any, any))
          .thenReturn(Future.successful(()))

        doNothing().when(mockAuditable).sendDataEvent(any, any, any, any)(any, any)

        /*when(mockPreferencesRepository.findByVerificationToken(any)(any))
          .thenReturn(Future.successful(Some(TEST_PREFERENCES)))*/

        when(mockEntityResolverConnector.getTaxId(any)(any))
          .thenReturn(Future.successful(TEST_TAX_ID))

        val request: FakeRequest[AnyContentAsEmpty.type] =
          fakeRequest(PUT, routes.EmailVerificationController.verifyEmail().url)

        val result: Future[Result] = route(app, request.withBody(Json.toJson(validEmailToken))).value

        status(result) mustBe NO_CONTENT
      }
    }

    "return IllegalStateException" when {
      "email token is valid but there is no matching preferences" in new Setup {
        intercept[IllegalStateException] {

          when(mockPreferencesRepository.findByVerificationToken(any)(any))
            .thenReturn(Future.successful(Some(TEST_PREFERENCES.copy(email = None, pendingEmail = None))))

          val request: FakeRequest[AnyContentAsEmpty.type] =
            fakeRequest(PUT, routes.EmailVerificationController.verifyEmail().url)

          await(route(app, request.withBody(Json.toJson(validEmailToken))).value)
        }
      }
    }
  }

  trait Setup {
    val mockPreferencesRepository: PreferencesRepository = mock[PreferencesRepository]
    val mockEntityResolverConnector: EntityResolverConnector = mock[EntityResolverConnector]
    val mockTaxpayerConnector: TaxpayerConnector = mock[TaxpayerConnector]
    val mockCitizenDetailsConnector: CitizenDetailsConnector = mock[CitizenDetailsConnector]
    val mockMessageConnector: MessageConnector = mock[MessageConnector]
    val mockTemplateHelper: TemplateHelper = mock[TemplateHelper]
    val mockAuditConnector: AuditConnector = mock[AuditConnector]
    val mockEtmpService: ETMPService = mock[ETMPService]
    val mockPCNService: PreferencesChangedNotifierService = mock[PreferencesChangedNotifierService]
    val mockAuditable: Auditable = mock[Auditable]

    val wellFormattedToken: String = "12345678-abcd-4abc-abcd-123456789012"
    private val invalidToken: String = "test_token"

    val invalidEmailToken: EmailToken = EmailToken(invalidToken)
    val validEmailToken: EmailToken = EmailToken(wellFormattedToken)

    val app: Application = applicationBuilder
      .overrides(
        inject.bind[PreferencesRepository].toInstance(mockPreferencesRepository),
        inject.bind[EntityResolverConnector].toInstance(mockEntityResolverConnector),
        inject.bind[CitizenDetailsConnector].toInstance(mockCitizenDetailsConnector),
        inject.bind[TaxpayerConnector].toInstance(mockTaxpayerConnector),
        inject.bind[MessageConnector].toInstance(mockMessageConnector),
        inject.bind[TemplateHelper].toInstance(mockTemplateHelper),
        inject.bind[AuditConnector].toInstance(mockAuditConnector),
        inject.bind[ETMPService].toInstance(mockEtmpService),
        inject.bind[PreferencesChangedNotifierService].toInstance(mockPCNService),
        inject.bind[Auditable].toInstance(mockAuditable),
        inject.bind[Decrypter].toInstance(FakeApplicationCrypto)
      )
      .build()
  }
}
