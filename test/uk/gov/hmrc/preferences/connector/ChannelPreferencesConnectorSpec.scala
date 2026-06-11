/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.connector

import com.codahale.metrics.SharedMetricRegistries
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{ aResponse, givenThat, post, urlEqualTo }
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers.*
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach, Suite }
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.Application
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{ JsResultException, Json }
import uk.gov.hmrc.crypto.{ Decrypter, Encrypter }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.preferences.model.TaxId
import utils.{ FakeApplicationCrypto, GenerateRandom }
import utils.TestData.TEST_ENROLMENT

import scala.concurrent.ExecutionContext
import play.api.inject.bind

class ChannelPreferencesConnectorSpec
    extends PlaySpec with ScalaFutures with GuiceOneAppPerTest with MockitoSugar with ChannelPreferencesWireMock
    with IntegrationPatience {

  "updatePreferencesForItsa" should {
    "return true on successful response " in new TestCase {
      givenThat(
        post(urlEqualTo("/channel-preferences/preference/itsa/status"))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
          )
      )

      connector.updatePreferencesForItsa(testTaxId, true, None).futureValue should be(true)
    }

    "return false on BAD_REQUEST response " in new TestCase {
      givenThat(
        post(urlEqualTo("/channel-preferences/preference/itsa/status"))
          .willReturn(
            aResponse()
              .withStatus(Status.BAD_REQUEST)
          )
      )

      connector.updatePreferencesForItsa(testTaxId, true, None).futureValue should be(false)
    }

    "return false on NOT_FOUND response " in new TestCase {
      givenThat(
        post(urlEqualTo("/channel-preferences/itsa/status"))
          .willReturn(
            aResponse()
              .withStatus(Status.NOT_FOUND)
          )
      )

      connector.updatePreferencesForItsa(testTaxId, true, None).futureValue should be(false)
    }

    "return false on BAD_GATEWAY response " in new TestCase {
      givenThat(
        post(urlEqualTo("/channel-preferences/preference/itsa/status"))
          .willReturn(
            aResponse()
              .withStatus(Status.BAD_GATEWAY)
          )
      )

      connector.updatePreferencesForItsa(testTaxId, true, None).futureValue should be(false)
    }
  }

  "formats" should {

    "read the json correctly" in new TestCase {
      import connector.formats

      Json.parse(preferenceStatusJsonString).as[connector.PreferenceStatus] mustBe preferenceStatus
    }

    "throw exception for invalid json" in new TestCase {
      import connector.formats

      intercept[JsResultException] {
        Json.parse(preferenceStatusInvalidJsonString).as[connector.PreferenceStatus]
      }
    }

    "write the object correctly" in new TestCase {
      Json.toJson(preferenceStatus) mustBe Json.parse(preferenceStatusJsonString)
    }
  }

  class TestCase {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

    val testTaxId: TaxId = TaxId(
      GenerateRandom.entityId().value,
      Some(GenerateRandom.utr().value),
      Some(GenerateRandom.nino().value),
      Some("X12345678901234")
    )

    val connector: ChannelPreferencesConnector = app.injector.instanceOf[ChannelPreferencesConnector]

    val preferenceStatus: connector.PreferenceStatus =
      connector.PreferenceStatus(enrolment = TEST_ENROLMENT, status = true)

    val preferenceStatusJsonString: String = """{"enrolment":"test_enrolment","status":true}""".stripMargin
    val preferenceStatusInvalidJsonString: String = """{"enrolment":"test_enrolment"}""".stripMargin
  }

  override def fakeApplication(): Application = new GuiceApplicationBuilder()
    .configure(
      "metrics.jvm"                                    -> false,
      "channel-preferences.port"                       -> dependenciesPort,
      "microservice.services.channel-preferences.port" -> dependenciesPort
    )
    .overrides(bind[Decrypter].toInstance(FakeApplicationCrypto))
    .build()

}

trait ChannelPreferencesWireMock extends BeforeAndAfterAll with BeforeAndAfterEach {
  suite: Suite =>

  def dependenciesPort = 22223

  lazy val wireMockServer = new WireMockServer(wireMockConfig().port(dependenciesPort))

  override def beforeAll(): Unit = {
    super.beforeAll()
    wireMockServer.start()
    WireMock.configureFor(dependenciesPort)
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

}
