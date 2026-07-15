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
