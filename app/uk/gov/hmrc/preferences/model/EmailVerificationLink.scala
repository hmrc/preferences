/*
 * Copyright 2023 HM Revenue & Customs
 *
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
