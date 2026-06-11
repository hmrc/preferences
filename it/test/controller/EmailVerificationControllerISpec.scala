/*
 * Copyright 2024 HM Revenue & Customs
 *
 */

package controller

import conf.PreferencesTestRoutes
import conf.PreferencesTestRoutes.`/preferences/:entityId`
import org.mongodb.scala.SingleObservableFuture
import org.mongodb.scala.bson.collection.immutable.Document
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{ BeforeAndAfterEach, TestSuite }
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.http.ContentTypes
import play.api.http.Status.{ INTERNAL_SERVER_ERROR, NO_CONTENT }
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{ Format, Json }
import play.api.test.Helpers.{ CONTENT_TYPE, PUT, contentAsString, defaultAwaitTimeout, status }
import play.api.test.{ FakeHeaders, FakeRequest, Injecting }
import uk.gov.hmrc.auth.core.AffinityGroup.Individual
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.crypto.{ Crypted, Decrypter, Encrypter, PlainBytes, PlainContent, PlainText }
import uk.gov.hmrc.mongo.test.MongoSupport
import uk.gov.hmrc.paperless.controllers.model.PreferenceResponse
import uk.gov.hmrc.preferences.controllers.{ EmailVerificationController, routes }
import uk.gov.hmrc.preferences.model.TermsAndConditions.{ Accepted, Refused }
import uk.gov.hmrc.preferences.model.*
import uk.gov.hmrc.preferences.util.DateTimeExtensions.InstantExtensions
import uk.gov.hmrc.preferences.util.Dc
import utils.FakeApplicationCrypto

import java.time.Instant
import java.util.UUID
import scala.concurrent.ExecutionContext

class EmailVerificationControllerISpec
    extends AnyFreeSpec with Matchers with MongoSupport with TestSuite with GuiceOneServerPerSuite with ScalaFutures
    with IntegrationPatience with BeforeAndAfterEach with Injecting {

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  val preferencesTestRoutes: PreferencesTestRoutes = app.injector.instanceOf[PreferencesTestRoutes]

  object FakeEncrypter extends Encrypter {

    def encrypt(plain: PlainText): Crypted =
      Crypted(plain.value)

    def encrypt(plain: PlainBytes): Crypted =
      Crypted(new String(plain.value))

    override def encrypt(plain: PlainContent): Crypted =
      plain match {
        case t: PlainText  => encrypt(t)
        case b: PlainBytes => encrypt(b)
      }
  }

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "featureFlags.usePCN" -> true,
      "auditing.enabled"    -> false,
      "metrics.enabled"     -> false
    )
    .overrides(bind[Decrypter].toInstance(FakeApplicationCrypto))
    .overrides(bind[Encrypter].toInstance(FakeEncrypter))
    .build()

  val ggAuthPort: Int = 8585

  private val controller = inject[EmailVerificationController]

  override def beforeEach(): Unit = {
    mongoClient
      .getDatabase("preferences")
      .getCollection("saIndividualPreferences")
      .drop()
      .toFuture()
      .futureValue

    mongoClient
      .getDatabase("entity-resolver")
      .getCollection("entity")
      .drop()
      .toFuture()
      .futureValue
    super.beforeEach()
  }

  // ==========================================================================
  "PUT /preferences/email" - {
    // ==========================================================================

    // Requires an EmailToken, which must match the EmailToken regex
    // [0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[0-9a-f]{4}-[0-9a-f]{12}
    "return 204" in {
      val p = createPreferences(withEntity = true, nino = Option("YY000200A"), None)
      val token = p.pendingEmail.get.verificationLink.get._id

      val reqBody =
        s"""{
           |  "token" : "$token"
           |}""".stripMargin

      val fakePostRequest = createFakeRequest(PUT, reqBody)
      val result = controller.verifyEmail()(fakePostRequest)
      status(result) must be(NO_CONTENT)
    }

    "return 500 when no entity-resolver record" in {
      val p = createPreferences(withEntity = false, nino = Option("YY000200A"), sautr = None) // should make test fail
      val token = p.pendingEmail.get.verificationLink.get._id

      val reqBody =
        s"""{
           |  "token" : "$token"
           |}""".stripMargin

      val fakePostRequest = createFakeRequest(PUT, reqBody)
      val result = controller.verifyEmail()(fakePostRequest)

      status(result) must be(INTERNAL_SERVER_ERROR)
      contentAsString(result) must include("Entity Resolver lookup TaxId failed")
    }

    "validate termsAndConditions before and after email verification" in {
      val p = createPreferences(withEntity = true, nino = Option("YY000200A"), None, accepted = false)
      val token = p.pendingEmail.get.verificationLink.get._id

      val printPreferencesBefore =
        preferencesTestRoutes.get(`/preferences/:entityId`(p.entityId)).json.as[PreferenceResponse]

      val reqBody =
        s"""{
           |  "token" : "$token"
           |}""".stripMargin

      val fakePostRequest = createFakeRequest(PUT, reqBody)
      val result = controller.verifyEmail()(fakePostRequest)
      status(result) must be(NO_CONTENT)

      val printPreferencesAfter =
        preferencesTestRoutes.get(`/preferences/:entityId`(p.entityId)).json.as[PreferenceResponse]

      printPreferencesBefore.termsAndConditions.get("generic").get.accepted mustBe false
      printPreferencesAfter.termsAndConditions.get("generic").get.accepted mustBe true
    }
  }

  private def createFakeRequest(method: String, reqBody: String) =
    FakeRequest(
      method,
      routes.EmailVerificationController.verifyEmail().url,
      FakeHeaders(Seq(CONTENT_TYPE -> ContentTypes.JSON)),
      Json.parse(reqBody)
    )

  private def createPreferences(
    withEntity: Boolean,
    nino: Option[String],
    sautr: Option[String],
    accepted: Boolean = true
  ): Preferences = {
    val eid = EntityId(UUID.randomUUID().toString)

    val link = EmailVerificationLink(linkSentTime = Instant.now.minusDays(1))
    val p = Preferences(
      entityId = eid,
      termsAndConditions = TermsAndConditions(if (accepted) Accepted(Dc.instantNow()) else Refused(Dc.instantNow())),
      pendingEmail = Some(PendingEmailAddress(email = "test@mail.com", verificationLink = Some(link))),
      userType = Some(UserType(Some(Individual), Some(ConfidenceLevel.L200)))
    )
    implicit val pformat: Format[Preferences] = Preferences.formats

    mongoClient
      .getDatabase("preferences")
      .getCollection("saIndividualPreferences")
      .insertOne(Document(pformat.writes(p).toString()))
      .toFuture()
      .futureValue

    if (withEntity) {
      val sb: StringBuilder = new StringBuilder
      // Create the entity
      sb ++= s"""{"_id" : "$eid""""
      if (nino.isDefined) sb ++= s""", "nino" : "${nino.get}" """
      if (sautr.isDefined) sb ++= s""", "sautr": "${sautr.get}" """
      sb ++= s"}"

      val item = mongoClient
        .getDatabase("entity-resolver")
        .getCollection("entity")
        .insertOne(Document(sb.mkString))
        .toFuture()
        .futureValue
      item.wasAcknowledged() must be(true)
    }
    p
  }
}
