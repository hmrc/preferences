/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.templates

import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.Configuration
import uk.gov.hmrc.preferences.model.TaxpayerName

class TemplateHelperSpec extends PlaySpec with MockitoSugar {

  private val mockConfiguration = mock[Configuration]

  private val templateHelper =
    new TemplateHelper(mockConfiguration)

  when(mockConfiguration.get[String]("taxPlatformSaPrefsRootUri")).thenReturn("https://www.tax.service.gov.uk")

  val expectedResult =
    "PHAgY2xhc3M9ImdvdnVrLWJvZHkiPk5vdyB5b3UgaGF2ZSBzaWduZWQgdXAgdG8gb25saW5lIHRheCBsZXR0ZXJzLCB3ZSB3YW50ZWQgdG8gdGVsbCB5b3UgbW9yZS48L3A+CjxoMiBjbGFzcz0iZ292dWstaGVhZGluZy1sIj5PdXIgcHJvbWlzZSB0byB5b3U8L2gyPgo8cCBjbGFzcz0iZ292dWstYm9keSI+V2Ugd2lsbDo8L3A+Cjx1bCBjbGFzcz0iZ292dWstbGlzdCBnb3Z1ay1saXN0LS1idWxsZXQiPgo8bGk+c2VuZCB5b3Ugb25saW5lIHRheCBsZXR0ZXJzLCBzdWNoIGFzIGlmIHlvdSBoYXZlIHBhaWQgdG9vIGxpdHRsZSBvciB0b28gbXVjaCB0YXg8L2xpPgo8bGk+ZW1haWwgeW91IHRvIGxldCB5b3Uga25vdyB3aGVuZXZlciB5b3UgaGF2ZSBhIG5ldyBvbmxpbmUgbGV0dGVyIHRvIHJlYWQ8L2xpPgo8L3VsPgo8aDIgY2xhc3M9ImdvdnVrLWhlYWRpbmctbCI+TmV2ZXIgbWlzcyBhIHRheCBsZXR0ZXI8L2gyPgo8cCBjbGFzcz0iZ292dWstYm9keSI+V2Ugd2lsbCB3cml0ZSB0byB5b3UgYnkgcG9zdCBpZjo8L3A+Cjx1bCBjbGFzcz0iZ292dWstbGlzdCBnb3Z1ay1saXN0LS1idWxsZXQiPgo8bGk+d2Ugc2VuZCBhbiBlbWFpbCwgYnV0IGl0IGRvZXMgbm90IHJlYWNoIHlvdSBhbmQgaXMgcmV0dXJuZWQgdG8gdXM8L2xpPgo8bGk+eW91IGhhdmUgbm90IGRvbmUgc29tZXRoaW5nIGltcG9ydGFudDwvbGk+CjwvdWw+CjxwIGNsYXNzPSJnb3Z1ay1ib2R5Ij5UaGlzIHNlcnZpY2UgaXMgc3RpbGwgZ3Jvd2luZywgc28gYXQgdGhlIG1vbWVudCB3ZSBvbmx5IHNlbmQgc29tZSB0YXggbGV0dGVycyBvbmxpbmUuPC9wPgo8cCBjbGFzcz0iZ292dWstYm9keSI+WW91IGNhbiBhbHNvIHByaW50IHRoZW0gYXQgYW55IHRpbWUsIHNvIHlvdSBoYXZlIGEgcGFwZXIgY29weSBpZiB5b3UgZXZlciBuZWVkIG9uZS48L3A+CjxoMiBjbGFzcz0iZ292dWstaGVhZGluZy1sIj5UbyByZWFkIHlvdXIgdGF4IGxldHRlcnM8L2gyPgo8cCBjbGFzcz0iZ292dWstYm9keSI+WW91IG5lZWQgdG8gc2lnbiBpbiB0byB5b3VyIEhNUkMgYWNjb3VudCBhbmQgc2VsZWN0ICdNZXNzYWdlcycuPC9wPgo8cCBjbGFzcz0iZ292dWstYm9keSI+WW91IGNhbiBhbHNvIHVzZSBITVJDJ3MgZnJlZSBtb2JpbGUgYXBwLiBTZWFyY2ggZm9yICdITVJDIGFwcCcgdG8gZG93bmxvYWQgaXQuPC9wPgo="

  "TemplateHelper getMessageContent" should {

    "return the correct Base64 encoded message content for a BTA customer" in {
      val result = templateHelper.getMessageContent()
      result mustBe expectedResult
    }

    "return the correct Base64 encoded message content for a PTA customer" in {
      val result = templateHelper.getMessageContent()
      result mustBe expectedResult
    }
  }

  "TemplateHelper getSalutation" should {

    "return the correct salutation when a title and a capitalised surname is provided" in {
      val taxpayerName = TaxpayerName(
        Some("Dr."),
        Some("forename"),
        Some("secondforname"),
        Some("Surname"),
        Some("honours"),
        Some("line1"),
        Some("line2"),
        Some("line3")
      )
      val result = templateHelper.getSalutation(Some(taxpayerName))
      result mustBe "Dear Dr. Surname"
    }

    "return the correct salutation when a title and a lower-cased surname is provided" in {
      val taxpayerName = TaxpayerName(
        Some("dr."),
        Some("forename"),
        Some("secondforname"),
        Some("surname"),
        Some("honours"),
        Some("line1"),
        Some("line2"),
        Some("line3")
      )
      val result = templateHelper.getSalutation(Some(taxpayerName))
      result mustBe "Dear Dr. Surname"
    }

    "return the correct salutation with a capitalised surname when a title and a surname all in capitals is provided" in {
      val taxpayerName = TaxpayerName(
        Some("DR."),
        Some("forename"),
        Some("secondforname"),
        Some("SURNAME"),
        Some("honours"),
        Some("line1"),
        Some("line2"),
        Some("line3")
      )
      val result = templateHelper.getSalutation(Some(taxpayerName))
      result mustBe "Dear Dr. Surname"
    }

    "return the default english salutation when the title is missing but a surname is provided" in {
      val taxpayerName = TaxpayerName(
        None,
        Some("forename"),
        Some("secondforname"),
        Some("surname"),
        Some("honours"),
        Some("line1"),
        Some("line2"),
        Some("line3")
      )
      val result = templateHelper.getSalutation(Some(taxpayerName))
      result mustBe "Dear Customer"
    }

    "return the default english salutation when the title is provided but a surname is missing" in {
      val taxpayerName = TaxpayerName(
        Some("mrs."),
        Some("forename"),
        Some("secondforname"),
        None,
        Some("honours"),
        Some("line1"),
        Some("line2"),
        Some("line3")
      )
      val result = templateHelper.getSalutation(Some(taxpayerName))
      result mustBe "Dear Customer"
    }
  }
}
