/*
 * Copyright 2026 HM Revenue & Customs
 *
 */

package stubs

import uk.gov.hmrc.domain.{ Nino, SaUtr }
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.http.Status.{ CREATED, OK }
import play.api.libs.json.Json
import play.api.libs.json.Json.JsValueWrapper

trait WireMockStubs {
  self: WireMockUtil =>

  def stubEntityResolverPost(utr: SaUtr, entityId: String): Unit =
    wireMockServer.stubFor(
      post(urlEqualTo(s"/test-only/entity-resolver-admin/sa/${utr.utr}"))
        .willReturn(
          aResponse()
            .withStatus(CREATED)
            .withBody(entityId)
        )
    )
  def stubEntityResolverGetTaxId(entityId: String, nino: Option[String] = None, sautr: Option[String] = None): Unit = {
    val taxIdResponse = Json.obj(
      "nino"  -> nino,
      "sautr" -> sautr
    )

    wireMockServer.stubFor(
      get(urlPathEqualTo(s"/entity-resolver?entityId=$entityId"))
        .withQueryParam("entityId", equalTo(entityId))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withHeader("Content-Type", "application/json")
            .withBody(taxIdResponse.toString())
        )
    )

  }

  def stubMessageConnectorPost(messageId: String): Unit = {
    val messageResponse = Json.obj("id" -> messageId)

    wireMockServer.stubFor(
      post(urlEqualTo("/secure-messaging/v4/message"))
        .willReturn(
          aResponse()
            .withStatus(CREATED)
            .withHeader("Content-Type", "application/json")
            .withBody(messageResponse.toString())
        )
    )
  }

  def stubMessageServiceGet(messageId: String, messageContent: String): Unit =
    wireMockServer.stubFor(
      get(urlEqualTo(s"/secure-messaging/messages/$messageId/content"))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withHeader("Content-Type", "text/html")
            .withBody(messageContent)
        )
    )

  def setupAllStubs(utr: SaUtr, entityId: String, messageId: String, messageContent: String): Unit = {
    stubMessageConnectorPost(messageId)
    stubMessageServiceGet(messageId, messageContent)
  }

  def stubForAuthorisedAndEnrolled(response: String): StubMapping =
    wireMockServer.stubFor(
      post(urlPathEqualTo("/auth/authorise"))
        .willReturn(
          ok(response)
        )
    )

  def buildAuthStub(
    withUtr: Option[SaUtr] = None,
    withNino: Option[Nino] = None,
    affinityGroup: String = "Organisation",
    confidenceLevel: Int = 200
  ) = {
    var list = Seq[JsValueWrapper]()

    list = withUtr.fold(list)(utr => list :+ Json.obj("key" -> "UTR", "value" -> s"$utr"))
    list = withNino.fold(list)(nino => list :+ Json.obj("key" -> "NINO", "value" -> s"$nino"))

    var builder = new AuthStubResponseBuilder()
      .withAffinityGroup(affinityGroup)
      .withConfidenceLevel(confidenceLevel)
      .withEnrolments(
        Json.arr(
          Json.obj(
            "key"         -> "IR-SA",
            "identifiers" -> Json.arr(list*)
          )
        )
      )

    builder = withNino.fold(builder)(nino => builder.withNino(nino))
    builder = withUtr.fold(builder)(utr => builder.withUtr(utr))

    val response = builder.build()
    stubForAuthorisedAndEnrolled(response)
  }

  def stubEmailServiceProcessQueue(): Unit =
    wireMockServer.stubFor(
      post(urlEqualTo("/test-only/hmrc/email-admin/process-email-queue"))
        .willReturn(
          aResponse()
            .withStatus(OK)
        )
    )

  def stubDigitalContactStubGetEmails(recipientEmail: String, emailsResponse: String): Unit =
    wireMockServer.stubFor(
      get(urlEqualTo(s"/digital-contact-stub/imi/messages/email/$recipientEmail"))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withHeader("Content-Type", "application/json")
            .withBody(emailsResponse)
        )
    )
}
