/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.scheduled

import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.preferences.util.DateTimeExtensions.InstantExtensions
import uk.gov.hmrc.preferences.util.Dc

class PackageSpec extends PlaySpec {

  "calculate delay" should {

    "supply the duration when test time is exactly the same as delay" in {
      val delayInHours = 7
      val todaysTestDateTime = Dc.instantNow().withTimeAtStartOfDay().withHourOfDay(delayInHours)
      val timeFn = () => todaysTestDateTime
      val f = calcDelay(Some(delayInHours), timeFn)
      f.toHours must be(24)
    }

    "supply duration when test time is one hour earlier than delay" in {
      val delayInHours = 7
      val todaysTestDateTime = Dc.instantNow().withTimeAtStartOfDay().withHourOfDay(6)
      val timeFn = () => todaysTestDateTime
      val f = calcDelay(Some(delayInHours), timeFn)
      f.toHours must be(1)
    }

    "supply duration when test time is one hour later than delay" in {
      val delayInHours = 7
      val todaysTestDateTime = Dc.instantNow().withTimeAtStartOfDay().withHourOfDay(8)
      val timeFn = () => todaysTestDateTime
      val f = calcDelay(Some(delayInHours), timeFn)
      f.toHours must be(23)
    }

    "supply duration when no delay specified should provide 1 minute default" in {
      val todaysTestDateTime = Dc.instantNow().withTimeAtStartOfDay().withHourOfDay(8)
      val timeFn = () => todaysTestDateTime
      val f = calcDelay(None, timeFn)
      f.toMinutes must be(1)
    }

  }
}
