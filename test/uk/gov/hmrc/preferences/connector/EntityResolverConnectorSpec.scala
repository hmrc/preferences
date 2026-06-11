/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.connector

import com.codahale.metrics.SharedMetricRegistries
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatest.matchers.should.Matchers.*
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach, Suite }
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.Application
import play.api.http.Status.OK
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import uk.gov.hmrc.crypto.{ Decrypter, Encrypter }
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.preferences.exceptions.*
import uk.gov.hmrc.preferences.model.{ EntityId, TaxId }
import utils.{ FakeApplicationCrypto, GenerateRandom }

import java.net.URL
import scala.concurrent.ExecutionContext
import play.api.inject.bind

class EntityResolverConnectorSpec
    extends PlaySpec with ScalaFutures with GuiceOneAppPerTest with MockitoSugar with WithWireMock
    with IntegrationPatience {

  "get resolver url" should {
    "get raw url with no querystring" in new TestCase {
      val url: URL = connector.getEntityResolverUrl(false, None, None)
      url.toString mustBe "http://localhost:22222/entity-resolver"
    }

    "get raw regime url with no querystring" in new TestCase {
      val url: URL = connector.getEntityResolverUrl(true, None, None)
      url.toString mustBe "http://localhost:22222/regime/entity-resolver"
    }

    "get resolve querystring" in new TestCase {
      val url: URL = connector.getEntityResolverUrl(false, Some(true), None)
      url.toString mustBe "http://localhost:22222/entity-resolver?resolve=true"
    }

    "get correct resolve querystring when explicitly set to false" in new TestCase {
      val url: URL = connector.getEntityResolverUrl(false, Some(false), None)
      url.toString mustBe "http://localhost:22222/entity-resolver?resolve=false"
    }

    "get resolve and resolveIds querystring" in new TestCase {
      val url: URL = connector.getEntityResolverUrl(false, Some(true), Some(true))
      url.toString mustBe "http://localhost:22222/entity-resolver?resolve=true&resolveIds=true"
    }

    "get regime url with resolve and resolveIds querystring" in new TestCase {
      val url: URL = connector.getEntityResolverUrl(true, Some(true), Some(true))
      url.toString mustBe "http://localhost:22222/regime/entity-resolver?resolve=true&resolveIds=true"
    }
  }

  "getTaxId" should {
    "return TaxId with sautr" in new TestCase {
      givenThat(
        get(urlEqualTo("/entity-resolver?entityId=7ca752a5-14e4-4c29-bfb2-13bcd785e713")).willReturn(
          aResponse()
            .withStatus(200)
            .withBody("{\"_id\": \"7ca752a5-14e4-4c29-bfb2-13bcd785e713\", \"sautr\": \"2000029888\"}")
        )
      )

      connector.getTaxId(EntityId("7ca752a5-14e4-4c29-bfb2-13bcd785e713")).futureValue should be(
        TaxId("7ca752a5-14e4-4c29-bfb2-13bcd785e713", Some("2000029888"), None)
      )
    }

    "return TaxId with nino" in new TestCase {
      givenThat(
        get(urlEqualTo("/entity-resolver?entityId=7ca752a5-14e4-4c29-bfb2-13bcd785e713")).willReturn(
          aResponse()
            .withStatus(200)
            .withBody("{\"_id\": \"7ca752a5-14e4-4c29-bfb2-13bcd785e713\", \"nino\": \"XY047088B\"}")
        )
      )

      connector.getTaxId(EntityId("7ca752a5-14e4-4c29-bfb2-13bcd785e713")).futureValue should be(
        TaxId("7ca752a5-14e4-4c29-bfb2-13bcd785e713", None, Some("XY047088B"))
      )
    }

    "return TaxId with hmrcMtdItsa" in new TestCase {
      givenThat(
        get(urlEqualTo("/entity-resolver?entityId=7ca752a5-14e4-4c29-bfb2-13bcd785e713")).willReturn(
          aResponse()
            .withStatus(200)
            .withBody("{\"_id\": \"7ca752a5-14e4-4c29-bfb2-13bcd785e713\", \"HMRC-MTD-IT\": \"XY047088B\"}")
        )
      )

      connector.getTaxId(EntityId("7ca752a5-14e4-4c29-bfb2-13bcd785e713")).futureValue should be(
        TaxId("7ca752a5-14e4-4c29-bfb2-13bcd785e713", None, None, Some("XY047088B"))
      )
    }
  }

  "getTaxIdOption" should {
    "return TaxId with sautr" in new TestCase {
      givenThat(
        get(urlEqualTo("/entity-resolver?entityId=7ca752a5-14e4-4c29-bfb2-13bcd785e713")).willReturn(
          aResponse()
            .withStatus(200)
            .withBody("{\"_id\": \"7ca752a5-14e4-4c29-bfb2-13bcd785e713\", \"sautr\": \"2000029888\"}")
        )
      )

      connector.getTaxIdOption(EntityId("7ca752a5-14e4-4c29-bfb2-13bcd785e713")).futureValue should be(
        Some(TaxId("7ca752a5-14e4-4c29-bfb2-13bcd785e713", Some("2000029888"), None))
      )
    }

    "return TaxId with nino" in new TestCase {
      givenThat(
        get(urlEqualTo("/entity-resolver?entityId=7ca752a5-14e4-4c29-bfb2-13bcd785e713")).willReturn(
          aResponse()
            .withStatus(200)
            .withBody("{\"_id\": \"7ca752a5-14e4-4c29-bfb2-13bcd785e713\", \"nino\": \"XY047088B\"}")
        )
      )

      connector.getTaxIdOption(EntityId("7ca752a5-14e4-4c29-bfb2-13bcd785e713")).futureValue should be(
        Some(TaxId("7ca752a5-14e4-4c29-bfb2-13bcd785e713", None, Some("XY047088B")))
      )
    }

    "return TaxId with hmrcMtdItsa" in new TestCase {
      givenThat(
        get(urlEqualTo("/entity-resolver?entityId=7ca752a5-14e4-4c29-bfb2-13bcd785e713")).willReturn(
          aResponse()
            .withStatus(200)
            .withBody("{\"_id\": \"7ca752a5-14e4-4c29-bfb2-13bcd785e713\", \"HMRC-MTD-IT\": \"XY047088B\"}")
        )
      )

      connector.getTaxIdOption(EntityId("7ca752a5-14e4-4c29-bfb2-13bcd785e713")).futureValue should be(
        Some(TaxId("7ca752a5-14e4-4c29-bfb2-13bcd785e713", None, None, Some("XY047088B")))
      )
    }

    "return None when no entry exists for the given entityId" in new TestCase {
      givenThat(
        get(urlEqualTo("/entity-resolver?entityId=7ca752a5-14e4-4c29-bfb2-13bcd785e713")).willReturn(
          aResponse()
            .withStatus(404)
        )
      )

      connector.getTaxIdOption(EntityId("7ca752a5-14e4-4c29-bfb2-13bcd785e713")).futureValue should be(None)
    }
  }

  "updateEntity" should {

    "return UnsetMarkDeEnrolment" in new TestCase {
      givenThat(
        get(urlEqualTo("/preferences/checkAndDelete/7ca752a5-14e4-4c29-bfb2-13bcd785e713/sautr")).willReturn(
          aResponse()
            .withStatus(200)
            .withBody("{\"reason\": \"UNSET_MARK_DE_ENROLMENT\"}")
        )
      )

      connector.updateEntity(EntityId("7ca752a5-14e4-4c29-bfb2-13bcd785e713"), "sautr").futureValue should be(
        UnsetMarkDeEnrolment
      )
    }

    "return DeletePreferences" in new TestCase {
      givenThat(
        get(urlEqualTo("/preferences/checkAndDelete/7ca752a5-14e4-4c29-bfb2-13bcd785e713/sautr")).willReturn(
          aResponse()
            .withStatus(200)
            .withBody("{\"reason\": \"DELETE_PREFERENCES\"}")
        )
      )

      connector.updateEntity(EntityId("7ca752a5-14e4-4c29-bfb2-13bcd785e713"), "sautr").futureValue should be(
        DeletePreferences
      )
    }

    "return DoNotProcess" in new TestCase {
      givenThat(
        get(urlEqualTo("/preferences/checkAndDelete/7ca752a5-14e4-4c29-bfb2-13bcd785e713/sautr")).willReturn(
          aResponse()
            .withStatus(200)
            .withBody("{\"reason\": \"SOMETHING_ELSE\"}")
        )
      )

      connector.updateEntity(EntityId("7ca752a5-14e4-4c29-bfb2-13bcd785e713"), "sautr").futureValue should be(
        DoNotProcess
      )
    }

    "return InvalidEntity" in new TestCase {
      givenThat(
        get(urlEqualTo("/preferences/checkAndDelete/9ca752a5-14e4-4c29-bfb2-13bcd785e713/sautr")).willReturn(
          aResponse()
            .withStatus(404)
            .withBody("{\"reason\": \"INVALID_ENTITY\"}")
        )
      )

      connector.updateEntity(EntityId("9ca752a5-14e4-4c29-bfb2-13bcd785e713"), "sautr").futureValue should be(
        InvalidEntity
      )
    }

    "return EntityProcessError" in new TestCase {
      givenThat(
        get(urlEqualTo("/preferences/checkAndDelete/7ca752a5-14e4-4c29-bfb2-13bcd785e713/sautr")).willReturn(
          aResponse()
            .withStatus(400)
        )
      )

      connector.updateEntity(EntityId("7ca752a5-14e4-4c29-bfb2-13bcd785e713"), "sautr").futureValue should be(
        EntityProcessError
      )
    }
  }

  "getEntityIdByTaxId" should {
    "return an EntityId when the HTTP request is successful" in new TestCase {

      private val utr: SaUtr = GenerateRandom.utr()
      private val entityId = GenerateRandom.entityId()

      givenThat(
        get(urlEqualTo(s"/entity-resolver?taxRegime=${utr.name}&taxId=${utr.value}")).willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(
              Json
                .obj(
                  "_id"    -> entityId.value,
                  utr.name -> utr.value
                )
                .toString
            )
        )
      )
      connector.getEntityIdByTaxId(utr.name, utr.value).value.futureValue shouldBe (Right(entityId))
    }

    "EntityNotFound  when response is 404" in new TestCase {
      private val utr: SaUtr = GenerateRandom.utr()

      givenThat(
        get(urlEqualTo(s"/entity-resolver?taxRegime=${utr.name}&taxId=${utr.value}")).willReturn(
          notFound()
        )
      )
      connector.getEntityIdByTaxId(utr.name, utr.value).value.futureValue shouldBe Left(EntityNotFound)
    }

    "EntityRequestServerError when response is 500" in new TestCase {
      private val utr: SaUtr = GenerateRandom.utr()

      givenThat(
        get(urlEqualTo(s"/entity-resolver?taxRegime=${utr.name}&taxId=${utr.value}")).willReturn(
          serverError()
        )
      )
      connector.getEntityIdByTaxId(utr.name, utr.value).value.futureValue shouldBe Left(EntityRequestServerError(""))
    }
  }

  "getEntityIdFromAuth" should {
    "return an EntityId when the HTTP request is successful" in new TestCase {

      private val utr: SaUtr = GenerateRandom.utr()
      private val entityId = GenerateRandom.entityId()

      givenThat(
        get(urlEqualTo(s"/entity-resolver")).willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(
              Json
                .obj(
                  "_id"    -> entityId.value,
                  utr.name -> utr.value
                )
                .toString
            )
        )
      )
      connector.getEntityIdByAuth(None).value.futureValue shouldBe Right(entityId)
    }

    "return an Entity when the HTTP request is successful, bypassing resolution" in new TestCase {
      private val utr: SaUtr = GenerateRandom.utr()
      private val entityId = GenerateRandom.entityId()

      givenThat(
        get(urlEqualTo(s"/entity-resolver?resolve=false")).willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(
              Json
                .obj(
                  "_id"    -> entityId.value,
                  utr.name -> utr.value
                )
                .toString
            )
        )
      )
      connector.getEntityIdByAuth(Some(false)).value.futureValue shouldBe Right(entityId)
    }

    "return an Entity when the HTTP request is successful, requesting resolution" in new TestCase {
      private val utr: SaUtr = GenerateRandom.utr()
      private val entityId = GenerateRandom.entityId()

      givenThat(
        get(urlEqualTo(s"/entity-resolver?resolve=true")).willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(
              Json
                .obj(
                  "_id"    -> entityId.value,
                  utr.name -> utr.value
                )
                .toString
            )
        )
      )
      connector.getEntityIdByAuth(Some(true)).value.futureValue shouldBe Right(entityId)
    }

    "EntityNotFound  when response is 404" in new TestCase {
      givenThat(
        get(urlEqualTo(s"/entity-resolver")).willReturn(
          notFound()
        )
      )
      connector.getEntityIdByAuth(None).value.futureValue shouldBe Left(EntityNotFound)
    }

    "EntityResponseAuthFailed  when response is 404" in new TestCase {
      givenThat(
        get(urlEqualTo(s"/entity-resolver")).willReturn(
          unauthorized()
        )
      )
      connector.getEntityIdByAuth(None).value.futureValue shouldBe Left(EntityUnauthorised(""))
    }

    "EntityRequestServerError when response is 500" in new TestCase {
      givenThat(
        get(urlEqualTo(s"/entity-resolver")).willReturn(
          serverError().withBody("oops")
        )
      )
      connector.getEntityIdByAuth(None).value.futureValue shouldBe Left(EntityRequestServerError("oops"))
    }
  }

  "getEntityIdByAuthWithRegime" should {
    "return an EntityId when the HTTP request is successful" in new TestCase {

      private val utr: SaUtr = GenerateRandom.utr()
      private val entityId = GenerateRandom.entityId()

      givenThat(
        get(urlEqualTo(s"/regime/entity-resolver?resolve=true&resolveIds=true")).willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(
              Json
                .obj(
                  "_id"    -> entityId.value,
                  utr.name -> utr.value
                )
                .toString
            )
        )
      )
      connector.getEntityIdByAuthWithRegime(Some(true), Some(true)).value.futureValue shouldBe Right(entityId)
    }

    "return EntityNotFound when response is 500" in new TestCase {
      givenThat(
        get(urlEqualTo("/regime/entity-resolver?resolve=true&resolveIds=true"))
          .willReturn(
            serverError()
          )
      )

      connector.getEntityIdByAuth(Some(true), Some(true)).value.futureValue shouldBe Left(EntityNotFound)
    }
  }

  "deleteEntity" should {
    "return DeletePreferences when the response is 200" in new TestCase {
      givenThat(
        delete(urlEqualTo("/preferences/entity/7ca752a5-14e4-4c29-bfb2-13bcd785e713")).willReturn(
          ok.withBody("{\"reason\": \"DELETE_PREFERENCES\"}")
        )
      )

      connector
        .deleteEntity(EntityId("7ca752a5-14e4-4c29-bfb2-13bcd785e713"))
        .futureValue shouldBe DeletePreferences
    }

    "return INVALID_ENTITY when the response is 200" in new TestCase {
      givenThat(
        delete(urlEqualTo("/preferences/entity/7ca752a5-14e4-4c29-bfb2-13bcd785e713")).willReturn(
          ok.withBody("{\"reason\": \"INVALID_ENTITY\"}")
        )
      )

      connector
        .deleteEntity(EntityId("7ca752a5-14e4-4c29-bfb2-13bcd785e713"))
        .futureValue shouldBe InvalidEntity
    }

    "return EntityProcessError when response is 404" in new TestCase {
      givenThat(
        delete(urlEqualTo("/preferences/entity/7ca752a5-14e4-4c29-bfb2-13bcd785e713")).willReturn(notFound)
      )

      connector.deleteEntity(EntityId("7ca752a5-14e4-4c29-bfb2-13bcd785e713")).futureValue shouldBe EntityProcessError
    }
  }

  class TestCase {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

    val connector: EntityResolverConnector = app.injector.instanceOf[EntityResolverConnector]
  }

  override def fakeApplication(): Application = new GuiceApplicationBuilder()
    .overrides(bind[Decrypter].toInstance(FakeApplicationCrypto))
    .build()

}

trait WithWireMock extends BeforeAndAfterAll with BeforeAndAfterEach {
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
