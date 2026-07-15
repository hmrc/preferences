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

package uk.gov.hmrc.preferences.controllers

import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.preferences.model.{ EmailBounce, EmailPreference }
import uk.gov.hmrc.preferences.util.Dc

class EmailPreferenceSpec extends PlaySpec {

  "errorCodeToMessage" should {
    def emailPrefs(errorCode: Option[Int]) =
      EmailPreference.create("a@a.pl", "status", EmailBounce(errorCode, Dc.instantNow()))

    "convert 421 to Temporary failure - service unavailable" in {
      emailPrefs(Some(421)).message.get mustBe "Temporary failure - service unavailable"
    }

    "convert 450 to Temporary failure - service unavailable" in {
      emailPrefs(Some(450)).message.get mustBe "Temporary failure - mailbox unavailable"
    }

    "convert 451 to Temporary failure - service unavailable" in {
      emailPrefs(Some(451)).message.get mustBe "Temporary failure - local error in processing"
    }

    "convert 452 to Temporary failure - service unavailable" in {
      emailPrefs(Some(452)).message.get mustBe "Temporary failure - insufficient system storage"
    }

    "convert several 5xx to Temporary failure - service unavailable" in {
      Seq(500, 501, 502, 503, 504, 521, 530, 550, 551, 553, 554).foreach { code =>
        emailPrefs(
          Some(code)
        ).message.get mustBe "your email service is unavailable - you might want to change the email address reminders are sent to."
      }
    }
    "convert 552 to Temporary failure - service unavailable" in {
      emailPrefs(Some(552)).message.get mustBe "your inbox is full."
    }

    "convert No code to Permanent failure - requested Mailbox unavailable" in {
      emailPrefs(None).message.get mustBe "Permanent failure - requested Mailbox unavailable"
    }

    "convert unknown code to Permanent failure - requested Mailbox unavailable" in {
      emailPrefs(Some(0)).message.get mustBe "Permanent failure - requested Mailbox unavailable"
    }
  }
}
