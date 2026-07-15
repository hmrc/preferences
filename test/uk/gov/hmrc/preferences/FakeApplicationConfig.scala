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

package uk.gov.hmrc.preferences

trait FakeApplicationConfig {
  protected def fakeAppAdditionalConfig(env: String = "Dev"): Map[String, String] = Map(
    s"$env.scheduling.emailBounceQueueMonitor.initialDelay"         -> "2 hours",
    s"$env.scheduling.emailBounceQueueMonitor.interval"             -> "5 seconds",
    s"$env.scheduling.sendVerificationReminders.initialDelay"       -> "2 hours",
    s"$env.scheduling.sendVerificationReminders.interval"           -> "5 seconds",
    s"$env.scheduling.sendVerificationReminders.activePeriod.start" -> "00:00", // here to allow tests run all the time
    s"$env.scheduling.sendVerificationReminders.activePeriod.stop"  -> "23:59", // here to allow tests run all the time
    s"play.http.router"                                             -> "testOnlyDoNotUseInAppConf.Routes"
  )
}
