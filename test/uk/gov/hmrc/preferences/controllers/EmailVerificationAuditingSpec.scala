/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.controllers

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.mockito.ArgumentMatchers.{ any, eq as eqTo }
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import org.mongodb.scala.bson.ObjectId
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.LoneElement
import org.scalatestplus.play.PlaySpec
import play.api.http.HttpConfiguration
import play.api.{ Configuration, Environment }
import play.api.test.Helpers
import play.api.test.Helpers.stubPlayBodyParsers
import uk.gov.hmrc.http.{ HeaderCarrier, HeaderNames }
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.Audit
import uk.gov.hmrc.preferences.Auditable
import uk.gov.hmrc.preferences.connector.{ CitizenDetailsConnector, EntityResolverConnector, MessageConnector, TaxpayerConnector }
import uk.gov.hmrc.preferences.controllers.ApiVersion.v1
import uk.gov.hmrc.preferences.controllers.model.EmailToken
import uk.gov.hmrc.preferences.model.MessageDeliveryFormat.Digital
import uk.gov.hmrc.preferences.model.*
import uk.gov.hmrc.preferences.model.TermsAndConditions.Accepted
import uk.gov.hmrc.preferences.repository.PreferencesRepository
import uk.gov.hmrc.preferences.service.{ ETMPService, PreferencesChangedNotifierService }
import uk.gov.hmrc.preferences.templates.TemplateHelper
import uk.gov.hmrc.preferences.util.Dc

import java.time.temporal.ChronoUnit
import scala.concurrent.{ ExecutionContext, Future }

class EmailVerificationAuditingSpec extends PlaySpec with LoneElement with ScalaFutures with MockitoSugar {

  implicit val system: ActorSystem = ActorSystem("test")
  implicit val materializer: Materializer = Materializer(system)

  "Calling the verifyEmail endpoint" should {
    "generate a success audit event that the email has been verified" in new TestCase {

      private val link = EmailVerificationLink(linkSentTime = Dc.instantNow().minus(1, ChronoUnit.DAYS))

      def pendingPreferences: Preferences =
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

      override def verifiedPreferences: Preferences =
        pendingPreferences.copy(pendingEmail = None, email = Some(EmailAddress("test@mail.com")))

      resource.verifyTokenAgainstPendingEmails(EmailToken(link._id))(HeaderCarrier()).futureValue

      private val dataEvent = testAudit.capturedDataEvents.loneElement
      dataEvent.auditSource must be("test")
      dataEvent.auditType must be("TxSucceeded")
      dataEvent.eventId must not be empty
      dataEvent.generatedAt must not be null
      dataEvent.tags must contain("transactionName" -> "Email Verified")
      dataEvent.tags.get(HeaderNames.xRequestId) must not be empty
      dataEvent.detail must (
        contain("entityId"       -> "2222") and
          contain("emailAddress" -> "test@mail.com") and
          contain("verificationLink" -> "/verification-link/")
      )
    }
  }

  trait TestCase {

    import scala.concurrent.ExecutionContext.Implicits.global

    def verifiedPreferences: Preferences

    val termsAndConditionsAcceptedForGenericOnly: TermsAndConditions =
      TermsAndConditions(Accepted(Dc.instantNow()))

    val mockPreferencesRepository: PreferencesRepository = mock[PreferencesRepository]
    val mockEntityResolverConnector: EntityResolverConnector = mock[EntityResolverConnector]
    val mockTaxpayerConnector: TaxpayerConnector = mock[TaxpayerConnector]
    val mockCitizenDetailsConnector: CitizenDetailsConnector = mock[CitizenDetailsConnector]
    val mockMessageConnector: MessageConnector = mock[MessageConnector]
    val mockTemplateHelper: TemplateHelper = mock[TemplateHelper]
    val mockEtmpService: ETMPService = mock[ETMPService]
    val mockPCNService: PreferencesChangedNotifierService = mock[PreferencesChangedNotifierService]
    val verificationLinkPrefix = "/verification-link/"
    val mockEmailVerificationLink: EmailVerificationLink => String = _ => verificationLinkPrefix
    val mockAuditConnector: AuditConnector = mock[AuditConnector]
    val testAudit = new TestAudit(mockAuditConnector)
    val testAuditable: Auditable = new Auditable {
      override def appName: String = "test"
      override def audit: Audit = testAudit
    }

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
      mockEmailVerificationLink,
      testAuditable,
      mockEtmpService,
      mockPCNService,
      Helpers.stubControllerComponents(),
      false
    )
  }

}
