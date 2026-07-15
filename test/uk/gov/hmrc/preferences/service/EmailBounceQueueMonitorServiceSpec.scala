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

package uk.gov.hmrc.preferences.service

import org.bson.types.ObjectId
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.{ Application, inject }
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.*
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.preferences.connector.Bounce
import uk.gov.hmrc.preferences.repository.{ PreferenceUpdated, PreferencesRepository }
import uk.gov.hmrc.preferences.util.Dc
import utils.TestData.{ TEST_EMAIL, TEST_EMAIL_VERIFICATION_LINK, TEST_ENTITY_ID, TEST_TIME_INSTANT }
import org.mockito.ArgumentMatchers.{ any, same }
import org.mockito.Mockito.{ never, verify, when }
import uk.gov.hmrc.auth.core.AffinityGroup.Individual
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.ConfidenceLevel.L200
import uk.gov.hmrc.crypto.{ Decrypter, Encrypter }
import uk.gov.hmrc.preferences.Auditable
import uk.gov.hmrc.preferences.model.TermsAndConditions.Accepted
import uk.gov.hmrc.preferences.model.{ PendingEmailAddress, Preferences, TermsAndConditions, UserType }
import utils.FakeApplicationCrypto

import javax.inject.Named
import scala.concurrent.{ ExecutionContext, Future }

class EmailBounceQueueMonitorServiceSpec extends PlaySpec with MockitoSugar with ScalaFutures {

  "markAsBounced" should {

    "not update the preferences" when {
      "no preferences found for the given email" in new Setup {
        when(mockPreferencesRepository.findByEmail(TEST_EMAIL)).thenReturn(Future.successful(Seq()))

        val result: Unit = await(service.markAsBounced(bounce))

        result mustBe ()
      }

      "preferences found but etmpUpdateFlag is false" in new Setup {
        val preferences: Seq[Preferences] =
          Seq(
            Preferences(
              entityId = TEST_ENTITY_ID,
              termsAndConditions = TermsAndConditions(Accepted(TEST_TIME_INSTANT)),
              pendingEmail =
                Some(PendingEmailAddress(email = TEST_EMAIL, verificationLink = Some(TEST_EMAIL_VERIFICATION_LINK))),
              userType = Some(UserType(Some(Individual), Some(L200)))
            )
          )

        when(mockPreferencesRepository.findByEmail(TEST_EMAIL)).thenReturn(Future.successful(preferences))

        val result: Unit = await(service.markAsBounced(bounce))

        result mustBe ()
      }
    }

    "update the preferences" when {
      "etmpUpdateFlag is true" in new Setup {
        val emailQueueMonitorService = new EmailBounceQueueMonitorService(
          etmpService = mockEtmpService,
          auditable = mockAuditable,
          individualPreferencesRepository = mockPreferencesRepository,
          pcnService = mockPcnService,
          etmpUpdateFlag = true
        )

        val preferences: Seq[Preferences] =
          Seq(
            Preferences(
              entityId = TEST_ENTITY_ID,
              termsAndConditions = TermsAndConditions(Accepted(TEST_TIME_INSTANT)),
              pendingEmail =
                Some(PendingEmailAddress(email = TEST_EMAIL, verificationLink = Some(TEST_EMAIL_VERIFICATION_LINK))),
              userType = Some(UserType(Some(Individual), Some(L200)))
            )
          )

        when(mockPreferencesRepository.findByEmail(TEST_EMAIL)).thenReturn(Future.successful(preferences))
        when(mockEtmpService.checkAndUpdateETMP(any, any, any)(any)).thenReturn(Future.successful(ETMPUpdateSuccess))

        when(mockPreferencesRepository.addBouncesAndClearVerificationLink(any, any, any, any)(any))
          .thenReturn(Future.successful(PreferenceUpdated))
        when(mockPcnService.notifyPreferencesChanged(any, any, any, any, any)(any, any))
          .thenReturn(Future.successful(()))

        val result: Unit = await(emailQueueMonitorService.markAsBounced(bounce))

        result mustBe ()
      }
    }
  }

  trait Setup {
    val app: Application = new GuiceApplicationBuilder()
      .configure("metrics.enabled" -> false)
      .configure("auditing.enabled" -> false)
      .configure("metrics.graphite.enabled" -> false)
      .overrides(
        inject.bind[Decrypter].toInstance(FakeApplicationCrypto)
      )
      .build()

    implicit val hc: HeaderCarrier = HeaderCarrier()
    implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

    val mockPreferencesRepository: PreferencesRepository = mock[PreferencesRepository]
    val mockPcnService: PreferencesChangedNotifierService = mock[PreferencesChangedNotifierService]
    val mockEtmpService: ETMPService = mock[ETMPService]
    val mockAuditable: Auditable = mock[Auditable]

    val bounce: Bounce = Bounce(TEST_EMAIL, TEST_TIME_INSTANT, None)

    val service: EmailBounceQueueMonitorService = app.injector.instanceOf[EmailBounceQueueMonitorService]
  }
}
