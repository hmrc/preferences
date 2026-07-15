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

package controller

import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach, SuiteMixin }
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.http.Status.*
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Result
import play.api.test.Helpers.{ GET, contentAsJson, contentAsString, defaultAwaitTimeout, route, writeableOf_AnyContentAsEmpty }
import play.api.test.{ FakeRequest, Helpers }
import stubs.{ WireMockStubs, WireMockUtil }
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.http.{ HeaderCarrier, SessionKeys }
import uk.gov.hmrc.mongo.test.{ CleanMongoCollectionSupport, MongoSupport }
import uk.gov.hmrc.paperless.controllers.model.PreferenceResponse
import uk.gov.hmrc.preferences.model.*
import uk.gov.hmrc.preferences.model.EmailEventType.EmailVerified
import uk.gov.hmrc.preferences.model.Language.English
import uk.gov.hmrc.preferences.model.OptEventType.OptIn
import uk.gov.hmrc.preferences.model.PageType.IPage
import uk.gov.hmrc.preferences.model.TermsAndConditions.Accepted
import uk.gov.hmrc.preferences.repository.PreferencesRepository
import uk.gov.hmrc.preferences.util.Dc
import utils.{ FakeApplicationCrypto, GenerateRandom }

import java.time.Instant
import scala.concurrent.Future

class NewPreferencesControllerISpec
    extends PlaySpec with SuiteMixin with ScalaFutures with IntegrationPatience with GuiceOneServerPerSuite
    with MongoSupport with CleanMongoCollectionSupport with WireMockUtil with BeforeAndAfterEach with BeforeAndAfterAll
    with WireMockStubs {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val major = 222
  private val minor = 111

  override def fakeApplication(): Application =
    // If applicationMode is not set, use Mode.Test (the default for GuiceApplicationBuilder)
    GuiceApplicationBuilder()
      .configure(
        "metrics.enabled"                            -> false,
        "auditing.enabled"                           -> false,
        "metrics.graphite.enabled"                   -> false,
        "mongodb.uri"                                -> mongoUri,
        "play.http.router"                           -> "testOnlyDoNotUseInAppConf.Routes",
        "microservice.services.auth-login-api.port"  -> wireMockServer.port(),
        "microservice.services.auth.port"            -> wireMockServer.port(),
        "microservice.services.entity-resolver.port" -> wireMockServer.port()
      )
      .overrides(bind[Decrypter].toInstance(FakeApplicationCrypto))
      .build()

  "be updated with optInPage and returns majorVersion" in {
    val repository = app.injector.instanceOf[PreferencesRepository]
    val entityId = GenerateRandom.entityId()

    val p = Preferences(
      entityId,
      TermsAndConditions(
        Accepted(
          updatedAt = Dc.instantNow(),
          optInPage = Some(OptInPage(Version(major, minor), 1, PageType.IPage)),
          eventType = Some(OptEventType.OptIn)
        )
      )
    )
    repository.createOrUpdateTermsAndConditions(p, None).futureValue

    val url = s"/preferences/${entityId.value}"
    val request = FakeRequest(GET, url).withSession(SessionKeys.authToken -> "Bearer testToken")
    val response: Future[Result] = route(app, request).get

    val status = Helpers.status(response)
    val json = contentAsJson(response)
    status must be(OK)

    val genericTandCResponse = json.as[PreferenceResponse].termsAndConditions.get("generic")
    val version = for {
      genericTandC <- genericTandCResponse
      version      <- genericTandC.majorVersion
    } yield version
    version.get must be(major)
  }

  "be updated with optIn event information" in {
    val entityId = GenerateRandom.entityId()
    val emailId = GenerateRandom.email()
    val repository = app.injector.instanceOf[PreferencesRepository]

    val p = Preferences(
      entityId,
      TermsAndConditions(
        Accepted(
          updatedAt = Dc.instantNow(),
          optInPage = Some(OptInPage(Version(major, minor), 1, PageType.IPage)),
          eventType = Some(OptEventType.OptIn)
        )
      )
    )
    repository.createOrUpdateTermsAndConditions(p, None).futureValue
    val withId = repository.findBy(entityId).futureValue
    repository
      .setUnverifiedEmailAddress(
        entityId,
        PendingEmailAddress(
          email = emailId,
          verificationLink = Some(EmailVerificationLink(linkSentTime = Dc.instantNow()))
        ),
        event = Seq(
          OptInEvent(OptIn, OptInPage(Version(1, 0), 8, IPage), entityId, Instant.now(), English, Some(false)),
          EmailEvent(entityId, EmailVerified, emailId, None, Instant.now())
        )
      )
      .futureValue

    repository
      .markEmailVerified(
        withId.get._id,
        PendingEmailAddress(
          email = emailId,
          verificationLink = Some(EmailVerificationLink(linkSentTime = Dc.instantNow()))
        ),
        language = None,
        event = None
      )
      .futureValue

    val url = s"/preferences-admin/events/${entityId.value}"
    val request = FakeRequest(GET, url).withSession(SessionKeys.authToken -> "Bearer testToken")
    val response: Future[Result] = route(app, request).get

    val status = Helpers.status(response)
    val events = contentAsString(response)
    status must be(OK)
    events must include("opt-in")
    events must include(EmailEventType.EmailVerified.entryName)
    events must include(emailId)
  }
}
