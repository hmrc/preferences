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

package uk.gov.hmrc.preferences.model

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json

import java.time.Instant

class PendingEmailAddressSpec extends PlaySpec {

  "PendingEmailAddress" should {
    val date = Instant.parse("2015-05-13T00:00:00Z")
    val pendingEmailJson = Json.parse(s"""{
                                         |  "verificationLink" : {
                                         |      "linkSentTime" : {"$$date": { "$$numberLong": "${date.toEpochMilli}"}},
                                         |      "_id" : "b827c56c-c411-4dca-aa31-a8c766fa8faa"
                                         |  },
                                         |  "email" : "test@test.com",
                                         |  "lowercaseEmail" : "test@test.com",
                                         |  "reminder" : {
                                         |      "status" : "todo",
                                         |      "updatedAt":  {"$$date": { "$$numberLong": "${date.toEpochMilli}"}}
                                         |  },
                                         |  "language": "cy"
                                         |}
       """.stripMargin)
    """ be successfully deserialized and serialize from/to json""" in {
      val pendingAddress = pendingEmailJson.as[PendingEmailAddress]
      Json.toJson[PendingEmailAddress](pendingAddress) must be(pendingEmailJson)
    }
  }
}

class EmailAddressSpec extends PlaySpec {
  """PendingEmailAddress should be successfully deserialized from string """ should {
    val date = Instant.parse("2015-05-13T00:00:00Z")
    val emailAddressJson = Json.parse(s"""
                                         |{
                                         |  "email" : "bob@example.com",
                                         |  "lowercaseEmail" : "bob@example.com",
                                         |  "verifiedOn" : {"$$date": { "$$numberLong": "${date.toEpochMilli}"}},
                                         |  "bounceCount": 0,
                                         |  "verifiedWithLink" : {
                                         |    "linkSentTime" : {"$$date": { "$$numberLong": "${date.toEpochMilli}"}},
                                         |    "_id" : "id"
                                         |  },
                                         |  "language": "cy"
                                         |}
    """.stripMargin)
    """ be successfully deserialized and serialize from/to json""" in {
      val emailAddress = emailAddressJson.as[EmailAddress]
      Json.toJson[EmailAddress](emailAddress) must be(emailAddressJson)
    }
  }
}
