/*
 * Copyright 2023 HM Revenue & Customs
 *
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
