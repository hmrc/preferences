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

import ch.qos.logback.classic.{ Level, Logger as LogbackLogger }
import com.codahale.metrics.SharedMetricRegistries
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{ aResponse, get, givenThat, urlEqualTo }
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers.*
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach, Suite }
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.Application
import play.api.http.Status
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{ JsResultException, Json }
import uk.gov.hmrc.crypto.{ Decrypter, Encrypter }
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.preferences.model.{ TaxId, TaxpayerName }
import utils.{ FakeApplicationCrypto, LogCapturing }
import utils.TestData.{ TEST_FORENAME, TEST_SUR_NAME, TEST_TITLE }

import scala.concurrent.ExecutionContext

class CitizenDetailsConnectorSpec
    extends PlaySpec with ScalaFutures with MockitoSugar with BeforeAndAfterAll with BeforeAndAfterEach
    with LogCapturing with IntegrationPatience {

  lazy val wireMockServer = new WireMockServer(
    wireMockConfig()
      .dynamicHttpsPort()
      .dynamicPort()
  )

  lazy val port: Int = wireMockServer.port()

  override def beforeAll(): Unit = {
    super.beforeAll()
    wireMockServer.start()

    SharedMetricRegistries.clear()
    WireMock.configureFor(wireMockServer.port())
  }

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

  "getCitizenDetails" should {
    "return CitizenDetails " in new TestCase {
      private val jsonBody = s"""{"firstName": "firstName", "lastName": "lastName", "title":"Ms", "nino":"$ninoStr"}"""
      wireMockServer.addStubMapping(
        get(urlEqualTo(s"/citizen-details/$nino/designatory-details/basic"))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withBody(jsonBody)
          )
          .build()
      )

      connector.getTaxpayerName(nino).futureValue should be(
        Some(
          TaxpayerName(
            forename = Some("firstName"),
            surname = Some("lastName"),
            title = Some("Ms")
          )
        )
      )
    }

  }

  "CitizenDetailsConnector connector" should {
    "log a warn level log message and return an empty CitizenDetails on 5** or non 404 4** errors" in new TestCase {
      val logger = play.api.Logger(connector.getClass).underlyingLogger.asInstanceOf[LogbackLogger]
      withCaptureOfLoggingFrom(logger) { logEvents =>
        wireMockServer.addStubMapping(
          get(urlEqualTo(s"/citizen-details/$nino/designatory-details/basic"))
            .willReturn(
              aResponse()
                .withStatus(Status.INTERNAL_SERVER_ERROR)
            )
            .build()
        )
        connector.getTaxpayerName(nino).futureValue should be(None)
        logEvents.head.getMessage should be(s"Unable to get citizen details for nino: ${nino.value}")

        wireMockServer.addStubMapping(
          get(urlEqualTo(s"/citizen-details/$nino/designatory-details/basic"))
            .willReturn(
              aResponse()
                .withStatus(Status.NOT_IMPLEMENTED)
            )
            .build()
        )
        connector.getTaxpayerName(nino).futureValue should be(None)
        logEvents.head.getMessage should be(s"Unable to get citizen details for nino: ${nino.value}")

        wireMockServer.addStubMapping(
          get(urlEqualTo(s"/citizen-details/$nino/designatory-details/basic"))
            .willReturn(
              aResponse()
                .withStatus(Status.UNAUTHORIZED)
            )
            .build()
        )
        connector.getTaxpayerName(nino).futureValue should be(None)
        logEvents.head.getMessage should be(s"Unable to get citizen details for nino: ${nino.value}")

        logEvents.count(_.getLevel == Level.WARN) should be(3)
      }
    }

    "log an info level log message and return empty CitizenDetails on 404 error" in new TestCase {
      val logger = play.api.Logger(connector.getClass).underlyingLogger.asInstanceOf[LogbackLogger]
      withCaptureOfLoggingFrom(logger) { logEvents =>
        wireMockServer.addStubMapping(
          get(urlEqualTo(s"/citizen-details/$nino/designatory-details/basic"))
            .willReturn(
              aResponse().withStatus(Status.NOT_FOUND)
            )
            .build()
        )
        connector.getTaxpayerName(nino).futureValue should be(None)
        logEvents.head.getMessage should be(s"No citizen details found for nino: $nino")
      }
    }
  }

  "CitizenDetails.formats" should {
    import CitizenDetails.formats

    "read the json correctly" in new Setup {
      Json.parse(citizenDetailsJsonString).as[CitizenDetails] mustBe citizenDetails
    }

    "throw exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(citizenDetailsInvalidJsonString).as[CitizenDetails]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(citizenDetails) mustBe Json.parse(citizenDetailsJsonString)
    }
  }

  class TestCase {
    val app: Application = new GuiceApplicationBuilder()
      .configure("metrics.enabled" -> false)
      .configure("auditing.enabled" -> false)
      .configure("metrics.graphite.enabled" -> false)
      .configure("microservice.services.citizen-details.port" -> port)
      .overrides(bind[Decrypter].toInstance(FakeApplicationCrypto))
      .build()

    implicit val hc: HeaderCarrier = HeaderCarrier()
    implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

    val ninoStr = "CA123456A"
    val nino: Nino = Nino(ninoStr)
    val connector: CitizenDetailsConnector = app.injector.instanceOf[CitizenDetailsConnector]
  }

  trait Setup {
    val citizenDetails: CitizenDetails = CitizenDetails(
      firstName = Some(TEST_FORENAME),
      lastName = Some(TEST_SUR_NAME),
      title = Some(TEST_TITLE),
      deceased = Some(false)
    )

    val citizenDetailsJsonString: String =
      """{"firstName":"test_forename","lastName":"test_last_name","title":"test_title","deceased":false}""".stripMargin

    val citizenDetailsInvalidJsonString: String =
      """{"firstName":"test_forename","lastName":"test_last_name","title":"test_title","deceased":0}""".stripMargin
  }
}
