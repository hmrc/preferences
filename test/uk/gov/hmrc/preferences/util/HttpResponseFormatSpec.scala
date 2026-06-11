/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.util

import uk.gov.hmrc.http.HttpResponse
import org.scalatestplus.play.PlaySpec
import play.api.http.Status.OK

class HttpResponseFormatSpec extends PlaySpec {

  "HttpResponseString" should {

    "return correct string presentation" in {
      import HttpResponseFormat.HttpResponseString

      val httpResponse: HttpResponse = HttpResponse(status = OK)

      httpResponse.asString mustBe "status: [200], body: [], headers: []"
    }
  }
}
