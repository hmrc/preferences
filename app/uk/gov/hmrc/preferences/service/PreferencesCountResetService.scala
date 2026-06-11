/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.preferences.service

import uk.gov.hmrc.preferences.repository.PreferencesMetricsRepository
import uk.gov.hmrc.preferences.scheduling.Result

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class PreferencesCountResetService @Inject() (
  preferencesMetricsRepository: PreferencesMetricsRepository
)(implicit ec: ExecutionContext) {

  def execute: Future[Result] =
    preferencesMetricsRepository.reset().map {
      case r if !r.wasAcknowledged() =>
        Result(s"Could not reset metric counts, update was not acknowledged")
      case _ =>
        Result("Successfully reset metric counts")
    }
}
