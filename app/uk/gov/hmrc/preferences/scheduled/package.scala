/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences

import uk.gov.hmrc.preferences.model.{ EmailEvent, EmailEventType, Preferences }

import java.time.{ Duration, Instant, LocalDate, LocalDateTime, ZoneId }
import scala.concurrent.duration.FiniteDuration
import scala.jdk.DurationConverters.JavaDurationOps

package object scheduled {
  private[scheduled] def calcDelay(
    startHourOfDay: Option[Int],
    now: () => Instant,
    defaultDelay: Int = 1
  ): FiniteDuration =
    startHourOfDay
      .map { h =>
        val currentDate = LocalDate.ofInstant(now(), ZoneId.of("UTC"))
        val currentDateTime = LocalDateTime.ofInstant(now(), ZoneId.of("UTC"))

        val startOfToday = currentDate.atStartOfDay()
        val offsetFromStartOfToday = startOfToday.plusHours(h)

        if (currentDateTime.isBefore(offsetFromStartOfToday)) {
          Duration.between(currentDateTime, offsetFromStartOfToday)
        } else {
          Duration.between(currentDateTime, offsetFromStartOfToday.plusDays(1))
        }
      }
      .getOrElse(Duration.ofMinutes(defaultDelay))
      .toScala

  def getEmailEvent(p: Preferences): EmailEvent =
    new CurrentTime {}.withCurrentTime { time =>
      EmailEvent(
        p.entityId,
        EmailEventType.SystemExpiredPendingEmailRemoval,
        p.pendingEmail.fold("no-email@none.com")(_.email),
        Some(p.isPaperless),
        time
      )
    }
}
