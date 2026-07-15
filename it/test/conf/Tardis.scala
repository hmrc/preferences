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

package conf

import uk.gov.hmrc.preferences.util.DateTimeExtensions.{ InstantExtensions, LocalDateExtensions }
import uk.gov.hmrc.preferences.util.Dc

import java.time.{ Clock, Instant, LocalDate }
import java.time.format.DateTimeFormatter
import scala.util.Try

trait Tardis {

  def beforeAnyUpdatesCouldHaveOccurred = LocalDate.parse("2012-12-25").atStartOfDay().plusHours(12)

  private lazy val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

  private def dateTimeFromString(dateString: String): Instant = LocalDate.parse(dateString, formatter).toInstant

  val preSA316 = dateTimeFromString("04/11/2015")

  def preSA316Instant = preSA316

  def oneDayAgo = Dc.instantNow().minusDays(1)

  def twoDaysAgo = oneDayAgo.minusDays(1)

  def oneHourAgo = Dc.instantNow().minusHours(1)

  def twoHoursAgo = Dc.instantNow().minusHours(2)

  def daysAgo(days: Int) = Dc.instantNow().minusDays(days)

  def atTime[T](when: Instant)(toChangeThePast: => T): T = {
    Dc.instantSet(Clock.fixed(when, Dc.zoneId))
    val result = Try(toChangeThePast)
    Dc.instantReset()
    result.get
  }
}
