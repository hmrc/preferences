/*
 * Copyright 2026 HM Revenue & Customs
 *
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
