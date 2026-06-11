/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.service

import org.scalatestplus.play.PlaySpec

class ProcessingResultSpec extends PlaySpec {

  "processing result" should {

    "empty should record zero defaults" in {
      val emptyResult = ProcessingResult.Empty
      emptyResult.processedCount must be(0)
    }

    "increment both counts on addSuccess" in {
      val emptyResult = ProcessingResult.Empty
      val newResult = emptyResult.addSuccess()
      newResult.processedCount must be(1)
      newResult.successfulCount must be(1)
    }

    "increment processed count, but not success on addFailure" in {
      val emptyResult = ProcessingResult.Empty
      val newResult = emptyResult.addFailure()
      newResult.processedCount must be(1)
      newResult.successfulCount must be(0)
    }

  }
}
