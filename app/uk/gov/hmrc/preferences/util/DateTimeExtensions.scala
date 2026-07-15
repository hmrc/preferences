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

package uk.gov.hmrc.preferences.util

import java.time.temporal.ChronoUnit
import java.time.{ Clock, Duration, Instant, LocalDate, LocalDateTime, Period, ZoneId, ZoneOffset }

object Dc {

  val zone = "UTC"
  val zoneId: ZoneId = ZoneId.of(zone)

  private var clock: Clock = Clock.system(ZoneId.of(zone))

  def instantNow(): Instant = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS)

  // For testing, set a different clock type
  def instantSet(newClock: Clock): Unit = clock = newClock
  // After you have finished with the current clock, set back to normal
  def instantReset(): Unit = clock = Clock.system(ZoneId.of(zone))
}

object DateTimeExtensions {

  implicit class InstantExtensions(val i: Instant) {
    def getMillis: Long = i.toEpochMilli
    def toLocalDate: LocalDate = LocalDate.ofInstant(i, ZoneId.of(Dc.zone))

    def plusDays(d: Long): Instant = i.plus(Duration.ofDays(d))
    def plusHours(h: Long): Instant = i.plus(Duration.ofHours(h))
    def plusMinutes(m: Long): Instant = i.plus(Duration.ofMinutes(m))

    def minusDays(d: Long): Instant = i.minus(Duration.ofDays(d))
    def minusHours(h: Long): Instant = i.minus(Duration.ofHours(h))
    def minusMinutes(m: Long): Instant = i.minus(Duration.ofMinutes(m))
    def minusMonths(m: Int): Instant = minus(Period.ofMonths(m))
    def minusYears(y: Int): Instant = minus(Period.ofYears(y))

    private def minus(period: Period): Instant =
      i.toLocalDate
        .atStartOfDay()
        .minus(period)
        .toInstant(ZoneOffset.UTC)

    def withTimeAtStartOfDay(): Instant =
      i.toLocalDate.atStartOfDay().toInstant(ZoneOffset.UTC)

    def withHourOfDay(h: Int): Instant = i.atZone(ZoneOffset.UTC).withHour(h).toInstant
  }

  implicit class LocalDateTimeExtensions(val ldt: LocalDateTime) {

    def withTime(hourOfDay: Int, minuteOfHour: Int, secondOfMinute: Int, millisOfSecond: Int): LocalDateTime =
      ldt
        .withHour(hourOfDay)
        .withMinute(minuteOfHour)
        .withSecond(secondOfMinute)
        .withNano(millisOfSecond * 1000000)
  }

  implicit class LocalDateExtensions(val ld: LocalDate) {
    def toInstant: Instant = ld.atStartOfDay().toInstant(ZoneOffset.UTC)
  }

}
