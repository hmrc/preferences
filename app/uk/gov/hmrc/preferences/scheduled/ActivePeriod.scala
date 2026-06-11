/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.scheduled

import play.api.{ Configuration, Logger }
import uk.gov.hmrc.preferences.config.ScheduledJobConfig
import java.time.{ Duration, LocalDateTime, LocalTime }
import scala.concurrent.duration.{ DAYS, FiniteDuration }
import scala.jdk.DurationConverters.JavaDurationOps

class ActivePeriod(jobConfig: ScheduledJobConfig, name: String, executionTime: LocalDateTime = LocalDateTime.now()) {
  private val logger: Logger = Logger(getClass)

  private def startAt: LocalTime =
    jobConfig.activePeriodStart

  private def stopAt: LocalTime =
    jobConfig.activePeriodStop

  private def now: LocalDateTime = executionTime

  def isActive: Boolean =
    now.toLocalTime.isAfter(startAt) && now.toLocalTime.isBefore(stopAt)

  val DelayDuration: FiniteDuration = FiniteDuration(1, DAYS)

  private def delayUntilNext(targetTime: LocalTime, label: String): FiniteDuration = {
    val now = this.now
    val targetToday = targetTime.atDate(now.toLocalDate)
    val nextOccurrence = if (targetToday.isBefore(now)) {
      targetToday.plusDays(1)
    } else {
      targetToday
    }
    val delay = Duration.between(now, nextOccurrence)

    logger.warn(
      s"$name calculating $label from 'now': 'now': $now, 'target': $nextOccurrence, " +
        s"'delay': ${prettyPrint(delay)}"
    )

    delay.toScala
  }

  def startOfActivePeriod: FiniteDuration = delayUntilNext(startAt, "start of active period")

  def endOfActivePeriod: FiniteDuration = delayUntilNext(stopAt, "end of active period")

  private def prettyPrint(duration: Duration): String = s"${duration.toHours} " +
    s"hours ${duration.toMinutes % 60} minutes"
}
