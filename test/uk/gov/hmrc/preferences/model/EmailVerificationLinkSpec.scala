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

import com.typesafe.config.ConfigFactory
import org.bson.types.ObjectId

import java.util.UUID
import org.scalatest.Inside._
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import uk.gov.hmrc.preferences.util.DateTimeExtensions.InstantExtensions
import uk.gov.hmrc.preferences.util.Dc

import java.time.{ Duration, Instant }
import scala.jdk.CollectionConverters._

class EmailVerificationLinkSpec extends PlaySpec {

  trait Setup {
    val uuid: String = UUID.randomUUID().toString
    val now: Instant = Dc.instantNow()
    val rowId: ObjectId = ObjectId.get()
    val emailLink: EmailVerificationLink = EmailVerificationLink(uuid, now)
    val configStr = "emailVerificationLink.timeout"
  }

  "The deserialiser" should {
    "handle a verification link without return link text and url" in {
      val source =
        """{"linkSentTime":{"$date": { "$numberLong": "1511256267441"}},"_id":"d5d44f01-fe42-4fb2-a6d1-a82c312b04e6"}""""
      val actual = Json.parse(source.stripMargin).as[EmailVerificationLink]
      val expectedDate = Instant.parse("2017-11-21T09:24:27.441Z")
      inside(actual) { case EmailVerificationLink(id, linkSentTime, returnText, returnUrl) =>
        id mustBe "d5d44f01-fe42-4fb2-a6d1-a82c312b04e6"
        linkSentTime.toLocalDate mustEqual expectedDate.toLocalDate
        returnText mustBe None
        returnUrl mustBe None
      }
    }

    "handle a verification link with return link text and url" in {
      val source =
        """{
          |"linkSentTime":{"$date": {"$numberLong": "1511256267441"}},
          |"_id":"d5d44f01-fe42-4fb2-a6d1-a82c312b04e6",
          |"returnText":"Return Text",
          |"returnUrl":"Return Url"
          |}"""".stripMargin
      val actual = Json.parse(source.stripMargin).as[EmailVerificationLink]
      val expectedDate = Instant.parse("2017-11-21T09:24:27.441Z")
      inside(actual) { case EmailVerificationLink(id, linkSentTime, returnText, returnUrl) =>
        id mustBe "d5d44f01-fe42-4fb2-a6d1-a82c312b04e6"
        linkSentTime.toLocalDate mustEqual expectedDate.toLocalDate
        returnText mustBe Some("Return Text")
        returnUrl mustBe Some("Return Url")
      }
    }
  }
  "The isValid method" should {

    "return false if the current time is after the expiry time" in new Setup {
      private val futureExpiredDate = now.plus(EmailVerificationLink.verificationLinkTimeout).plusHours(1)
      emailLink.isValid(futureExpiredDate) mustBe false
    }

    "return false if the current time is the same as the expiry time" in new Setup {
      private val exactExpiredDate = now.plus(EmailVerificationLink.verificationLinkTimeout)
      emailLink.isValid(exactExpiredDate) mustBe false
    }

    "return true if the current time is the before as the expiry time" in new Setup {
      private val futureValidDate = now.plus(EmailVerificationLink.verificationLinkTimeout).minusMillis(1)
      emailLink.isValid(futureValidDate) mustBe true
    }
  }

  "verificationLinkTimeout" should {

    "be 7 days" in new Setup {
      EmailVerificationLink.verificationLinkTimeout mustBe Duration.ofDays(7)
    }

    "be 5 days" in new Setup {
      val config = ConfigFactory.parseMap(Map(configStr -> "5 days").asJava)
      EmailVerificationLink.emailVerificationLinkTimeout(config) mustBe Duration.ofDays(5)
    }

    "be 1 hour" in new Setup {
      val config = ConfigFactory.parseMap(Map(configStr -> "1 hour").asJava)
      EmailVerificationLink.emailVerificationLinkTimeout(config) mustBe Duration.ofHours(1)
    }

    "be 10 minutes" in new Setup {
      val config = ConfigFactory.parseMap(Map(configStr -> "10 minutes").asJava)
      EmailVerificationLink.emailVerificationLinkTimeout(config) mustBe Duration.ofMinutes(10)
    }

    "be 15 seconds" in new Setup {
      val config = ConfigFactory.parseMap(Map(configStr -> "15 seconds").asJava)
      EmailVerificationLink.emailVerificationLinkTimeout(config) mustBe Duration.ofSeconds(15)
    }
  }
}
