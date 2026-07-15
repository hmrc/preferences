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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.bson.types.ObjectId
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{ any, eq as eqTo }
import org.mockito.Mockito.{ when, * }
import org.scalatest.LoneElement
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.{ Configuration, Environment }
import play.api.http.{ HeaderNames, HttpConfiguration, Status }
import play.api.libs.json.{ JsValue, Json }
import play.api.test.Helpers
import play.api.test.Helpers.*
import uk.gov.hmrc.auth.core.AffinityGroup
import uk.gov.hmrc.domain.{ Nino, SaUtr }
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse }
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.Audit
import uk.gov.hmrc.preferences.Auditable
import uk.gov.hmrc.preferences.connector.{ CitizenDetailsConnector, EntityResolverConnector, MessageConnector, TaxpayerConnector }
import uk.gov.hmrc.preferences.controllers.ApiVersion.{ v1, v2 }
import uk.gov.hmrc.preferences.controllers.model.EmailToken
import uk.gov.hmrc.preferences.model.MessageDeliveryFormat.Digital
import uk.gov.hmrc.preferences.model.TermsAndConditions.Accepted
import uk.gov.hmrc.preferences.model.*
import uk.gov.hmrc.preferences.model.VerifyStatus.{ AlreadyVerified, AlreadyVerifiedLinks }
import uk.gov.hmrc.preferences.repository.PreferencesRepository
import uk.gov.hmrc.preferences.service.{ ETMPService, PreferencesChangedNotifierService }
import uk.gov.hmrc.preferences.templates.{ CustomerType, TemplateHelper, TemplateId }
import uk.gov.hmrc.preferences.util.Dc

import java.time.temporal.ChronoUnit
import scala.concurrent.{ ExecutionContext, Future }

class EmailVerificationSpec extends PlaySpec with LoneElement with ScalaFutures with MockitoSugar {
  implicit val system: ActorSystem = ActorSystem("test")
  implicit val materializer: Materializer = Materializer(system)

  "Calling the verifyEmail endpoint" should {
    "give us the return link text and return url if supplied" in new TestCase {

      private val link = EmailVerificationLink(
        linkSentTime = Dc.instantNow().minus(1, ChronoUnit.DAYS),
        returnText = Some("Return Text"),
        returnUrl = Some("Return Url")
      )

      private def pendingPreferences =
        Preferences(
          entityId = EntityId("2222"),
          termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
          pendingEmail = Some(PendingEmailAddress(email = "test@mail.com", verificationLink = Some(link)))
        )

      when(mockPreferencesRepository.findByVerificationToken(any[EmailToken])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(pendingPreferences)))

      when(
        mockPreferencesRepository
          .markEmailVerified(any[ObjectId], any[PendingEmailAddress], any[Option[Language]], any[Option[Event]])(
            any[HeaderCarrier]
          )
      ).thenReturn(Future.successful(()))

      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Digital), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      private val result = resource.verifyTokenAgainstPendingEmails(EmailToken(link._id))(HeaderCarrier())

      status(result) mustBe 201
      private val doc = Json.parse(contentAsString(result))
      (doc \ "returnLinkText").as[String] mustBe "Return Text"
      (doc \ "returnUrl").as[String] mustBe "Return Url"
    }

    "not give us the return link text and return url if not supplied" in new TestCase {
      private val link =
        EmailVerificationLink(
          linkSentTime = Dc.instantNow().minus(1, ChronoUnit.DAYS),
          returnText = None,
          returnUrl = None
        )

      private def pendingPreferences =
        Preferences(
          entityId = EntityId("2222"),
          termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
          pendingEmail = Some(PendingEmailAddress(email = "test@mail.com", verificationLink = Some(link)))
        )

      when(mockPreferencesRepository.findByVerificationToken(any[EmailToken])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(pendingPreferences)))

      when(
        mockPreferencesRepository
          .markEmailVerified(any[ObjectId], any[PendingEmailAddress], any[Option[Language]], any[Option[Event]])(
            any[HeaderCarrier]
          )
      ).thenReturn(Future.successful(()))

      when(
        mockPCNService
          .notifyPreferencesChanged(any[ObjectId], any[EntityId], eqTo(Digital), eqTo(false), any)(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
      )
        .thenReturn(Future.successful(()))

      private val result =
        resource.verifyTokenAgainstPendingEmails(EmailToken(link._id))(HeaderCarrier())

      status(result) mustBe 204
    }

    "give us the return link text and return url if supplied when verifying an already verified email" in new TestCase {
      private val link = EmailVerificationLink(
        linkSentTime = Dc.instantNow().minus(1, ChronoUnit.DAYS),
        returnText = Some("Return Text"),
        returnUrl = Some("Return Url")
      )

      private def pendingPreferences =
        Preferences(
          entityId = EntityId("2222"),
          termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
          email = Some(
            EmailAddress(
              email = "test@mail.com",
              verifiedWithLink = Some(link)
            )
          )
        )

      when(mockPreferencesRepository.findByVerificationToken(any[EmailToken])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(pendingPreferences)))

      private val result = resource.verifyTokenAgainstPendingEmails(EmailToken(link._id))(HeaderCarrier())

      status(result) mustBe OK
      private val doc = contentAsJson(result)
      (doc \ "returnLinkText").as[String] mustBe "Return Text"
      (doc \ "returnUrl").as[String] mustBe "Return Url"
      (doc \ "verifyStatus").as[VerifyStatus] mustBe AlreadyVerifiedLinks
      (doc \ "description").as[String] contains "already verified"
    }

    "not give us the return link text and return url if not supplied when verifying an already verified email" in new TestCase {
      private val link =
        EmailVerificationLink(
          linkSentTime = Dc.instantNow().minus(1, ChronoUnit.DAYS),
          returnText = None,
          returnUrl = None
        )

      private def pendingPreferences =
        Preferences(
          entityId = EntityId("2222"),
          termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
          email = Some(
            EmailAddress(
              email = "test@mail.com",
              verifiedWithLink = Some(link)
            )
          )
        )

      when(mockPreferencesRepository.findByVerificationToken(any[EmailToken])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(pendingPreferences)))

      private val result = resource.verifyTokenAgainstPendingEmails(EmailToken(link._id))(HeaderCarrier())

      status(result) mustBe OK
      val responseBody = contentAsJson(result)
      (responseBody \ "verifyStatus").as[VerifyStatus] mustBe AlreadyVerified
      (responseBody \ "description").as[String] contains "already verified"
    }

    "one-way message for verified user" should {
      "be sent only with sautr when affinity group is Individual" in new TestCase {

        private def pendingPreferences =
          Preferences(
            entityId = EntityId("2222"),
            termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
            userType = Option(UserType(Some(AffinityGroup.Individual))),
            email = Option(EmailAddress("test@test.com"))
          )

        when(mockPreferencesRepository.findByVerificationToken(any[EmailToken])(any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(pendingPreferences)))
        when(mockEntityResolverConnector.getTaxId(any[EntityId])(any[HeaderCarrier]))
          .thenReturn(Future.successful(TaxId(_id = "55226158-fd55-4060-ba86-ec44f12c750b", Some("2000029800"), None)))
        when(mockMessageConnector.postMessage(any[JsValue])(any[HeaderCarrier]))
          .thenReturn(Future.successful(HttpResponse(Status.OK, "")))
        when(mockTaxpayerConnector.getTaxpayerName(any[SaUtr])(any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(TaxpayerName())))

        eventually {
          resource.sendSecureMessage(EmailToken("test@mail.com")).futureValue
          verify(mockMessageConnector, times(1)).postMessage(any[JsValue])(any[HeaderCarrier])
        }
      }

      "be sent with both sautr and nino when affinity group is Individual" in new TestCase {

        private def pendingPreferences =
          Preferences(
            entityId = EntityId("2222"),
            termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
            userType = Option(UserType(Some(AffinityGroup.Individual))),
            email = Option(EmailAddress("test@test.com"))
          )

        when(mockPreferencesRepository.findByVerificationToken(any[EmailToken])(any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(pendingPreferences)))
        when(mockEntityResolverConnector.getTaxId(any[EntityId])(any[HeaderCarrier]))
          .thenReturn(
            Future.successful(
              TaxId(_id = "55226158-fd55-4060-ba86-ec44f12c750b", Some("2000029800"), Some("ZR076938C"))
            )
          )
        when(mockMessageConnector.postMessage(any[JsValue])(any[HeaderCarrier]))
          .thenReturn(Future.successful(HttpResponse(Status.OK, "")))
        when(mockTaxpayerConnector.getTaxpayerName(any[SaUtr])(any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(TaxpayerName())))

        resource.sendSecureMessage(EmailToken("test@mail.com")).futureValue
        verify(mockMessageConnector, times(1)).postMessage(any[JsValue])(any[HeaderCarrier])
      }

      "be sent with only nino when affinity group is Individual" in new TestCase {

        private def pendingPreferences =
          Preferences(
            entityId = EntityId("2222"),
            termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
            userType = Option(UserType(Some(AffinityGroup.Individual))),
            email = Option(EmailAddress("test@test.com"))
          )

        when(mockPreferencesRepository.findByVerificationToken(any[EmailToken])(any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(pendingPreferences)))
        when(mockEntityResolverConnector.getTaxId(any[EntityId])(any[HeaderCarrier]))
          .thenReturn(Future.successful(TaxId(_id = "55226158-fd55-4060-ba86-ec44f12c750b", None, Some("ZR076938C"))))
        when(mockMessageConnector.postMessage(any[JsValue])(any[HeaderCarrier]))
          .thenReturn(Future.successful(HttpResponse(Status.OK, "")))
        when(mockCitizenDetailsConnector.getTaxpayerName(any[Nino])(any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(TaxpayerName())))

        resource.sendSecureMessage(EmailToken("test@mail.com")).futureValue
        verify(mockMessageConnector, times(1)).postMessage(any[JsValue])(any[HeaderCarrier])
      }

      "not be sent with sautr when affinity group is Organisation" in new TestCase {

        private def pendingPreferences =
          Preferences(
            entityId = EntityId("2222"),
            termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
            userType = Option(UserType(Some(AffinityGroup.Organisation))),
            email = Option(EmailAddress("test@test.com"))
          )

        when(mockPreferencesRepository.findByVerificationToken(any[EmailToken])(any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(pendingPreferences)))
        when(mockEntityResolverConnector.getTaxId(any[EntityId])(any[HeaderCarrier]))
          .thenReturn(Future.successful(TaxId(_id = "55226158-fd55-4060-ba86-ec44f12c750b", Some("2000029800"), None)))
        when(mockMessageConnector.postMessage(any[JsValue])(any[HeaderCarrier]))
          .thenReturn(Future.successful(HttpResponse(Status.OK, "")))

        resource.sendSecureMessage(EmailToken("test@mail.com")).futureValue
        verify(mockMessageConnector, times(0)).postMessage(any[JsValue])(any[HeaderCarrier])
      }

      "not be sent with no nino or sautr" in new TestCase {

        private def pendingPreferences =
          Preferences(
            entityId = EntityId("2222"),
            termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
            userType = Option(UserType(Some(AffinityGroup.Individual))),
            email = Option(EmailAddress("test@test.com"))
          )

        when(mockPreferencesRepository.findByVerificationToken(any[EmailToken])(any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(pendingPreferences)))
        when(mockEntityResolverConnector.getTaxId(any[EntityId])(any[HeaderCarrier]))
          .thenReturn(Future.successful(TaxId(_id = "55226158-fd55-4060-ba86-ec44f12c750b", None, None)))
        when(mockMessageConnector.postMessage(any[JsValue])(any[HeaderCarrier]))
          .thenReturn(Future.successful(HttpResponse(Status.OK, "")))

        resource.sendSecureMessage(EmailToken("test@mail.com")).futureValue
        verify(mockMessageConnector, times(0)).postMessage(any[JsValue])(any[HeaderCarrier])
      }

      "not be sent with no user type" in new TestCase {

        private def pendingPreferences =
          Preferences(
            entityId = EntityId("2222"),
            termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
            email = Option(EmailAddress("test@test.com"))
          )

        when(mockPreferencesRepository.findByVerificationToken(any[EmailToken])(any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(pendingPreferences)))
        when(mockEntityResolverConnector.getTaxId(any[EntityId])(any[HeaderCarrier]))
          .thenReturn(Future.successful(TaxId(_id = "55226158-fd55-4060-ba86-ec44f12c750b", Some("2000029800"), None)))
        when(mockMessageConnector.postMessage(any[JsValue])(any[HeaderCarrier]))
          .thenReturn(Future.successful(HttpResponse(Status.OK, "")))

        resource.sendSecureMessage(EmailToken("test@mail.com")).futureValue
        verify(mockMessageConnector, times(0)).postMessage(any[JsValue])(any[HeaderCarrier])
      }

      "be sent for ITSA when affinity group is Organisation" in new TestCase {
        private def pendingPreferences =
          Preferences(
            entityId = EntityId("2222"),
            termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
            userType = Option(UserType(Some(AffinityGroup.Organisation))),
            email = Option(EmailAddress("test@test.com"))
          )

        when(mockPreferencesRepository.findByVerificationToken(any[EmailToken])(any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(pendingPreferences)))
        when(mockEntityResolverConnector.getTaxId(any[EntityId])(any[HeaderCarrier]))
          .thenReturn(
            Future.successful(TaxId(_id = "55226158-fd55-4060-ba86-ec44f12c750b", None, None, Some("ABCD12345678901")))
          )
        when(mockMessageConnector.postMessage(any[JsValue])(any[HeaderCarrier]))
          .thenReturn(Future.successful(HttpResponse(Status.OK, "")))

        resource.sendSecureMessage(EmailToken("test@mail.com")).futureValue
        verify(mockMessageConnector, times(1)).postMessage(any[JsValue])(any[HeaderCarrier])
        verify(mockTaxpayerConnector, times(0)).getTaxpayerName(any[SaUtr])(any[HeaderCarrier])
        verify(mockCitizenDetailsConnector, times(0)).getTaxpayerName(any[Nino])(any[HeaderCarrier])
      }

      "try and get taxpayer name from sautr for ITSA" in new TestCase {
        private def pendingPreferences =
          Preferences(
            entityId = EntityId("2222"),
            termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
            userType = Option(UserType(Some(AffinityGroup.Organisation))),
            email = Option(EmailAddress("test@test.com"))
          )

        when(mockPreferencesRepository.findByVerificationToken(any[EmailToken])(any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(pendingPreferences)))
        when(mockEntityResolverConnector.getTaxId(any[EntityId])(any[HeaderCarrier]))
          .thenReturn(
            Future.successful(
              TaxId(
                _id = "55226158-fd55-4060-ba86-ec44f12c750b",
                Some("2000029800"),
                Some("ZR076938C"),
                Some("ABCD12345678901")
              )
            )
          )
        when(mockMessageConnector.postMessage(any[JsValue])(any[HeaderCarrier]))
          .thenReturn(Future.successful(HttpResponse(Status.OK, "")))
        when(mockTaxpayerConnector.getTaxpayerName(any[SaUtr])(any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(TaxpayerName())))
        when(mockCitizenDetailsConnector.getTaxpayerName(any[Nino])(any[HeaderCarrier]))
          .thenReturn(Future.successful(None))

        resource.sendSecureMessage(EmailToken("test@mail.com")).futureValue
        verify(mockMessageConnector, times(1)).postMessage(any[JsValue])(any[HeaderCarrier])
        verify(mockTaxpayerConnector, times(1)).getTaxpayerName(any[SaUtr])(any[HeaderCarrier])
        verify(mockCitizenDetailsConnector, times(0)).getTaxpayerName(any[Nino])(any[HeaderCarrier])
      }

      "try and get taxpayer name from nino for ITSA" in new TestCase {
        private def pendingPreferences =
          Preferences(
            entityId = EntityId("2222"),
            termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
            userType = Option(UserType(Some(AffinityGroup.Organisation))),
            email = Option(EmailAddress("test@test.com"))
          )

        when(mockPreferencesRepository.findByVerificationToken(any[EmailToken])(any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(pendingPreferences)))
        when(mockEntityResolverConnector.getTaxId(any[EntityId])(any[HeaderCarrier]))
          .thenReturn(
            Future.successful(
              TaxId(
                _id = "55226158-fd55-4060-ba86-ec44f12c750b",
                Some("2000029800"),
                Some("ZR076938C"),
                Some("ABCD12345678901")
              )
            )
          )
        when(mockMessageConnector.postMessage(any[JsValue])(any[HeaderCarrier]))
          .thenReturn(Future.successful(HttpResponse(Status.OK, "")))
        when(mockTaxpayerConnector.getTaxpayerName(any[SaUtr])(any[HeaderCarrier])).thenReturn(Future.successful(None))
        when(mockCitizenDetailsConnector.getTaxpayerName(any[Nino])(any[HeaderCarrier]))
          .thenReturn(Future.successful(None))

        resource.sendSecureMessage(EmailToken("test@mail.com")).futureValue
        verify(mockMessageConnector, times(1)).postMessage(any[JsValue])(any[HeaderCarrier])
        verify(mockTaxpayerConnector, times(1)).getTaxpayerName(any[SaUtr])(any[HeaderCarrier])
        verify(mockCitizenDetailsConnector, times(1)).getTaxpayerName(any[Nino])(any[HeaderCarrier])
      }

      "be sent only with sautr when affinity group is Individual - MessageV4" in new TestCase {

        private def pendingPreferences =
          Preferences(
            entityId = EntityId("2222"),
            termsAndConditions = termsAndConditionsAcceptedForGenericOnly,
            userType = Option(UserType(Some(AffinityGroup.Individual))),
            email = Option(EmailAddress("test@test.com"))
          )

        val messageV4: ArgumentCaptor[JsValue] = ArgumentCaptor.forClass(classOf[JsValue])
        when(mockPreferencesRepository.findByVerificationToken(any[EmailToken])(any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(pendingPreferences)))
        when(mockEntityResolverConnector.getTaxId(any[EntityId])(any[HeaderCarrier]))
          .thenReturn(Future.successful(TaxId(_id = "55226158-fd55-4060-ba86-ec44f12c750b", Some("2000029800"), None)))
        when(mockMessageConnector.postMessage(messageV4.capture())(any[HeaderCarrier]))
          .thenReturn(Future.successful(HttpResponse(Status.OK, "")))
        when(mockTaxpayerConnector.getTaxpayerName(any[SaUtr])(any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(TaxpayerName())))
        eventually {
          resource.sendSecureMessage(EmailToken("test@mail.com")).futureValue
          (messageV4.getValue \ "content" \\ "subject")
            .map(_.as[String]) mustBe List("Your online tax letters", "Eich llythyrau treth ar-lein")
        }
      }

    }

    "Message Builder" should {

      "create correct message for Individuals - MessageV4" in new TestCase {
        private val taxId = TaxId(_id = "55226158-fd55-4060-ba86-ec44f12c750b", Some("2000029800"), None)
        when(mockTemplateHelper.getMessageContent()).thenReturn("message content")
        when(mockTemplateHelper.getWelshMessageContent()).thenReturn("welsh message content")

        private val notificationMessage =
          resource
            .messageBuilder(
              CustomerType.PTA,
              taxId,
              EmailAddress("test@test.com"),
              Some(TaxpayerName(line1 = Some("Test")))
            )

        (notificationMessage \ "externalRef" \ "id").as[String] must not be empty
        (notificationMessage \ "externalRef" \ "source").as[String] must be("preferences")
        (notificationMessage \ "messageType").as[String] must be(TemplateId.DIGITAL_OPTIN)
        (notificationMessage \ "recipient" \ "email").as[String] must be("test@test.com")
        (notificationMessage \ "recipient" \ "name" \ "line1").as[String] must be("Test")
        (notificationMessage \ "recipient" \ "regime").as[String] must be("sa")
        (notificationMessage \ "recipient" \ "taxIdentifier" \ "name").as[String] must be("sautr")
        (notificationMessage \ "recipient" \ "taxIdentifier" \ "value").as[String] must be("2000029800")
        (notificationMessage \ "content" \\ "subject").map(_.as[String]) must be(
          List("Your online tax letters", "Eich llythyrau treth ar-lein")
        )
        (notificationMessage \ "content" \\ "body").map(_.as[String]) must be(
          List("message content", "welsh message content")
        )
        (notificationMessage \ "content" \\ "lang").map(_.as[String]) must be(List("en", "cy"))
      }

      "create correct message for Organisation" in new TestCase {
        private val taxId = TaxId(_id = "55226158-fd55-4060-ba86-ec44f12c750b", Some("2000029800"), None)
        when(mockTemplateHelper.getMessageContent())
          .thenReturn("message content")

        private val notificationMessage =
          resource.messageBuilder(CustomerType.BTA, taxId, EmailAddress("test@test.com"), Some(TaxpayerName()))
        (notificationMessage \ "externalRef" \ "source").as[String] must be("preferences")
        (notificationMessage \ "messageType").as[String] must be(TemplateId.DIGITAL_OPTIN)
        (notificationMessage \ "recipient" \ "email").as[String] must be("test@test.com")
      }
    }
  }

  trait TestCase {

    import scala.concurrent.ExecutionContext.Implicits.global

    implicit val hc: HeaderCarrier = HeaderCarrier()

    val termsAndConditionsAcceptedForGenericOnly: TermsAndConditions =
      TermsAndConditions(Accepted(Dc.instantNow()))

    val mockPreferencesRepository: PreferencesRepository = mock[PreferencesRepository]
    val mockEntityResolverConnector: EntityResolverConnector = mock[EntityResolverConnector]
    val mockTaxpayerConnector: TaxpayerConnector = mock[TaxpayerConnector]
    val mockCitizenDetailsConnector: CitizenDetailsConnector = mock[CitizenDetailsConnector]
    val mockMessageConnector: MessageConnector = mock[MessageConnector]
    val mockTemplateHelper: TemplateHelper = mock[TemplateHelper]
    val mockAuditConnector: AuditConnector = mock[AuditConnector]
    val mockEtmpService: ETMPService = mock[ETMPService]
    val mockPCNService: PreferencesChangedNotifierService = mock[PreferencesChangedNotifierService]
    val mockAuditable: Auditable = new Auditable {
      override def appName: String = "test"
      override def audit: Audit = new Audit("test", mockAuditConnector)
    }
    val dummyLink = "this/is/a/fake/link"
    val mockVerificationLink: EmailVerificationLink => String = _ => dummyLink

    val env = Environment.simple()
    implicit val httpConfig: HttpConfiguration = HttpConfiguration.fromConfiguration(
      Configuration.load(env),
      env
    )

    val resource: EmailVerificationController = new EmailVerificationController(
      () => Dc.instantNow(),
      mockPreferencesRepository,
      mockEntityResolverConnector,
      mockTaxpayerConnector,
      mockCitizenDetailsConnector,
      mockMessageConnector,
      mockTemplateHelper,
      mockVerificationLink,
      mockAuditable,
      mockEtmpService,
      mockPCNService,
      Helpers.stubControllerComponents(),
      false
    )
  }
}
