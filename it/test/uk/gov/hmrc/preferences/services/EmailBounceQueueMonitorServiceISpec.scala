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

package uk.gov.hmrc.preferences.services

import org.mongodb.scala.SingleObservableFuture
import org.mongodb.scala.bson.collection.immutable.Document
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{ BeforeAndAfterEach, TestSuite }
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Format
import play.api.test.Helpers.{ await, defaultAwaitTimeout }
import play.api.test.Injecting
import uk.gov.hmrc.auth.core.AffinityGroup.Individual
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.test.MongoSupport
import uk.gov.hmrc.preferences.connector.Bounce
import uk.gov.hmrc.preferences.exceptions.EntityTaxIdLookupException
import uk.gov.hmrc.preferences.model.TermsAndConditions.Accepted
import uk.gov.hmrc.preferences.model.*
import uk.gov.hmrc.preferences.service.EmailBounceQueueMonitorService
import uk.gov.hmrc.preferences.util.DateTimeExtensions.InstantExtensions
import uk.gov.hmrc.preferences.util.Dc
import utils.FakeApplicationCrypto

import java.time.Instant
import java.util.UUID
import scala.concurrent.ExecutionContext

class EmailBounceQueueMonitorServiceISpec
    extends AnyFreeSpec with Matchers with MongoSupport with TestSuite with GuiceOneServerPerSuite with ScalaFutures
    with IntegrationPatience with BeforeAndAfterEach with Injecting {

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "auditing.enabled" -> false,
      "metrics.enabled"  -> false
    )
    .overrides(bind[Decrypter].toInstance(FakeApplicationCrypto))
    .build()

  val ggAuthPort: Int = 8585
  val TestEmail = "test@mail.com"

  private val emailBounceQueueMonitorService = inject[EmailBounceQueueMonitorService]
  private implicit val hc: HeaderCarrier = HeaderCarrier()

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
  "PUT /preferences/:entityId/updated" - {
    // ==========================================================================

    "execute successfully" in {
      createPreferences(withEntity = true, nino = Option("YY000200A"), None)
      val bounce = Bounce(TestEmail, Dc.instantNow(), None)
      emailBounceQueueMonitorService.markAsBounced(bounce).futureValue
    }

    "execute successfully when P2 bounced" in {
      createPreferences(withEntity = true, nino = Option("YY000200A"), None)
      val bounce = Bounce(TestEmail, Dc.instantNow(), None, None, None, Some("P2"), Some("YY000200A"))
      emailBounceQueueMonitorService.markAsBounced(bounce).futureValue
    }

    "throw exception when no entity-resolver record" in {
      createPreferences(withEntity = false, nino = Option("YY000200A"), None)
      val bounce = Bounce(TestEmail, Dc.instantNow(), None)

      val ex = the[EntityTaxIdLookupException] thrownBy await(
        emailBounceQueueMonitorService.markAsBounced(bounce)
      )
      ex.getMessage must include("Entity Resolver lookup TaxId failed")
    }

    "throw exception when no taxids defined in entity resolver" in {
      createPreferences(withEntity = true, nino = None, sautr = None) // should make test fail
      val bounce = Bounce(TestEmail, Dc.instantNow(), None)

      the[RuntimeException] thrownBy await(
        emailBounceQueueMonitorService.markAsBounced(bounce)
      )
    }

  }

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
