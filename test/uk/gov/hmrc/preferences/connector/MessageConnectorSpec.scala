/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.connector

import com.codahale.metrics.SharedMetricRegistries
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.*
import org.scalatest.matchers.should.Matchers.*
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach, Suite }
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.Application
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import uk.gov.hmrc.crypto.{ Decrypter, Encrypter }
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext
import play.api.inject.bind
import utils.FakeApplicationCrypto

class MessageConnectorSpec
    extends PlaySpec with MockitoSugar with GuiceOneAppPerTest with WithWireMockMessage with ScalaFutures {

  "Message connector " should {
    "return 200 when postMessage is called" in new TestClass {
      private val messageJson = Json.toJson(messageExample)

      givenThat(
        post(urlEqualTo("/secure-messaging/v4/message"))
          .withRequestBody(equalToJson(messageJson.toString))
          .willReturn(aResponse().withStatus(Status.CREATED))
      )

      messageConnector.postMessage(Json.toJson(messageExample)).futureValue.status shouldBe Status.CREATED
    }

    class TestClass {

      implicit val hc: HeaderCarrier = HeaderCarrier()
      implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

      val messageConnector: MessageConnector = app.injector.instanceOf[MessageConnector]

      val messageExample: String =
        s"""
           |{
           |"externalRef":{"id":"123412342314","source":"preferences"},
           |"recipient":{
           |  "taxIdentifier":{
           |    "name":"HMRC-NI",
           |    "value":"AB123456C"
           |   },
           |   "email":"someEmail@test.com",
           |   "name":{}
           |},
           |"messageType":"digitalOptInConfirmation",
           |"subject":"SUBJECT",
           |"content":"SGVsbG8gV29ybGQ=",
           |"alertDetails":{"templateId":"","recipientName":{},"data":{}}
           |}
           |""".stripMargin
    }
  }

  override def fakeApplication(): Application = new GuiceApplicationBuilder()
    .overrides(bind[Decrypter].toInstance(FakeApplicationCrypto))
    .build()
}

trait WithWireMockMessage extends BeforeAndAfterAll with BeforeAndAfterEach {
  suite: Suite =>

  def dependenciesPort = 22222

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
