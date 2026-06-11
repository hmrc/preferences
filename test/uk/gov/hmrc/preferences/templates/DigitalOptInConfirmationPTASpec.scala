/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.templates

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

class DigitalOptInConfirmationPTASpec extends PlaySpec with MockitoSugar {

  private def expectedHtml() =
    "<p class=\"govuk-body\">Now you have signed up to online tax letters, we wanted to tell you more.</p>" +
      "<h2 class=\"govuk-heading-l\">Our promise to you</h2>" +
      "<p class=\"govuk-body\">We will:</p>" +
      "<ul class=\"govuk-list govuk-list--bullet\">" +
      "<li>send you online tax letters, such as if you have paid too little or too much tax</li>" +
      "<li>email you to let you know whenever you have a new online letter to read</li>" +
      "</ul>" +
      "<h2 class=\"govuk-heading-l\">Never miss a tax letter</h2>" +
      "<p class=\"govuk-body\">We will write to you by post if:</p>" +
      "<ul class=\"govuk-list govuk-list--bullet\">" +
      "<li>we send an email, but it does not reach you and is returned to us</li>" +
      "<li>you have not done something important</li>" +
      "</ul>" +
      "<p class=\"govuk-body\">This service is still growing, so at the moment we only send some tax letters online.</p>" +
      "<p class=\"govuk-body\">You can also print them at any time, so you have a paper copy if you ever need one.</p>" +
      "<h2 class=\"govuk-heading-l\">To read your tax letters</h2>" +
      "<p class=\"govuk-body\">You need to sign in to your HMRC account and select 'Messages'.</p>" +
      "<p class=\"govuk-body\">You can also use HMRC's free mobile app. Search for 'HMRC app' to download it.</p>"

  "The digitalOptInConfirmation_PTA template" should {

    val mockServicsConfig = mock[ServicesConfig]
    when(mockServicsConfig.baseUrl(any[String])).thenReturn("https://www.tax.service.gov.uk/")

    "render an HTML" in {
      val content = html.digitalOptinConfirmation()
      content.toString().filter(_ >= ' ') mustEqual expectedHtml()
    }
  }

}
