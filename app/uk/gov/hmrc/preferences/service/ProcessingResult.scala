/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.service

case class ProcessingResult(processedCount: Int, successfulCount: Int) {
  def increment(successful: Boolean): ProcessingResult =
    copy(processedCount + 1, successfulCount + (if (successful) 1 else 0))

  def addSuccess(): ProcessingResult = increment(successful = true)
  def addFailure(): ProcessingResult = increment(successful = false)
}
object ProcessingResult {
  val Empty: ProcessingResult = ProcessingResult(0, 0)
}
