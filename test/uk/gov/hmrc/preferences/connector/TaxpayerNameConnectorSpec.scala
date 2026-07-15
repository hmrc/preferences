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
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{ aResponse, get, givenThat, urlEqualTo }
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.scalatest.matchers.should.Matchers.*
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach, Suite }
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.Application
import play.api.http.Status
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.crypto.{ Decrypter, Encrypter }
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.preferences.model.TaxpayerName
import utils.LogCapturing
import utils.FakeApplicationCrypto

import scala.concurrent.ExecutionContext

class TaxpayerNameConnectorSpec
    extends PlaySpec with ScalaFutures with GuiceOneAppPerTest with MockitoSugar with WithWireMockTaxpayerData
    with LogCapturing with IntegrationPatience {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  private val sautr = SaUtr("2000029888")

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure("auditing.enabled" -> false)
      .overrides(bind[Decrypter].toInstance(FakeApplicationCrypto))
      .build()

  "getTaxpayerName" should {
    "return TaxpayerName when retrieving a full taxpayer details payload" in new TestCase {
      private val jsonBody =
        """{
          |    "name" : {
          |        "title": "Mr",
          |        "forename": "Erbert",
          |        "secondForename": "Donaldson",
          |        "surname": "Ducking",
          |        "honours": "KCBE"
          |    },
          |    "address": {
          |        "addressLine1": "42 Somewhere's Street",
          |        "addressLine2": "London",
          |        "addressLine3": "Greater London",
          |        "addressLine4": "",
          |        "addressLine5": "",
          |        "postcode": "WO9H 8AA",
          |        "foreignCountry": null,
          |        "returnedLetter": true,
          |        "additionalDeliveryInformation": "Leave by door"
          |    },
          |    "contact": {
          |        "telephone": {
          |            "daytime": "02654321#1235",
          |            "evening": "027123456",
          |            "mobile": "07676767",
          |            "fax": "0209798969"
          |        },
          |        "email": {
          |            "primary": "erbert@notthere.co.uk"
          |        },
          |        "other": {}
          |    }
          |}
          | """.stripMargin
      givenThat(
        get(urlEqualTo("/self-assessment/individual/2000029888/designatory-details/taxpayer")).willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withBody(jsonBody)
        )
      )

      connector.getTaxpayerName(sautr).futureValue should be(
        Some(
          TaxpayerName(
            Some("Mr"),
            Some("Erbert"),
            Some("Donaldson"),
            Some("Ducking"),
            Some("KCBE")
          )
        )
      )
    }

    "return TaxpayerName when retrieving a partial taxpayer details payload which includes a full name object" in new TestCase {
      private val jsonBody =
        """{
          |    "name" : {
          |        "title": "Mr",
          |        "forename": "Erbert",
          |        "secondForename": "Donaldson",
          |        "surname": "Ducking",
          |        "honours": "KCBE"
          |    }
          |}
          | """.stripMargin
      givenThat(
        get(urlEqualTo("/self-assessment/individual/2000029888/designatory-details/taxpayer")).willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withBody(jsonBody)
        )
      )

      connector.getTaxpayerName(sautr).futureValue should be(
        Some(
          TaxpayerName(
            Some("Mr"),
            Some("Erbert"),
            Some("Donaldson"),
            Some("Ducking"),
            Some("KCBE")
          )
        )
      )
    }

    "return None when JSON holds no naming details" in new TestCase {
      private val jsonBody =
        """{
          |    "address": {
          |        "addressLine1": "42 Somewhere's Street",
          |        "addressLine2": "London",
          |        "addressLine3": "Greater London",
          |        "addressLine4": "",
          |        "addressLine5": "",
          |        "postcode": "WO9H 8AA",
          |        "foreignCountry": null,
          |        "returnedLetter": true,
          |        "additionalDeliveryInformation": "Leave by door"
          |    },
          |    "contact": {
          |        "telephone": {
          |            "daytime": "02654321#1235",
          |            "evening": "027123456",
          |            "mobile": "07676767",
          |            "fax": "0209798969"
          |        },
          |        "email": {
          |            "primary": "erbert@notthere.co.uk"
          |        },
          |        "other": {}
          |    }
          |}
          | """.stripMargin
      givenThat(
        get(urlEqualTo("/self-assessment/individual/2000029888/designatory-details/taxpayer")).willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withBody(jsonBody)
        )
      )

      connector.getTaxpayerName(sautr).futureValue should be(None)
    }

    "return None when JSON body is empty" in new TestCase {
      givenThat(
        get(urlEqualTo("/self-assessment/individual/2000029888/designatory-details/taxpayer")).willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withBody("{}")
        )
      )

      connector.getTaxpayerName(sautr).futureValue should be(None)
    }
  }

  "Taxpayer connector" should {
    "log a warn level log message and return an empty TaxpayerName on 5** or non 404 4** errors" in new TestCase {
      val logger = play.api.Logger(connector.getClass).underlyingLogger.asInstanceOf[LogbackLogger]
      withCaptureOfLoggingFrom(logger) { logEvents =>
        givenThat(
          get(urlEqualTo("/self-assessment/individual/2000029888/designatory-details/taxpayer")).willReturn(
            aResponse()
              .withStatus(Status.INTERNAL_SERVER_ERROR)
          )
        )
        connector.getTaxpayerName(sautr).futureValue should be(None)
        logEvents.head.getMessage should be(s"Unable to get taxpayer name for utr: ${sautr.value}, 500")

        givenThat(
          get(urlEqualTo("/self-assessment/individual/2000029888/designatory-details/taxpayer")).willReturn(
            aResponse()
              .withStatus(Status.NOT_IMPLEMENTED)
          )
        )
        connector.getTaxpayerName(sautr).futureValue should be(None)
        logEvents.head.getMessage should be(s"Unable to get taxpayer name for utr: ${sautr.value}, 500")

        givenThat(
          get(urlEqualTo("/self-assessment/individual/2000029888/designatory-details/taxpayer")).willReturn(
            aResponse()
              .withStatus(Status.UNAUTHORIZED)
          )
        )
        connector.getTaxpayerName(sautr).futureValue should be(None)
        logEvents.head.getMessage should be(s"Unable to get taxpayer name for utr: ${sautr.value}, 500")

        logEvents.count(_.getLevel == Level.WARN) should be(3)
      }
    }

    "log an info level log message and return empty TaxpayerName on 404 error" in new TestCase {
      val logger = play.api.Logger(connector.getClass).underlyingLogger.asInstanceOf[LogbackLogger]
      withCaptureOfLoggingFrom(logger) { logEvents =>
        givenThat(
          get(urlEqualTo("/self-assessment/individual/2000029888/designatory-details/taxpayer")).willReturn(
            aResponse()
              .withStatus(Status.NOT_FOUND)
          )
        )
        connector.getTaxpayerName(sautr).futureValue should be(None)
        logEvents.head.getLevel should be(Level.INFO)
        logEvents.head.getMessage should be(s"No taxpayer name found for utr: ${sautr.value}")
      }
    }
  }

  class TestCase {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

    val connector: TaxpayerConnector = app.injector.instanceOf[TaxpayerConnector]

  }

}

trait WithWireMockTaxpayerData extends BeforeAndAfterAll with BeforeAndAfterEach {
  suite: Suite =>

  def dependenciesPort = 8091

  lazy val wireMockServer = new WireMockServer(wireMockConfig().port(dependenciesPort))

  override def beforeAll(): Unit = {
    super.beforeAll()
    wireMockServer.start()
    WireMock.configureFor(dependenciesPort)
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    wireMockServer.resetMappings()
    wireMockServer.resetRequests()
  }
  override def afterAll(): Unit = {
    super.afterAll()
    wireMockServer.stop()
  }

}
