/*
 * Copyright 2024 HM Revenue & Customs
 *
 */

package controller

import conf.ItAuthHelper
import org.mongodb.scala.SingleObservableFuture
import org.mongodb.scala.bson.collection.immutable.Document
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{ BeforeAndAfterEach, TestSuite }
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.http.ContentTypes
import play.api.http.Status.{ INTERNAL_SERVER_ERROR, OK }
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{ Format, Json }
import play.api.test.Helpers.{ CONTENT_TYPE, POST, contentAsString, defaultAwaitTimeout, status }
import play.api.test.{ FakeHeaders, FakeRequest, Injecting }
import uk.gov.hmrc.auth.core.AffinityGroup.Individual
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.domain.*
import uk.gov.hmrc.mongo.test.MongoSupport
import uk.gov.hmrc.preferences.controllers.{ TermsAndConditionsController, routes }
import uk.gov.hmrc.preferences.model.TermsAndConditions.Accepted
import uk.gov.hmrc.preferences.model.*
import uk.gov.hmrc.preferences.util.DateTimeExtensions.InstantExtensions
import uk.gov.hmrc.preferences.util.Dc
import utils.FakeApplicationCrypto

import java.time.Instant
import java.util.UUID
import scala.concurrent.ExecutionContext

class TermsAndConditionsControllerPCNISpec
    extends AnyFreeSpec with Matchers with MongoSupport with TestSuite with GuiceOneServerPerSuite with ScalaFutures
    with IntegrationPatience with BeforeAndAfterEach with Injecting {

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "featureFlags.usePCN" -> true,
      "auditing.enabled"    -> false,
      "metrics.enabled"     -> false
    )
    .overrides(bind[Decrypter].toInstance(FakeApplicationCrypto))
    .build()

  val ggAuthPort: Int = 8585

  private val termsAndConditionsController = inject[TermsAndConditionsController]
  private val authHelper = inject[ItAuthHelper]

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
  "optin and optout" - {
    // ==========================================================================

    "Opt out" - {

      "return 200" in {
        val p = createPreferences(withEntity = true, nino = Option("YY000200A"), None)

        val payload =
          s"""{
             |  "generic":{
             |    "accepted":false,
             |    "optInPage":{
             |      "version": {"major":2,"minor":1}, "cohort":1, "pageType":"IPage"}
             |  },
             |  "language": "en"
             |}""".stripMargin

        val fakeRequest = createFakeRequest(payload, p.entityId)
        val result = termsAndConditionsController.store(p.entityId)(fakeRequest)
        status(result) must be(OK)
      }

      "throw exception when no entity-resolver record" in {
        val p = createPreferences(withEntity = false, nino = Option("YY000200A"), None)

        val payload =
          s"""{
             |  "generic":{
             |    "accepted":false,
             |    "optInPage":{
             |      "version": {"major":2,"minor":1}, "cohort":1, "pageType":"IPage"}
             |  },
             |  "language": "en"
             |}""".stripMargin

        val fakeRequest = createFakeRequest(payload, p.entityId)
        val result = termsAndConditionsController.store(p.entityId)(fakeRequest)
        status(result) must be(INTERNAL_SERVER_ERROR)
        contentAsString(result) must include("Entity Resolver lookup TaxId failed")
      }

    }

    "Opt in" - {

      "opt in return 200" in {
        val p = createPreferences(withEntity = true, nino = Option("YY000200A"), None)

        val payload =
          s"""{
             |  "generic":{
             |    "accepted": true,
             |    "optInPage":{
             |      "version": {"major":2,"minor":1}, "cohort":1, "pageType":"IPage"}
             |  },
             |  "email": "test@mail.com",
             |  "language": "en",
             |  "returnText": "return-text",
             |  "returnUrl": "return-url"
             |}""".stripMargin

        val fakeRequest = createFakeRequest(payload, p.entityId)
        val result = termsAndConditionsController.store(p.entityId)(fakeRequest)
        status(result) must be(OK)
      }

      "throw exception when no entity-resolver record" in {
        val p = createPreferences(withEntity = false, nino = Option("YY000200A"), None)

        val payload =
          s"""{
             |  "generic":{
             |    "accepted": true,
             |    "optInPage":{
             |      "version": {"major":2,"minor":1}, "cohort":1, "pageType":"IPage"}
             |  },
             |  "email": "test@mail.com",
             |  "language": "en",
             |  "returnText": "return-text",
             |  "returnUrl": "return-url"
             |}""".stripMargin

        val fakeRequest = createFakeRequest(payload, p.entityId)
        val result = termsAndConditionsController.store(p.entityId)(fakeRequest)
        status(result) must be(INTERNAL_SERVER_ERROR)
        contentAsString(result) must include("Entity Resolver lookup TaxId failed")
      }

    }
  }

  private def createFakeRequest(reqBody: String, entityId: EntityId) =
    FakeRequest(
      POST,
      routes.TermsAndConditionsController.store(entityId).url,
      FakeHeaders(
        Seq(
          CONTENT_TYPE -> ContentTypes.JSON,
          authHelper.authHeader(Nino("YY000200A"), ggAuthPort)
        )
      ),
      Json.parse(reqBody)
    )

  private def createPreferences(withEntity: Boolean, nino: Option[String], sautr: Option[String]): Preferences = {
    val eid = EntityId(UUID.randomUUID().toString)

    val link = EmailVerificationLink(linkSentTime = Instant.now.minusDays(1))
    val p = Preferences(
      entityId = eid,
      termsAndConditions = TermsAndConditions(Accepted(Dc.instantNow())),
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
