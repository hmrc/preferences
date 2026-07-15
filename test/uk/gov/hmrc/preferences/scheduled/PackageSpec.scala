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
