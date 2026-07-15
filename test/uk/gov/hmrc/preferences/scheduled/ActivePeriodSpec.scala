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

package uk.gov.hmrc.preferences.scheduled

import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import play.api.Configuration
import uk.gov.hmrc.preferences.config.ScheduledJobConfig
import uk.gov.hmrc.preferences.jobs.RunModeBridge
import uk.gov.hmrc.preferences.util.DateTimeExtensions.LocalDateTimeExtensions

import java.time.{ LocalDateTime, LocalTime }
import scala.concurrent.duration.{ Duration, MINUTES }

class ActivePeriodSpec extends PlaySpec {

  private def makeActivePeriod(executionTime: LocalDateTime): ActivePeriod = {
    val jobName = "anyJob"

    val config = Configuration(
      s"scheduling.$jobName.taskEnabled"        -> true,
      s"scheduling.$jobName.initialDelay"       -> "1second",
      s"scheduling.$jobName.interval"           -> "1second",
      s"scheduling.$jobName.releaseLockAfter"   -> "1minute",
      s"scheduling.$jobName.activePeriod.start" -> "08:00",
      s"scheduling.$jobName.activePeriod.stop"  -> "23:00"
    )

    ActivePeriod(
      jobConfig = ScheduledJobConfig(configuration = config, name = jobName),
      name = jobName,
      executionTime = executionTime
    )
  }

  "active period" should {

    "allow processing" in {
      val executionTime = LocalDateTime.now.withTime(8, 1, 0, 0) // 8:01
      val activePeriod = makeActivePeriod(executionTime)
      val isActive = activePeriod.isActive
      isActive must be(true)
    }

    "disallow processing" in {
      val executionTime = LocalDateTime.now.withTime(7, 59, 0, 0) // "07:59"
      val activePeriod = makeActivePeriod(executionTime)
      activePeriod.isActive must be(false)
    }

    "start of active period from executionTime should be 1 minute" in {
      val executionTime = LocalDateTime.now.withTime(7, 59, 0, 0) // "07:59"
      val activePeriod = makeActivePeriod(executionTime)
      activePeriod.startOfActivePeriod must be(Duration(1, MINUTES))
    }

    "be zero if start of active period is exactly the same as executionTime" in {
      val executionTime = LocalDateTime.now.withTime(8, 0, 0, 0) // "08:00"
      val activePeriod = makeActivePeriod(executionTime)
      activePeriod.startOfActivePeriod must be(Duration(0, MINUTES))
    }

    "start of active period from executionTime should be 23h 59m" in {
      val executionTime = LocalDateTime.now.withTime(8, 1, 0, 0) // "08:01"
      val activePeriod = makeActivePeriod(executionTime)
      val twentyThreeHoursFiftyNineMinutes = (23 * 60) + 59
      activePeriod.startOfActivePeriod.toMinutes must be(twentyThreeHoursFiftyNineMinutes)
    }

    "end of active period from executionTime should be 1 minute" in {
      val executionTime = LocalDateTime.now.withTime(22, 59, 0, 0) // "22:59"
      val activePeriod = makeActivePeriod(executionTime)
      activePeriod.endOfActivePeriod must be(Duration(1, MINUTES))
    }

    "be zero if end of active period is exactly the same as executionTime" in {
      val executionTime = LocalDateTime.now.withTime(23, 0, 0, 0) // "23:00"
      val activePeriod = makeActivePeriod(executionTime)
      activePeriod.endOfActivePeriod must be(Duration(0, MINUTES))
    }

    "end of active period from executionTime should be 23h 59m" in {
      val executionTime = LocalDateTime.now.withTime(23, 1, 0, 0) // "23:01"
      val activePeriod = makeActivePeriod(executionTime)
      val twentyThreeHoursFiftyNineMinutes = (23 * 60) + 59
      activePeriod.endOfActivePeriod.toMinutes must be(twentyThreeHoursFiftyNineMinutes)
    }
  }
}
