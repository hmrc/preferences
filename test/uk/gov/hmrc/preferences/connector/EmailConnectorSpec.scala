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

package uk.gov.hmrc.preferences.connector

import com.codahale.metrics.SharedMetricRegistries
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{ aResponse, givenThat, post, urlEqualTo }
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatest.matchers.should.Matchers.*
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach, Suite }
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.Application
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{ JsResultException, Json }
import uk.gov.hmrc.crypto.{ Decrypter, Encrypter }
import uk.gov.hmrc.http.HeaderCarrier
import utils.TestData.{ TEST_CODE, TEST_EMAIL, TEST_FORM_TYPE, TEST_NINO, TEST_SOURCE, TEST_TEMPLATE_ID, TEST_TIME_INSTANT, TEST_TO_ADRESS }

import java.time.Instant
import scala.concurrent.ExecutionContext
import play.api.inject.bind
import utils.FakeApplicationCrypto

class EmailConnectorSpec
    extends PlaySpec with ScalaFutures with GuiceOneAppPerTest with MockitoSugar with WithEmailConnectorWireMock
    with IntegrationPatience {

  "send templated email" should {

    "send digital optin verification " in new TestCase {
      givenThat(post(urlEqualTo(s"/hmrc/email")).willReturn(aResponse().withStatus(Status.ACCEPTED)))
      connector.sendDigitalOptInEmailVerification("to", "verification-link", force = false).futureValue should be(())
    }

    "send digital optin verification fails" in new TestCase {
      givenThat(post(urlEqualTo(s"/hmrc/email")).willReturn(aResponse().withStatus(Status.INTERNAL_SERVER_ERROR)))
      the[Exception] thrownBy {
        connector.sendDigitalOptInEmailVerification("to", "verification-link", force = false).futureValue
      } should have message "The future returned an exception of type: uk.gov.hmrc.http.HttpException, " +
        "with message: Unexpected response (500) from email service."
    }

    "sendEmailChangedNotification" in new TestCase {
      givenThat(post(urlEqualTo(s"/hmrc/email")).willReturn(aResponse().withStatus(Status.ACCEPTED)))
      connector.sendEmailChangedNotification("to").futureValue should be(())
    }

    "sendDigitalOptOutEmail" in new TestCase {
      givenThat(post(urlEqualTo(s"/hmrc/email")).willReturn(aResponse().withStatus(Status.ACCEPTED)))
      connector.sendDigitalOptOutEmail("to").futureValue should be(())
    }

    "sendChangedEmailAddressVerification" in new TestCase {
      givenThat(post(urlEqualTo(s"/hmrc/email")).willReturn(aResponse().withStatus(Status.ACCEPTED)))
      connector.sendChangedEmailAddressVerification("to", "verification-link").futureValue should be(())
    }

    "sendVerificationReminder" in new TestCase {
      givenThat(post(urlEqualTo(s"/hmrc/email")).willReturn(aResponse().withStatus(Status.ACCEPTED)))
      connector.sendVerificationReminder("to", "verification-link", "days-ago").futureValue should be(())
    }
  }

  "Bounce.formats" should {
    import Bounce.formats

    "read the json correctly" in new Setup {
      Json.parse(bounceJsonString).as[Bounce] mustBe bounce
    }

    "throw exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(bounceInvalidJsonString).as[Bounce]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(bounce) mustBe Json.parse(bounceJsonString)
    }
  }

  "SendTemplatedEmailRequest.formats" should {
    import SendTemplatedEmailRequest.formats

    "read the json correctly" in new Setup {
      Json.parse(sendTemplatedEmailRequestJsonString).as[SendTemplatedEmailRequest] mustBe sendTemplatedEmailReq
    }

    "throw exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(sendTemplatedEmailRequestInvalidJsonString).as[SendTemplatedEmailRequest]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(sendTemplatedEmailReq) mustBe Json.parse(sendTemplatedEmailRequestJsonString)
    }
  }

  class TestCase {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

    val connector: EmailConnector = app.injector.instanceOf[EmailConnector]
  }

  trait Setup {
    val bounce: Bounce = Bounce(
      emailAddress = TEST_EMAIL,
      detected = TEST_TIME_INSTANT,
      code = Some(TEST_CODE),
      emailSource = Some(TEST_SOURCE),
      formType = Some(TEST_FORM_TYPE),
      nino = Some(TEST_NINO)
    )

    val sendTemplatedEmailReq: SendTemplatedEmailRequest = SendTemplatedEmailRequest(
      to = List(TEST_TO_ADRESS),
      templateId = TEST_TEMPLATE_ID,
      parameters = Map(),
      force = true
    )

    val bounceJsonString: String =
      """{
        |"emailAddress":"test@test.com",
        |"detected":"1972-02-24T21:04:16.000Z",
        |"code":156,
        |"emailSource":"test_source",
        |"formType":"test_form_type",
        |"nino":"AB112233A"
        |}""".stripMargin

    val bounceInvalidJsonString: String =
      """{
        |"detected":"1972-02-24T21:04:16.000Z",
        |"code":156,
        |"emailSource":"test_source",
        |"formType":"test_form_type",
        |"nino":"AB112233A"
        |}""".stripMargin

    val sendTemplatedEmailRequestJsonString: String =
      """{"to":["test_to_adress"],"templateId":"test_template","parameters":{},"force":true}""".stripMargin

    val sendTemplatedEmailRequestInvalidJsonString: String =
      """{"to":["test_to_adress"],"parameters":{},"force":true}""".stripMargin
  }

  override def fakeApplication(): Application = new GuiceApplicationBuilder()
    .overrides(bind[Decrypter].toInstance(FakeApplicationCrypto))
    .build()

}

trait WithEmailConnectorWireMock extends BeforeAndAfterAll with BeforeAndAfterEach {
  suite: Suite =>

  def dependenciesPort = 22222

  lazy val wireMockServer = new WireMockServer(wireMockConfig().port(dependenciesPort))

  override def beforeEach(): Unit = {
    super.beforeEach()
    SharedMetricRegistries.clear()
    wireMockServer.resetMappings()
    wireMockServer.resetRequests()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    wireMockServer.stop()
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    wireMockServer.start()
    SharedMetricRegistries.clear()
    WireMock.configureFor(dependenciesPort)
  }
}
