/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.jobs

import org.scalatestplus.play.PlaySpec
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.crypto.{ Decrypter, Encrypter }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.preferences.model.EmailVerificationLink
import utils.FakeApplicationCrypto
import utils.TestData.{ FIVE, FIVE_THOUSAND, HUNDRED, ONE_HUNDRED_TWENTY_THOUSAND, TEST_ID, TEST_LINK, TEST_TIME_INSTANT, THREE_HUNDRED_THOUSAND }

import scala.concurrent.duration.{ FiniteDuration, MILLISECONDS, MINUTES }

class RunModeBridgeSpec extends PlaySpec {
  "getEnabledFlag" should {
    "return correct value for the property key" in new Setup {
      runModeBridge.getEnabledFlag("sendVerificationReminders", "taskEnabled") mustBe false
    }
  }

  "getBatchSize" should {
    "return correct value for the property key" in new Setup {
      runModeBridge.getBatchSize("cleanupUnverifiedJob", "batchSize") mustBe FIVE_THOUSAND
    }
  }

  "getOptionalMillisForScheduling" should {
    "return correct value for the property key" in new Setup {
      runModeBridge.getOptionalMillisForScheduling("cleanupUnverifiedJob", "interval") mustBe Some(
        FiniteDuration(
          ONE_HUNDRED_TWENTY_THOUSAND,
          MILLISECONDS
        )
      )

      runModeBridge.getOptionalMillisForScheduling("cleanupUnverifiedJob", "unknown") mustBe empty
    }
  }

  "getLongMillis" should {
    "return correct value for the provided suffix" in new Setup {
      runModeBridge.getLongMillis("bounceQueue.batchSize") mustBe HUNDRED
    }
  }

  "taxPlatformSaPrefsRootUri" should {
    "return the correct value for the uri" in new Setup {
      runModeBridge.taxPlatformSaPrefsRootUri mustBe "http://localhost:9024"
    }
  }

  "externalVerificationLink" should {
    "return the correct value" in new Setup {
      runModeBridge.externalVerificationLink(
        EmailVerificationLink(_id = TEST_ID, linkSentTime = TEST_TIME_INSTANT)
      ) mustBe "http://localhost:9024/sa/print-preferences/verification/test_id"
    }
  }

  "getBounceBatchSize" should {
    "return the correct value for the bounce queue bacth size" in new Setup {
      runModeBridge.getBounceBatchSize mustBe Some(HUNDRED)
    }
  }

  trait Setup {
    val app: Application = new GuiceApplicationBuilder()
      .configure("metrics.enabled" -> false)
      .configure("auditing.enabled" -> false)
      .configure("metrics.graphite.enabled" -> false)
      .overrides(bind[Decrypter].toInstance(FakeApplicationCrypto))
      .build()

    implicit val hc: HeaderCarrier = HeaderCarrier()
    val runModeBridge: RunModeBridge = app.injector.instanceOf[RunModeBridge]
  }
}
