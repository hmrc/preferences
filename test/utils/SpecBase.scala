/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package utils

import org.hamcrest.Matchers.emptyString
import org.mockito.Mockito
import org.scalatest.OptionValues
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.mvc.AnyContentAsEmpty
import play.api.test.CSRFTokenHelper.CSRFFRequestHeader
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext

trait SpecBase extends PlaySpec with MockitoSugar with OptionValues with ScalaFutures with IntegrationPatience {

  val emptyString = ""

  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  def fakeRequest(method: String = emptyString, path: String = emptyString): FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(method, path).withCSRFToken
      .asInstanceOf[FakeRequest[AnyContentAsEmpty.type]]
      .withHeaders(newHeaders = "X-Session-Id" -> "someSessionId")

  lazy val applicationBuilder: GuiceApplicationBuilder = new GuiceApplicationBuilder()
    .configure("metrics.enabled" -> false)
    .configure("auditing.enabled" -> false)
    .configure("metrics.graphite.enabled" -> false)
}
