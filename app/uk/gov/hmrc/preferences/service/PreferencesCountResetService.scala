/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
