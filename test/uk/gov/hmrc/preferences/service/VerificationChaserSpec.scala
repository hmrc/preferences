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

import com.codahale.metrics.SharedMetricRegistries
import org.bson.types.ObjectId
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.ArgumentMatchers.{ any, eq as eqTo }
import org.scalatest.Assertions.pending
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{ Application, inject }
import uk.gov.hmrc.http.{ GatewayTimeoutException, HeaderCarrier }
import uk.gov.hmrc.preferences.connector.EmailConnector
import uk.gov.hmrc.preferences.model.EmailVerificationLink
import uk.gov.hmrc.preferences.repository.{ PreferencesRepository, ReminderWorkItem }
import uk.gov.hmrc.preferences.util.Dc
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import play.api.inject.bind
import play.api.test.Helpers.*
import uk.gov.hmrc.crypto.{ Decrypter, Encrypter }
import utils.FakeApplicationCrypto

import java.time.Instant
import scala.annotation.unused
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class VerificationChaserSpec extends PlaySpec with MockitoSugar with GuiceOneAppPerTest with BeforeAndAfterEach {
  val verificationLinkPrefix: String = "some_prefix"
  val mockPreferencesRepository: PreferencesRepository = mock[PreferencesRepository]
  val mockEmailConnector: EmailConnector = mock[EmailConnector]

  override def afterEach(): Unit = {
    val _ = app.stop()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    SharedMetricRegistries.clear()
  }

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure("metrics.jvm" -> false)
      .configure("appName" -> "preferences")
      .configure("taxPlatformSaPrefsRootUri" -> verificationLinkPrefix)
      .overrides(
        inject.bind[PreferencesRepository].toInstance(mockPreferencesRepository),
        inject.bind[EmailConnector].toInstance(mockEmailConnector)
      )
      .overrides(bind[Decrypter].toInstance(FakeApplicationCrypto))
      .build()

  trait TestCase {
    val expectedEmail = "someone@mail.com"
    val rawExpectedVerificationLink = "some_link"
    val expectedVerificationLink: EmailVerificationLink =
      EmailVerificationLink(rawExpectedVerificationLink, Dc.instantNow())

    val chaser: VerificationChaser = app.injector.instanceOf[VerificationChaser]
  }

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "Verification mail chaser" should {

    "send the email and return processing successful" in new TestCase {

      def sendVerificationReminder(email: String, verificationLink: String, @unused daysAgo: String): Future[Unit] = {
        email must be(expectedEmail)
        verificationLink must include(rawExpectedVerificationLink)
        verificationLink must include(verificationLinkPrefix)
        Future(())
      }

      def setReminderSucceeded(reminder: ReminderWorkItem): Future[Boolean] = {
        reminder.email must be(expectedEmail)
        Future.successful(true)
      }
      def shouldNotFail(@unused reminder: ReminderWorkItem): Future[Boolean] =
        throw new IllegalStateException("Reminder should not fail!")

      // Given
      def processItem: (ReminderWorkItem) => Future[Boolean] =
        chaser.processItem(sendVerificationReminder, setReminderSucceeded, shouldNotFail)

      private val reminderWorkItem = ReminderWorkItem(ObjectId.get(), expectedEmail, expectedVerificationLink)

      // When
      private val result = processItem(reminderWorkItem)

      // Then
      result.futureValue must be(true)
    }

    "attempt to send the email and return processing failed" in new TestCase {
      private val expectedEmail = "someone@mail.com"
      private val rawExpectedVerificationLink = "some_link"
      private val expectedVerificationLink = EmailVerificationLink(rawExpectedVerificationLink, Dc.instantNow())

      def sendVerificationReminder(email: String, verificationLink: String, @unused daysAgo: String): Future[Unit] = {
        email must be(expectedEmail)
        verificationLink must include(rawExpectedVerificationLink)
        verificationLink must include(verificationLinkPrefix)
        Future.failed(new GatewayTimeoutException("Test"))
      }

      def shouldNotSucceed(@unused reminder: ReminderWorkItem): Future[Boolean] =
        throw new IllegalStateException("Reminder should not succeed!")

      private var failed = false
      def setReminderFailed(reminder: ReminderWorkItem): Future[Boolean] = {
        reminder.email must be(expectedEmail)
        failed = true
        Future.successful(true)
      }

      // Given
      def processItem: (ReminderWorkItem) => Future[Boolean] =
        chaser.processItem(sendVerificationReminder, shouldNotSucceed, setReminderFailed)
      private val reminderWorkItem = ReminderWorkItem(ObjectId.get(), expectedEmail, expectedVerificationLink)

      // When
      private val result = processItem(reminderWorkItem)

      // Then
      result.futureValue must be(false)
      failed must be(true)
    }
  }
}
