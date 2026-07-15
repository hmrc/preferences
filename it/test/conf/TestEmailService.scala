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

import javax.inject.{ Inject, Singleton }
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json.toJson
import play.api.libs.json.{ Json, OFormat }
import play.api.libs.ws.WSClient
import play.api.libs.ws.writeableOf_JsValue
import play.api.libs.ws.writeableOf_String
import stubs.{ WireMockStubs, WireMockUtil }
import uk.gov.hmrc.preferences.util.Dc
import scala.concurrent.{ Await, Future }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

trait FindEmails {
  val emails: List[EmailContent]
  def withSubjectContaining(filters: Seq[String]): List[EmailContent]
  def verificationLinks(): Seq[String]
  def changedEmailNotification(): List[EmailContent]
  def verificationReminders(): List[EmailContent]
  def optOutNotifications(): List[EmailContent]
}

@Singleton
class TestEmailService @Inject() (ws: WSClient, emailService: EmailService)
    extends PlaySpec with ScalaFutures with WireMockUtil with WireMockStubs {
  val DigitalContactStubHost = "http://localhost:8185"
  def reset() = Await.result(ws.url(s"$DigitalContactStubHost/reset").get(), 2.seconds)

  def findEmailsForMock(recipientEmail: String) = new FindEmails {
    val emails: List[EmailContent] = sentEmailMock(recipientEmail)

    def withSubjectContaining(filters: Seq[String]): List[EmailContent] = fetchEmailWithSubject(emails, filters)

    def verificationLinks(): Seq[String] = extractVerificationLinksFrom(emails)

    def changedEmailNotification(): List[EmailContent] = fetchEmailWithSubject(emails, Seq("change of email address"))

    def verificationReminders(): List[EmailContent] = fetchEmailWithSubject(emails, Seq("complete the sign-up process"))

    def optOutNotifications(): List[EmailContent] = fetchEmailWithSubject(emails, Seq("HMRC notifications by post"))
  }

  def findEmailsFor(recipientEmail: String) = new FindEmails {

    val emails: List[EmailContent] = Await.result(sentEmails(recipientEmail), 15.seconds)

    def withSubjectContaining(filters: Seq[String]): List[EmailContent] = fetchEmailWithSubject(emails, filters)

    def verificationLinks(): Seq[String] = extractVerificationLinksFrom(emails)

    def changedEmailNotification(): List[EmailContent] = fetchEmailWithSubject(emails, Seq("change of email address"))

    def verificationReminders(): List[EmailContent] = fetchEmailWithSubject(emails, Seq("complete the sign-up process"))

    def optOutNotifications(): List[EmailContent] = fetchEmailWithSubject(emails, Seq("HMRC notifications by post"))
  }

  def markAsBounced(email: String, code: Int): Int = {
    val json = toJson(Map("events" -> Seq(BounceDetails(Dc.instantNow().toString, code, email))))
    ws.url(s"$DigitalContactStubHost/v2/exampleDomain/events").post(json).futureValue.status
  }

  implicit val formatSentMail: OFormat[EmailContent] = Json.format[EmailContent]

  implicit val formatBounceDetails: OFormat[BounceDetails] = Json.format[BounceDetails]

  case class BounceDetails(time: String, code: Int, address: String, severity: String = "permanent")

  private def sentEmails(recipientEmail: String): Future[List[EmailContent]] =
    for {
      _      <- emailService.`test-only/hmrc/email-admin/process-email-queue`.post("")
      emails <- ws.url(s"$DigitalContactStubHost/digital-contact-stub/imi/messages/email/$recipientEmail").get()
    } yield emails.json.as[List[EmailContent]]

  private def sentEmailMock(recipientEmail: String): List[EmailContent] = {
    val jsonResponse =
      s"""[{"channel":"email","from":"noreply@exampleDomain","to":[{"email":["$recipientEmail"],"correlationId":"1f57ac3d-4bdd-4e5b-b869-eb87815a35df"}],"tags":{"regime":"sa","templateId":"verifyEmailAddress","platform":"mdtp","ContactPolicyGroupId":"replaceThis"},"options":{"trackClicks":false,"trackOpens":true,"fromName":"HMRC digital <noreply@tax.service.gov.uk>"},"contactPolicy":{"contactPolicyGroup":"replaceThis","channelCheckConsent":true,"channelApplyFrequencyCap":true},"requestedReceipts":["submitted","delivered","not verified","invalid","bounce","complaint","read","failed"],"content":{"type":"html","subject":"HMRC electronic communications: verify your email address","text":"Verify your email address. Click this link: http://localhost:9024/sa/print-preferences/verification/a2138857-70bb-4287-950c-218988892dd3\\nThank you.","html":"<html><body>Verify your email address</body></html>"},"notifyUrl":""}]"""
    stubEmailServiceProcessQueue()
    stubDigitalContactStubGetEmails(recipientEmail, jsonResponse)
    Json.parse(jsonResponse).as[List[EmailContent]]
  }

  private def verificationLinkOfEmail(sentEmail: EmailContent) = {
    val from = sentEmail.content.text.indexOf("verification/") + 13
    val temp = sentEmail.content.text.substring(from, from + 37)
    temp.substring(0, temp.indexOf("\n"))
  }

  private def extractVerificationLinksFrom(hodStubSentMails: List[EmailContent]) =
    fetchEmailWithSubject(hodStubSentMails, Seq("verify")).map(verificationLinkOfEmail)

  private def fetchEmailWithSubject(hodStubSentMails: List[EmailContent], subject: Seq[String]): List[EmailContent] =
    hodStubSentMails.filter(email => subject.iterator.forall(email.content.subject.contains(_)))

}

case class ImiConsent(channel: String, address: String, consent: Boolean, reason: String)

object ImiConsent {
  implicit val format: OFormat[ImiConsent] = Json.format[ImiConsent]
}
