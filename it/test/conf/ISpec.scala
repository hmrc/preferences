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

import com.codahale.metrics.SharedMetricRegistries
import org.apache.pekko.stream.Materializer
import org.mongodb.scala.SingleObservableFuture
import org.mongodb.scala.bson.collection.immutable.Document
import org.scalatest.concurrent.{ Eventually, IntegrationPatience, ScalaFutures }
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach, SuiteMixin, TestSuite }
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.{ GuiceApplicationBuilder, GuiceableModule }
import play.api.test.Helpers.*
import uk.gov.hmrc.crypto.{ Crypted, Decrypter, Encrypter, PlainBytes, PlainContent, PlainText }
import uk.gov.hmrc.domain.{ HmrcMtdItsa, Nino, SaUtr }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.test.MongoSupport
import uk.gov.hmrc.preferences.model.EntityId
import uk.gov.hmrc.preferences.repository.PreferencesRepository
import uk.gov.hmrc.preferences.service.{ EmailBounceQueueMonitorService, VerificationChaser }
import utils.{ FakeApplicationCrypto, GenerateRandom }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait ISpec
    extends PlaySpec with SuiteMixin with ScalaFutures with IntegrationPatience with GuiceOneServerPerSuite
    with MongoSupport with BeforeAndAfterEach with BeforeAndAfterAll with Eventually with ResponseMatchers {
  this: TestSuite =>

  implicit val hc: HeaderCarrier = HeaderCarrier()

  def cleanMongoCollection: CleanMongoCollection

  override protected def beforeAll(): Unit = {
    super.afterEach()
    cleanMongoCollection.db.drop().toFuture().futureValue
  }

  override protected def afterEach(): Unit = {
    super.afterEach()
    cleanMongoCollection.db.drop().toFuture().futureValue
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    SharedMetricRegistries.clear()
    val _ = await(
      Future.traverse(cleanMongoCollection.collections())(a => a.deleteMany(Document()).toFuture())
    )
  }

  lazy val utr: SaUtr = GenerateRandom.utr()
  lazy val nino: Nino = GenerateRandom.nino()
  lazy val itsa: HmrcMtdItsa = HmrcMtdItsa("itsa")
  lazy val entityId: EntityId = GenerateRandom.entityId()
  lazy val ggAuthPort: Int = 8585

  trait ISpecTestCase {
    val materializer: Materializer = app.injector.instanceOf[Materializer]
    val emailService: EmailService = app.injector.instanceOf[EmailService]
    val testEmailService: TestEmailService = app.injector.instanceOf[TestEmailService]
    val verificationChaser: VerificationChaser = app.injector.instanceOf[VerificationChaser]
    val preferencesBuilder: PreferencesBuilder = app.injector.instanceOf[PreferencesBuilder]
    val preferencesRepository: PreferencesRepository = app.injector.instanceOf[PreferencesRepository]
    val entityResolverService: EntityResolverService = app.injector.instanceOf[EntityResolverService]
    val emailBounceProcessingService: EmailBounceQueueMonitorService =
      app.injector.instanceOf[EmailBounceQueueMonitorService]
  }

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

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(configMap)
      .overrides(additionalOverrides: _*)
      .overrides(bind[Decrypter].toInstance(FakeApplicationCrypto))
      .overrides(bind[Encrypter].toInstance(FakeEncrypter))
      .build()

  val authHelper: ItAuthHelper = app.injector.instanceOf[ItAuthHelper]
  val preferencesTestRoutes: PreferencesTestRoutes = app.injector.instanceOf[PreferencesTestRoutes]

  def additionalOverrides: Seq[GuiceableModule] =
    Seq.empty

  private lazy val mongoConfig =
    Map(s"mongodb.uri" -> mongoUri)

  private lazy val configMap = mongoConfig ++ additionalConfig

  def additionalConfig: Map[String, _] =
    Map(
      "metrics.jvm.enabled" -> false,
      "metrics.enabled"     -> false,
      "auditing.enabled"    -> false,
      "play.http.router"    -> "testOnlyDoNotUseInAppConf.Routes"
    )
}
