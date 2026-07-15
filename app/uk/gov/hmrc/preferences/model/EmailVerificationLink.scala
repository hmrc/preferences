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

import com.typesafe.config.{ Config, ConfigFactory }

import java.util.UUID
import play.api.libs.json.{ Format, Json }
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.{ Duration, Instant }
import java.util.concurrent.TimeUnit

final case class EmailVerificationLink(
  _id: String = UUID.randomUUID().toString,
  linkSentTime: Instant,
  returnText: Option[String] = None,
  returnUrl: Option[String] = None
) {

  private lazy val expiryTime: Instant = linkSentTime.plus(EmailVerificationLink.verificationLinkTimeout)

  def isValid(now: Instant): Boolean = now.isBefore(expiryTime)

}
object EmailVerificationLink {
  implicit val dateTimeFormat: Format[Instant] = MongoJavatimeFormats.instantFormat
  implicit val evFormat: Format[EmailVerificationLink] = Json.format[EmailVerificationLink]

  def createOrUpdate(
    prefs: Preferences,
    newEmail: String,
    timeSource: () => Instant,
    returnText: Option[String] = None,
    returnUrl: Option[String] = None
  ): EmailVerificationLink = {
    val refreshedOldLink = for {
      pendingEmail     <- prefs.pendingEmail
      verificationLink <- pendingEmail.verificationLink
      if pendingEmail.email == newEmail && verificationLink.isValid(timeSource())
    } yield verificationLink.copy(linkSentTime = timeSource())

    refreshedOldLink.getOrElse(
      EmailVerificationLink(linkSentTime = timeSource(), returnText = returnText, returnUrl = returnUrl)
    )
  }

  val verificationLinkTimeout: Duration = emailVerificationLinkTimeout(ConfigFactory.load())

  lazy val configStr = "emailVerificationLink.timeout"

  def emailVerificationLinkTimeout(config: Config): Duration =
    Duration.ofMillis(
      if (config.hasPath(configStr)) config.getDuration(configStr, TimeUnit.MILLISECONDS)
      else Duration.ofDays(7).toMillis
    )
}
