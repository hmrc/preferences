/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.connector

import javax.inject.{ Inject, Singleton }
import play.api.libs.json.{ Format, Json, OFormat }
import play.api.libs.ws.WSBodyWritables.writeableOf_JsValue
import uk.gov.hmrc.http.{ HeaderCarrier, HttpException, HttpResponse }
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.preferences.util.DateFormats

import java.net.URL
import java.time.Instant
import java.time.format.DateTimeFormatter
import scala.concurrent.{ ExecutionContext, Future }

object SendTemplatedEmailRequest {
  implicit val formats: OFormat[SendTemplatedEmailRequest] = Json.format[SendTemplatedEmailRequest]
}

case class SendTemplatedEmailRequest(
  to: List[String],
  templateId: String,
  parameters: Map[String, String],
  force: Boolean = false
)

object Bounce {
  implicit val formats: OFormat[Bounce] = {
    implicit val dateTimeFormats: Format[Instant] = DateFormats.instantFormats
    Json.format[Bounce]
  }
}

case class Bounce(
  emailAddress: String,
  detected: Instant,
  code: Option[Int],
  emailSource: Option[String] = None,
  mailgunEventId: Option[String] = None,
  formType: Option[String] = None,
  nino: Option[String] = None
)

object EmailTemplateId {
  val verifyEmailAddress = "verifyEmailAddress"
  val optOutOfDigital = "digitalOptOutConfirmation"
  val changeOfEmailAddress = "changeOfEmailAddress"
  val changeOfEmailAddressNewAddress = "changeOfEmailAddressNewAddress"
  val verificationReminder = "verificationReminder"
  val dateFormatter = DateTimeFormatter.ofPattern("dd MMMM YYYY")
}

@Singleton
class EmailConnector @Inject() (httpClient: HttpClientV2, servicesConfig: ServicesConfig)(implicit
  ec: ExecutionContext
) {

  import uk.gov.hmrc.preferences.connector.EmailTemplateId._

  def emailBaseUrl: String = servicesConfig.baseUrl("email")

  def url(path: String): URL = new URL(s"$emailBaseUrl$path")

  def sendDigitalOptInEmailVerification(to: String, verificationLink: String, force: Boolean)(implicit
    hc: HeaderCarrier
  ): Future[Unit] =
    sendTemplatedEmail(verifyEmailAddress, to, force = force, "verificationLink" -> verificationLink)

  def sendEmailChangedNotification(to: String)(implicit hc: HeaderCarrier): Future[Unit] =
    sendTemplatedEmail(changeOfEmailAddress, to, force = false)

  def sendDigitalOptOutEmail(to: String)(implicit hc: HeaderCarrier): Future[Unit] =
    sendTemplatedEmail(optOutOfDigital, to, force = false)

  def sendChangedEmailAddressVerification(to: String, verificationLink: String)(implicit
    hc: HeaderCarrier
  ): Future[Unit] =
    sendTemplatedEmail(changeOfEmailAddressNewAddress, to, force = false, "verificationLink" -> verificationLink)

  def sendVerificationReminder(to: String, verificationLink: String, daysAgo: String)(implicit
    hc: HeaderCarrier
  ): Future[Unit] =
    sendTemplatedEmail(
      verificationReminder,
      to,
      force = false,
      "verificationLink" -> verificationLink,
      "daysAgo"          -> daysAgo
    )

  protected def sendTemplatedEmail(
    templateId: String,
    toAddress: String,
    force: Boolean,
    parameters: (String, String)*
  )(implicit hc: HeaderCarrier): Future[Unit] = {

    val request = SendTemplatedEmailRequest(
      to = List(toAddress),
      templateId = templateId,
      parameters = Map(parameters: _*),
      force = force
    )

    httpClient
      .post(url("/hmrc/email"))
      .withBody(Json.toJson(request))
      .execute[HttpResponse] map {
      _.status match {
        case 202 => // Email queued
        case s   => throw new HttpException(s"Unexpected response ($s) from email service", s)
      }
    }
  }

}
