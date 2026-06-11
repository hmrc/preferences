/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.config

import play.api.Configuration
import utils.SpecBase

import java.time.LocalTime
import scala.concurrent.duration.{ Duration, HOURS, MILLISECONDS, MINUTES, SECONDS }

class ScheduledJobConfigSpec extends SpecBase {

  "initialDelay" should {
    "return the correct duration" in new Setup {
      scheduledJobConfig.initialDelay mustBe Duration(1, SECONDS)
    }
  }

  "interval" should {
    "return the correct duration" in new Setup {
      scheduledJobConfig.interval mustBe Duration(1, SECONDS)
    }
  }

  "taskEnabled" should {
    "return the correct value" in new Setup {
      scheduledJobConfig.taskEnabled mustBe true
    }
  }

  "releaseLockAfter" should {
    "return the correct duration" in new Setup {
      scheduledJobConfig.releaseLockAfter mustBe Duration(1, MINUTES)
      scheduledJobConfigWithoutReleaseLock.releaseLockAfter mustBe Duration(1, HOURS)
    }
  }

  "activePeriodStart" should {
    "return the correct value" in new Setup {
      val hour = 8
      val minute = 0

      scheduledJobConfig.activePeriodStart mustBe LocalTime.of(hour, minute)
    }
  }

  "activePeriodStop" should {
    "return the correct value" in new Setup {
      val hour = 23
      val minute = 0

      scheduledJobConfig.activePeriodStop mustBe LocalTime.of(hour, minute)
    }
  }

  trait Setup {
    val propertyName = "effectivelyDisabled"

    val config: Configuration = Configuration(
      s"scheduling.$propertyName.taskEnabled"        -> true,
      s"scheduling.$propertyName.initialDelay"       -> "1second",
      s"scheduling.$propertyName.interval"           -> "1second",
      s"scheduling.$propertyName.releaseLockAfter"   -> "1minute",
      s"scheduling.$propertyName.activePeriod.start" -> "08:00",
      s"scheduling.$propertyName.activePeriod.stop"  -> "23:00"
    )

    val configWithoutReleaseLock: Configuration = Configuration(
      s"scheduling.$propertyName.taskEnabled"        -> true,
      s"scheduling.$propertyName.initialDelay"       -> "1second",
      s"scheduling.$propertyName.interval"           -> "1second",
      s"scheduling.$propertyName.activePeriod.start" -> "08:00",
      s"scheduling.$propertyName.activePeriod.stop"  -> "23:00"
    )

    val scheduledJobConfig = new ScheduledJobConfig(config, propertyName)
    val scheduledJobConfigWithoutReleaseLock = new ScheduledJobConfig(configWithoutReleaseLock, propertyName)
  }
}
