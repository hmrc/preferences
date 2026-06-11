/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.connector

import com.codahale.metrics.SharedMetricRegistries
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{ aResponse, equalToJson, givenThat, post, urlEqualTo }
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach, Suite }
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status.OK
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.preferences.controllers.model.PreferencesChangedRequest
import uk.gov.hmrc.preferences.model.MessageDeliveryFormat.Digital

import java.time.Instant
import scala.concurrent.ExecutionContext
import uk.gov.hmrc.crypto.{ Decrypter, Encrypter }
import play.api.inject.bind
import utils.FakeApplicationCrypto

class PreferencesChangedNotifierConnectorSpec
    extends PlaySpec with ScalaFutures with GuiceOneAppPerSuite with MockitoSugar with IntegrationPatience
    with WireMockData {

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(
        "metrics.enabled"  -> false,
        "auditing.enabled" -> false
      )
      .overrides(bind[Decrypter].toInstance(FakeApplicationCrypto))
      .build()

  class TestCase {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]
    val connector = app.injector.instanceOf[PreferencesChangedNotifierConnector]

  }

  "PreferencesChangedNotifierConnector" should {
    "send correct message body and header" in new TestCase {
      private val pcr = PreferencesChangedRequest(
        changedValue = Digital,
        preferenceId = "pid",
        entityId = "eid",
        updatedAt = Instant.parse("2023-12-12T09:01:00.000Z"),
        taxIds = Map("nino" -> "AA001122B", "sautr" -> "sautr1"),
        bounced = false
      )

      private val jsonBody =
        s"""
           |{
           |"changedValue":"digital",
           |"preferenceId":"pid",
           |"entityId": "eid", 
           |"updatedAt":"2023-12-12T09:01:00.000Z",
           |"taxIds":{"nino":"AA001122B", "sautr":"sautr1"},
           |"bounced":false
           |}""".stripMargin

      givenThat(
        post(urlEqualTo("/preferences-changed"))
          .withRequestBody(equalToJson(jsonBody, true, true))
          .willReturn(aResponse())
      )

      private val response = connector.preferencesChanged(pcr).futureValue
      response.status mustBe OK
    }
  }
}

trait WireMockData extends BeforeAndAfterAll with BeforeAndAfterEach {
  suite: Suite =>

  def dependenciesPort = 22222

  lazy val wireMockServer = new WireMockServer(wireMockConfig().port(dependenciesPort))

  override def beforeAll(): Unit = {
    super.beforeAll()
    wireMockServer.start()
    SharedMetricRegistries.clear()
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
